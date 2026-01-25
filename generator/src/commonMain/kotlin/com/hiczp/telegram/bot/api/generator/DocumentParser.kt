package com.hiczp.telegram.bot.api.generator

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

object DocumentParser {
    private const val CONTENT_CONTAINER = "dev_page_content"
    private const val SECTION_TITLE = "Getting updates"
    private const val TAG_H3 = "h3"
    private const val TAG_H4 = "h4"
    private const val TAG_TABLE = "table"

    // Regex pattern for extracting Bot API version
    private val REGEX_BOT_API_VERSION = Regex("Bot API (\\d+\\.\\d+)", RegexOption.IGNORE_CASE)

    // Table header names
    private const val HEADER_FIELD = "field"
    private const val HEADER_PARAMETER = "parameter"
    private const val HEADER_TYPE = "type"
    private const val HEADER_REQUIRED = "required"
    private const val HEADER_DESCRIPTION = "description"

    // Type names
    private const val TYPE_INPUT_FILE = "InputFile"
    private const val TYPE_INPUT_MEDIA = "InputMedia"
    private const val TYPE_ARRAY_OF = "Array of "

    // Regex patterns for return type extraction (compiled once)
    private val REGEX_RETURNS_TRUE = Regex("returns?\\s+True", RegexOption.IGNORE_CASE)
    private val REGEX_TRUE_IS_RETURNED = Regex("True\\s+(?:is|on)\\s+(?:returned|success)", RegexOption.IGNORE_CASE)
    private val REGEX_RETURNS_FALSE = Regex("returns?\\s+False", RegexOption.IGNORE_CASE)
    private val REGEX_RETURNS_STRING = Regex("returns?\\s+(?:a\\s+)?String", RegexOption.IGNORE_CASE)
    private val REGEX_STRING_IS_RETURNED = Regex("String\\s+is\\s+returned", RegexOption.IGNORE_CASE)
    private val REGEX_AS_STRING = Regex("as\\s+(?:a\\s+)?String", RegexOption.IGNORE_CASE)
    private val REGEX_RETURNS_INTEGER = Regex("returns?\\s+(?:an?\\s+)?Int(?:eger)?", RegexOption.IGNORE_CASE)
    private val REGEX_INTEGER_IS_RETURNED = Regex("Integer\\s+is\\s+returned", RegexOption.IGNORE_CASE)
    private val REGEX_RETURNS_NUMBER = Regex("returns?\\s+(?:the\\s+)?number", RegexOption.IGNORE_CASE)
    private val REGEX_ARRAY_TYPE =
        Regex("(?:returns?\\s+(?:an?\\s+)?)?((?:$TYPE_ARRAY_OF)+)([A-Z]\\w+)", RegexOption.IGNORE_CASE)
    private val REGEX_OBJECT_IS_RETURNED =
        Regex("(?:an?|the)\\s+([A-Z]\\w+)\\s+object\\s+is\\s+returned", RegexOption.IGNORE_CASE)
    private val REGEX_TYPE_IS_RETURNED =
        Regex("(?:the\\s+)?(?:[a-z]+\\s+)?([A-Z]\\w+)\\s+is\\s+returned", RegexOption.IGNORE_CASE)
    private val REGEX_RETURNS_ON_SUCCESS =
        Regex("returns?\\s+(?:an?\\s+)?([A-Z]\\w+)\\s+on\\s+success", RegexOption.IGNORE_CASE)
    private val REGEX_AS_TYPE = Regex("as\\s+(?:an?\\s+)?([A-Z]\\w+)(?:\\s+object)?", RegexOption.IGNORE_CASE)
    private val REGEX_RETURNS_OBJECT = Regex("returns?\\s+(?:an?\\s+)?([A-Z]\\w+)\\s+object", RegexOption.IGNORE_CASE)
    private val REGEX_TYPE_OBJECT = Regex("([A-Z]\\w+)\\s+object", RegexOption.IGNORE_CASE)
    private val REGEX_RETURNS_TYPE_OF = Regex("returns?\\s+(?:the\\s+)?([A-Z]\\w+)\\s+of", RegexOption.IGNORE_CASE)
    private val REGEX_RETURNS_TYPE =
        Regex("returns?\\s+(?:the\\s+)?(?:[a-z]+\\s+)?([A-Z]\\w+)(?:\\s|\\.|,|$)", RegexOption.IGNORE_CASE)
    private val REGEX_ARRAY_OF_PATTERN = Regex(TYPE_ARRAY_OF, RegexOption.IGNORE_CASE)

