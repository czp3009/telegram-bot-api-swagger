package com.hiczp.telegram.bot.api.generator

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

object DocumentParser {
    private const val CONTENT_CONTAINER = "dev_page_content"
    private const val SECTION_TITLE = "Getting updates"
    private const val TAG_H3 = "h3"
    private const val TAG_H4 = "h4"
    private const val TAG_TABLE = "table"

    private data class ElementContent(
        val description: String,
        val table: Element?
    )

    fun parse(html: String): Pair<List<Method>, List<Object>> {
        val devPageContent = Ksoup.parse(html).body().getElementById(CONTENT_CONTAINER)
        checkNotNull(devPageContent) { "$CONTENT_CONTAINER is null" }
        val elements = devPageContent.children()
        val startIndex = elements.indexOfFirst {
            it.tagName() == TAG_H3 && it.text().trim() == SECTION_TITLE
        }
        check(startIndex != -1) { "Not found h3 tag $SECTION_TITLE" }

        val objects = mutableListOf<Object>()
        val methods = mutableListOf<Method>()

        elements.drop(startIndex + 1).forEach { element ->
            if (element.tagName() == TAG_H4) {
                val name = element.text().trim()
                if (name.isEmpty()) return@forEach

                // Skip section headers (titles with spaces that are not camelCase)
                if (isSectionHeader(name)) {
                    logger.debug { "Skipping section header: $name" }
                    return@forEach
                }

                val firstChar = name[0]

                val content = extractContent(element)

                when {
                    firstChar.isUpperCase() -> {
                        objects.add(parseObject(name, content.description, content.table))
                    }

                    firstChar.isLowerCase() -> {
                        methods.add(parseMethod(name, content.description, content.table))
                    }

                    else -> logger.info { "h4 tag is neither object nor method: $name" }
                }
            }
        }
        logger.info { "Parsed ${objects.size} objects and ${methods.size} methods" }
        return methods to objects
    }

    /**
     * Check if a name is a section header rather than an API object/method.
     * Section headers typically contain spaces and are not in camelCase format.
     * API objects/methods never contain spaces (they use camelCase or PascalCase).
     */
    private fun isSectionHeader(name: String): Boolean {
        // API objects and methods never contain spaces
        // If name contains spaces, it's a section header
        return name.contains(' ')
    }

    private fun extractContent(startElement: Element): ElementContent {
        val descriptionBuilder = StringBuilder()
        var tableElement: Element? = null

        var next = startElement.nextElementSibling()
        while (next != null && next.tagName() !in listOf(TAG_H3, TAG_H4)) {
            when {
                next.tagName() == TAG_TABLE -> tableElement = next
                else -> descriptionBuilder.append(next.text()).append("\n")
            }
            next = next.nextElementSibling()
        }

        return ElementContent(
            description = descriptionBuilder.toString().trim(),
            table = tableElement
        )
    }

    private fun parseTableHeaders(table: Element): Map<String, Int> {
        // Try to find headers in thead first
        var headers = table.select("thead tr th, thead tr td")

        // If no thead, try to find headers in the first row of tbody
        if (headers.isEmpty()) {
            headers = table.select("tbody tr:first-child th, tbody tr:first-child td")
        }

        // If still no headers, try any tr:first-child
        if (headers.isEmpty()) {
            headers = table.select("tr:first-child th, tr:first-child td")
        }

        if (headers.isEmpty()) {
            logger.warn { "No table headers found, using empty header map" }
        }

        return headers.mapIndexed { index, element ->
            element.text().trim().lowercase() to index
        }.toMap()
    }

    private fun parseObject(
        name: String,
        description: String,
        tableElement: Element?
    ): Object {
        val fields = if (tableElement == null) {
            logger.warn { "Object $name has no table element, fields will be empty" }
            emptyList()
        } else {
            val headerMap = parseTableHeaders(tableElement)
            val fieldIndex = headerMap["field"] ?: headerMap["parameter"] ?: 0
            val typeIndex = headerMap["type"] ?: 1
            val descriptionIndex = headerMap["description"] ?: 2

            tableElement.select("tbody tr").mapNotNull { row ->
                val cols = row.select("td")
                if (cols.size > maxOf(fieldIndex, typeIndex, descriptionIndex)) {
                    val fieldDesc = cols[descriptionIndex].text().trim()
                    val typeString = cols[typeIndex].text().trim()
                    Object.Field(
                        name = cols[fieldIndex].text().trim(),
                        type = Type.parse(typeString),
                        required = !fieldDesc.startsWith("Optional", ignoreCase = true),
                        description = fieldDesc
                    )
                } else {
                    logger.warn {
                        "Object $name: row has only ${cols.size} columns, expected at least ${
                            maxOf(
                                fieldIndex,
                                typeIndex,
                                descriptionIndex
                            ) + 1
                        }"
                    }
                    null
                }
            }
        }

