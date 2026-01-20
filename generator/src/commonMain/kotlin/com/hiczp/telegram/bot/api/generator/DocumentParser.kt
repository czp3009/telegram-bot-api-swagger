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
        val table: Element?
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
        // If the name contains spaces, it's a section header
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
        // Check if this is a union type definition
        val isUnionType = description.contains("can be one of", ignoreCase = true)
        val unionSubtypes = if (isUnionType) {
            extractUnionSubtypes(description)
        } else {
            emptyList()
        }

        val fields = if (tableElement == null) {
            if (!isUnionType) {
                logger.warn { "Object $name has no table element, fields will be empty" }
            }
            emptyList()
        } else {
            val headerMap = parseTableHeaders(tableElement)
            val fieldIndex = headerMap[HEADER_FIELD] ?: headerMap[HEADER_PARAMETER] ?: 0
            val typeIndex = headerMap[HEADER_TYPE] ?: 1
            val descriptionIndex = headerMap[HEADER_DESCRIPTION] ?: 2

            tableElement.select("tbody tr").mapNotNull { row ->
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

        return Object(name, description, fields, isUnionType, unionSubtypes)
    }

    private fun extractUnionSubtypes(description: String): List<String> {
        // Extract subtypes from description like "can be one of: Type1, Type2, Type3"
        val subtypes = mutableListOf<String>()

        // Match pattern like "can be one of" followed by a list
        val pattern = Regex("can be one of[:\\s]*", RegexOption.IGNORE_CASE)
        val afterPattern = description.substringAfter(pattern.find(description)?.value ?: "", "")

        if (afterPattern.isNotEmpty()) {
            // Extract type names (capitalized words)
            val typePattern = Regex("\\b([A-Z][a-zA-Z0-9]+)\\b")
            typePattern.findAll(afterPattern).forEach { match ->
                subtypes.add(match.groupValues[1])
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
     * Converts <a href="url">text</a> to [text](url)
     */
    private fun htmlToMarkdown(element: Element): String {
        val html = element.html()
        val baseUrl = "https://core.telegram.org/bots/api"

        // Convert <a> tags to Markdown links
        var markdown =
            html.replace(Regex("<a\\s+href=\"([^\"]+)\"[^>]*>([^<]+)</a>", RegexOption.IGNORE_CASE)) { matchResult ->
                var url = matchResult.groupValues[1]
                val text = matchResult.groupValues[2]

                // Convert relative URLs to absolute URLs
                if (url.startsWith("#")) {
                    url = "$baseUrl$url"
                } else if (url.startsWith("/")) {
                    url = "https://core.telegram.org$url"
                }

                "[$text]($url)"
            }

        // Convert <em> and <i> tags to Markdown italic
        markdown = markdown.replace(Regex("<(?:em|i)>([^<]+)</(?:em|i)>", RegexOption.IGNORE_CASE), "*$1*")

        // Convert <strong> and <b> tags to Markdown bold
        markdown = markdown.replace(Regex("<(?:strong|b)>([^<]+)</(?:strong|b)>", RegexOption.IGNORE_CASE), "**$1**")

        // Convert <code> tags to Markdown code
        markdown = markdown.replace(Regex("<code>([^<]+)</code>", RegexOption.IGNORE_CASE), "`$1`")

        // Remove remaining HTML tags
        markdown = markdown.replace(Regex("<[^>]+>"), "")

        // Decode HTML entities
        markdown = markdown
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")

        return markdown.trim()
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
            val parameterIndex = headerMap[HEADER_PARAMETER] ?: headerMap[HEADER_FIELD] ?: 0
            val typeIndex = headerMap[HEADER_TYPE] ?: 1
            val requiredIndex = headerMap[HEADER_REQUIRED] ?: 2
            val descriptionIndex = headerMap[HEADER_DESCRIPTION] ?: 3

            tableElement.select("tbody tr").mapNotNull { row ->
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

        val returnTypeString = extractReturnType(description)
        return Method(
            name,
            description,
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