    private data class ElementContent(
        val description: String,
        val table: Element?,
        val elements: List<Element>
    )

    /**
     * Extract the Bot API version from the HTML document.
     * The version is typically found in the "Recent changes" section in the format "Bot API X.Y".
     */
    fun extractVersion(html: String): String {
        val doc = Ksoup.parse(html)
        val devPageContent = doc.body().getElementById(CONTENT_CONTAINER)
        checkNotNull(devPageContent) { "$CONTENT_CONTAINER is null" }

        // Look for the version in the first few paragraphs after "Recent changes"
        val elements = devPageContent.children()
        val recentChangesIndex = elements.indexOfFirst {
            it.tagName() == TAG_H3 && it.text().contains("Recent changes", ignoreCase = true)
        }

        if (recentChangesIndex != -1) {
            // Search in the next few elements after "Recent changes"
            for (i in (recentChangesIndex + 1)..<minOf(recentChangesIndex + 10, elements.size)) {
                val element = elements[i]
                val text = element.text()
                val match = REGEX_BOT_API_VERSION.find(text)
                if (match != null) {
                    val version = match.groupValues[1]
                    logger.info { "Extracted Bot API version: $version" }
                    return version
                }
            }
        }

        error("Failed to extract Bot API version from document")
    }

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
                        objects.add(parseObject(name, content))
                    }

                    firstChar.isLowerCase() -> {
                        methods.add(parseMethod(name, content))
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
        // If the name contains spaces, it's a section header
        return name.contains(' ')
    }

    private fun extractContent(startElement: Element): ElementContent {
        val descriptionBuilder = StringBuilder()
        var tableElement: Element? = null
        val elements = mutableListOf<Element>()

        var next = startElement.nextElementSibling()
        while (next != null && next.tagName() !in listOf(TAG_H3, TAG_H4)) {
            elements.add(next)
            when {
                next.tagName() == TAG_TABLE -> tableElement = next
                else -> descriptionBuilder.append(next.text()).append("\n")
            }
            next = next.nextElementSibling()
        }

        return ElementContent(
            description = descriptionBuilder.toString().trim(),
            table = tableElement,
            elements = elements
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
        content: ElementContent
    ): Object {
        // Check if this is a union type definition
        // Patterns: 
        // 1. "can be one of"
        // 2. "should be one of"
        // 3. "It can be one of"
        // 4. "the following X scopes are supported"
        // 5. "the following X types"
        // 6. "currently support results of the following X types"
        val isUnionType = content.description.contains("can be one of", ignoreCase = true) ||
                content.description.contains("should be one of", ignoreCase = true) ||
                content.description.contains("It can be one of", ignoreCase = true) ||
                content.description.contains(Regex("the following \\d+ \\w+ are supported", RegexOption.IGNORE_CASE)) ||
                content.description.contains(Regex("the following \\d+ types", RegexOption.IGNORE_CASE)) ||
                content.description.contains(Regex("support.*the following \\d+ types", RegexOption.IGNORE_CASE))
        
        val unionSubtypes = if (isUnionType) {
            extractUnionSubtypes(content.elements)
        } else {
            emptyList()
        }

        val fields = if (content.table == null) {
            if (!isUnionType) {
                logger.warn { "Object $name has no table element, fields will be empty" }
            }
            emptyList()
        } else {
            val headerMap = parseTableHeaders(content.table)
            val fieldIndex = headerMap[HEADER_FIELD] ?: headerMap[HEADER_PARAMETER] ?: 0
            val typeIndex = headerMap[HEADER_TYPE] ?: 1
            val descriptionIndex = headerMap[HEADER_DESCRIPTION] ?: 2

            content.table.select("tbody tr").mapNotNull { row ->
                val cols = row.select("td")
                if (cols.size > maxOf(fieldIndex, typeIndex, descriptionIndex)) {
                    val fieldDesc = htmlToMarkdown(cols[descriptionIndex])
                    val typeString = cols[typeIndex].text().trim()
                    Object.Field(
                        name = cols[fieldIndex].text().trim(),
                        type = Type.parse(typeString),
                        required = !isOptionalField(fieldDesc),
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

        return Object(name, content.description, fields, isUnionType, unionSubtypes)
    }

    private fun extractUnionSubtypes(elements: List<Element>): List<String> {
        // Extract subtypes from <ul><li> list elements
        // Look for the first <ul> element that contains <li> items with type names
        
        val subtypes = mutableListOf<String>()

        for (element in elements) {
            if (element.tagName() == "ul") {
                // Extract type names from <li> elements
                val listItems = element.select("li")
                for (item in listItems) {
                    // Extract the type name from the link text or plain text
                    // Pattern: <li><a href="...">TypeName</a></li>
                    val links = item.select("a")
                    if (links.isNotEmpty()) {
                        // Get text from the first link
                        val typeName = links.first()?.text()?.trim()
                        if (!typeName.isNullOrEmpty() && typeName[0].isUpperCase()) {
                            subtypes.add(typeName)
                        }
                    } else {
                        // No link, try to extract from plain text
                        val text = item.text().trim()
                        if (text.isNotEmpty() && text[0].isUpperCase()) {
                            subtypes.add(text)
                        }
                    }
                }

                // If we found subtypes in this <ul>, we're done
                if (subtypes.isNotEmpty()) {
                    break
                }
            }
        }

        return subtypes
    }

    /**
     * Check if a field description indicates it's optional.
     * Handles various formats like "Optional.", "*Optional*.", "Optional ", etc.
     */
    private fun isOptionalField(description: String): Boolean {
        // Remove leading Markdown formatting (*, _, etc.) and whitespace
        val cleaned = description.trimStart('*', '_', ' ', '\t')
        return cleaned.startsWith("Optional", ignoreCase = true)
    }

    /**
     * Convert HTML element to Markdown-formatted text, preserving links.
     * Uses DOM traversal instead of regex for more reliable conversion.
     */
    private fun htmlToMarkdown(element: Element): String {
        val baseUrl = "https://core.telegram.org/bots/api"

        fun convertNode(node: Element): String {
            val result = StringBuilder()

            for (child in node.childNodes()) {
                when (child) {
                    is TextNode -> {
                        result.append(child.text())
                    }

                    is Element -> {
                        when (child.tagName().lowercase()) {
                            "a" -> {
                                var url = child.attr("href")
                                val text = child.text()

                                // Convert relative URLs to absolute URLs
                                url = when {
                                    url.startsWith("#") -> "$baseUrl$url"
                                    url.startsWith("/") -> "https://core.telegram.org$url"
                                    else -> url
                                }

                                result.append("[$text]($url)")
                            }

                            "em", "i" -> {
                                result.append("*${child.text()}*")
                            }

                            "strong", "b" -> {
                                result.append("**${child.text()}**")
                            }

                            "code" -> {
                                result.append("`${child.text()}`")
                            }

                            else -> {
                                // For other tags, recursively process children
                                result.append(convertNode(child))
                            }
                        }
                    }
                }
            }

            return result.toString()
        }

        return convertNode(element).trim()
    }

    private fun parseMethod(
        name: String,
        content: ElementContent
    ): Method {
        val parameters = if (content.table == null) {
            logger.warn { "Method $name has no table element, parameters will be empty" }
            emptyList()
        } else {
            val headerMap = parseTableHeaders(content.table)
            val parameterIndex = headerMap[HEADER_PARAMETER] ?: headerMap[HEADER_FIELD] ?: 0
            val typeIndex = headerMap[HEADER_TYPE] ?: 1
            val requiredIndex = headerMap[HEADER_REQUIRED] ?: 2
            val descriptionIndex = headerMap[HEADER_DESCRIPTION] ?: 3

            content.table.select("tbody tr").mapNotNull { row ->
                val cols = row.select("td")
                if (cols.size > maxOf(parameterIndex, typeIndex, requiredIndex, descriptionIndex)) {
                    val typeString = cols[typeIndex].text().trim()
                    Method.Parameter(
                        name = cols[parameterIndex].text().trim(),
                        type = Type.parse(typeString),
                        required = cols[requiredIndex].text().trim().equals("Yes", ignoreCase = true),
                        description = htmlToMarkdown(cols[descriptionIndex])
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

        val returnTypeString = extractReturnType(content.description)
        return Method(
            name,
            content.description,
            parameters,
            Type.parse(returnTypeString),
            determineHttpMethod(name, parameters)
        )
    }

    /**
     * Check if a type string contains file-related types (InputFile or InputMedia).
     */
    internal fun hasFileType(typeString: String): Boolean {
        return typeString.contains(TYPE_INPUT_FILE, ignoreCase = true) ||
                typeString.contains(TYPE_INPUT_MEDIA, ignoreCase = true)
    }

    private fun determineHttpMethod(methodName: String, parameters: List<Method.Parameter>): String {
        val hasFileParameter = parameters.any { param ->
            hasFileType(param.type.toString())
        }
        if (hasFileParameter) {
            return "POST"
        }
        return if (methodName.startsWith("get", ignoreCase = true)) "GET" else "POST"
    }

    private fun extractReturnType(description: String): String {
        // Check for Boolean type (True/False)
        if (REGEX_RETURNS_TRUE.containsMatchIn(description) ||
            REGEX_TRUE_IS_RETURNED.containsMatchIn(description) ||
            REGEX_RETURNS_FALSE.containsMatchIn(description)
        ) {
            return "Boolean"
        }

        // Check for basic types (String, Integer)
        if (REGEX_RETURNS_STRING.containsMatchIn(description) ||
            REGEX_STRING_IS_RETURNED.containsMatchIn(description) ||
            REGEX_AS_STRING.containsMatchIn(description)
        ) {
            return "String"
        }

        if (REGEX_RETURNS_INTEGER.containsMatchIn(description) ||
            REGEX_INTEGER_IS_RETURNED.containsMatchIn(description) ||
            REGEX_RETURNS_NUMBER.containsMatchIn(description)
        ) {
            return "Integer"
        }

        // Check for Array types (support nested arrays dynamically)
        REGEX_ARRAY_TYPE.find(description)?.let {
            val arrayPrefix = it.groupValues[1]
            val elementType = it.groupValues[2]

            if (elementType[0].isUpperCase()) {
                val normalizedPrefix = arrayPrefix.replace(Regex("array of ", RegexOption.IGNORE_CASE), TYPE_ARRAY_OF)
                return "$normalizedPrefix$elementType"
            }
        }

        // Extract object type from various patterns (ordered by specificity)

        // "a X object is returned" or "the X object is returned"
        REGEX_OBJECT_IS_RETURNED.find(description)?.let {
            return it.groupValues[1]
        }

        // "the X is returned" or "the sent/stopped/etc X is returned"
        REGEX_TYPE_IS_RETURNED.find(description)?.let {
            return it.groupValues[1]
        }

        // "Returns X on success" or "Returns a X on success"
        REGEX_RETURNS_ON_SUCCESS.find(description)?.let {
            val typeName = it.groupValues[1]
            if (typeName.equals("True", ignoreCase = true) || typeName.equals("False", ignoreCase = true)) {
                return "Boolean"
            }
            return typeName
        }

        // "as Type" or "as a Type object"
        REGEX_AS_TYPE.find(description)?.let {
            return it.groupValues[1]
        }

        // "returns a X object" or "returns X object"
        REGEX_RETURNS_OBJECT.find(description)?.let {
            return it.groupValues[1]
        }

        // "X object" (without "returns")
        REGEX_TYPE_OBJECT.find(description)?.let {
            return it.groupValues[1]
        }

        // "Returns the X of..." (e.g., "Returns the MessageId of the sent message")
        REGEX_RETURNS_TYPE_OF.find(description)?.let {
            return it.groupValues[1]
        }

        // "Returns the adjective TypeName" (e.g., "Returns the uploaded File")
        // This is the most general pattern and should be last
        REGEX_RETURNS_TYPE.find(description)?.let {
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
                // Normalize special boolean types
                val normalizedType = when (val trimmed = typeString.trim()) {
                    "True", "False" -> "Boolean"
                    else -> trimmed
                }

                // Count how many "Array of" prefixes exist
                val arrayCount = REGEX_ARRAY_OF_PATTERN.findAll(normalizedType).count()

                return if (arrayCount > 0) {
                    // Extract the element type (everything after the last "Array of")
                    val elementType = normalizedType.substringAfterLast(TYPE_ARRAY_OF, "").trim()

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
                    if (normalizedType.isEmpty()) {
                        error("Empty type string")
                    }
                    Simple(normalizedType)
                }
            }
        }
    }

    data class Object(
        val name: String,
        val description: String,
        val fields: List<Field>,
        val isUnionType: Boolean = false,
        val unionSubtypes: List<String> = emptyList(),
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