        return Object(name, description, fields)
    }

    private fun parseMethod(
        name: String,
        description: String,
        tableElement: Element?
    ): Method {
        val parameters = if (tableElement == null) {
            logger.warn { "Method $name has no table element, parameters will be empty" }
            emptyList()
        } else {
            val headerMap = parseTableHeaders(tableElement)
            val parameterIndex = headerMap["parameter"] ?: headerMap["field"] ?: 0
            val typeIndex = headerMap["type"] ?: 1
            val requiredIndex = headerMap["required"] ?: 2
            val descriptionIndex = headerMap["description"] ?: 3

            tableElement.select("tbody tr").mapNotNull { row ->
                val cols = row.select("td")
                if (cols.size > maxOf(parameterIndex, typeIndex, requiredIndex, descriptionIndex)) {
                    val typeString = cols[typeIndex].text().trim()
                    Method.Parameter(
                        name = cols[parameterIndex].text().trim(),
                        type = Type.parse(typeString),
                        required = cols[requiredIndex].text().trim().equals("Yes", ignoreCase = true),
                        description = cols[descriptionIndex].text().trim()
                    )
                } else {
                    logger.warn {
                        "Method $name: row has only ${cols.size} columns, expected at least ${
                            maxOf(
                                parameterIndex,
                                typeIndex,
                                requiredIndex,
                                descriptionIndex
                            ) + 1
                        }"
                    }
                    null
                }
            }
        }

        val returnTypeString = extractReturnType(description)
        return Method(
            name,
            description,
            parameters,
            Type.parse(returnTypeString),
            determineHttpMethod(name, parameters)
        )
    }

    private fun determineHttpMethod(methodName: String, parameters: List<Method.Parameter>): String {
        val hasFileParameter = parameters.any { param ->
            val typeString = param.type.toString()
            typeString.contains("InputFile", ignoreCase = true) ||
                    typeString.contains("InputMedia", ignoreCase = true)
        }
        if (hasFileParameter) {
            return "POST"
        }
        return if (methodName.startsWith("get", ignoreCase = true)) "GET" else "POST"
    }

    private fun extractReturnType(description: String): String {
        // Check for Boolean type (True/False)
        if (Regex("returns?\\s+True", RegexOption.IGNORE_CASE).containsMatchIn(description) ||
            Regex("True\\s+(?:is|on)\\s+(?:returned|success)", RegexOption.IGNORE_CASE).containsMatchIn(description) ||
            Regex("returns?\\s+False", RegexOption.IGNORE_CASE).containsMatchIn(description)
        ) {
            return "Boolean"
        }

        // Check for basic types (String, Integer)
        if (Regex("returns?\\s+(?:a\\s+)?String", RegexOption.IGNORE_CASE).containsMatchIn(description) ||
            Regex("String\\s+is\\s+returned", RegexOption.IGNORE_CASE).containsMatchIn(description) ||
            Regex("as\\s+(?:a\\s+)?String", RegexOption.IGNORE_CASE).containsMatchIn(description)
        ) {
            return "String"
        }

        if (Regex("returns?\\s+(?:an?\\s+)?Int(?:eger)?", RegexOption.IGNORE_CASE).containsMatchIn(description) ||
            Regex("Integer\\s+is\\s+returned", RegexOption.IGNORE_CASE).containsMatchIn(description) ||
            Regex("returns?\\s+(?:the\\s+)?number", RegexOption.IGNORE_CASE).containsMatchIn(description)
        ) {
            return "Integer"
        }

        // Check for Array types (support nested arrays dynamically)
        Regex("(?:returns?\\s+(?:an?\\s+)?)?((?:Array of )+)([A-Z]\\w+)", RegexOption.IGNORE_CASE).find(description)
            ?.let {
                val arrayPrefix = it.groupValues[1]
                val elementType = it.groupValues[2]

                if (elementType[0].isUpperCase()) {
                    val normalizedPrefix = arrayPrefix.replace(Regex("array of ", RegexOption.IGNORE_CASE), "Array of ")
                    return "$normalizedPrefix$elementType"
                }
            }

        // Extract object type from various patterns (ordered by specificity)

        // "a X object is returned" or "the X object is returned"
        Regex("(?:an?|the)\\s+([A-Z]\\w+)\\s+object\\s+is\\s+returned", RegexOption.IGNORE_CASE).find(description)
            ?.let {
                return it.groupValues[1]
            }

        // "the X is returned" or "the sent/stopped/etc X is returned"
        Regex("(?:the\\s+)?(?:[a-z]+\\s+)?([A-Z]\\w+)\\s+is\\s+returned", RegexOption.IGNORE_CASE).find(description)
            ?.let {
                return it.groupValues[1]
            }

        // "Returns X on success" or "Returns a X on success"
        Regex("returns?\\s+(?:an?\\s+)?([A-Z]\\w+)\\s+on\\s+success", RegexOption.IGNORE_CASE).find(description)?.let {
            val typeName = it.groupValues[1]
            if (typeName.equals("True", ignoreCase = true) || typeName.equals("False", ignoreCase = true)) {
                return "Boolean"
            }
            return typeName
        }

        // "as Type" or "as a Type object"
        Regex("as\\s+(?:an?\\s+)?([A-Z]\\w+)(?:\\s+object)?", RegexOption.IGNORE_CASE).find(description)?.let {
            return it.groupValues[1]
        }

        // "returns a X object" or "returns X object"
        Regex("returns?\\s+(?:an?\\s+)?([A-Z]\\w+)\\s+object", RegexOption.IGNORE_CASE).find(description)?.let {
            return it.groupValues[1]
        }

        // "X object" (without "returns")
        Regex("([A-Z]\\w+)\\s+object", RegexOption.IGNORE_CASE).find(description)?.let {
            return it.groupValues[1]
        }

        // "Returns the X of..." (e.g., "Returns the MessageId of the sent message")
        Regex("returns?\\s+(?:the\\s+)?([A-Z]\\w+)\\s+of", RegexOption.IGNORE_CASE).find(description)?.let {
            return it.groupValues[1]
        }

        // "Returns the adjective TypeName" (e.g., "Returns the uploaded File")
        // This is the most general pattern and should be last
        Regex("returns?\\s+(?:the\\s+)?(?:[a-z]+\\s+)?([A-Z]\\w+)(?:\\s|\\.|,|$)", RegexOption.IGNORE_CASE).find(
            description
        )?.let {
            val typeName = it.groupValues[1]
            if (typeName[0].isUpperCase()) {
                return typeName
            }
        }

        error("Failed to extract return type from description: ${description.take(200)}")
    }

    sealed class Type {
        data class Simple(val name: String) : Type() {
            override fun toString(): String = name
        }

        data class Generic(val name: String, val typeArguments: List<Type>) : Type() {
            override fun toString(): String = "$name<${typeArguments.joinToString(", ")}>"
        }

        companion object {
            fun parse(typeString: String): Type {
                val trimmed = typeString.trim()

                // Count how many "Array of" prefixes exist
                val arrayOfPattern = Regex("Array of ", RegexOption.IGNORE_CASE)
                val arrayCount = arrayOfPattern.findAll(trimmed).count()

                return if (arrayCount > 0) {
                    // Extract the element type (everything after the last "Array of")
                    val elementType = trimmed.substringAfterLast("Array of ", "").trim()

                    if (elementType.isEmpty() || !elementType[0].isUpperCase()) {
                        error("Invalid array element type: '$elementType' in type string: '$typeString'")
                    }

                    // Build nested Generic types from inside out
                    var currentType: Type = Simple(elementType)
                    repeat(arrayCount) {
                        currentType = Generic("Array", listOf(currentType))
                    }
                    currentType
                } else {
                    // Simple type
                    if (trimmed.isEmpty()) {
                        error("Empty type string")
                    }
                    Simple(trimmed)
                }
            }
        }
    }

    data class Object(
        val name: String,
        val description: String,
        val fields: List<Field>,
    ) {
        data class Field(
            val name: String,
            val type: Type,
            val required: Boolean,
            val description: String,
        )
    }

    data class Method(
        val name: String,
        val description: String,
        val parameters: List<Parameter>,
        val returnType: Type,
        val httpMethod: String,
    ) {
        data class Parameter(
            val name: String,
            val type: Type,
            val required: Boolean,
            val description: String,
        )
    }
}
