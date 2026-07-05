package com.hiczp.telegram.bot.api.generator

import com.hiczp.telegram.bot.api.generator.DocumentParser.Method
import com.hiczp.telegram.bot.api.generator.DocumentParser.Object
import community.flock.kotlinx.openapi.bindings.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive

private val logger = KotlinLogging.logger {}

object SwaggerGenerator {
    // Store discriminator information: typeName -> (fieldName, mapping of subtype -> value)
    private val discriminatorInfo = mutableMapOf<String, Pair<String, Map<String, String>>>()

    fun generate(methods: List<Method>, objects: List<Object>, apiVersion: String = "1.0.0"): String {
        logger.info { "Generating OpenAPI specification for ${methods.size} methods and ${objects.size} objects" }

        // Build discriminator info map by examining union subtypes
        discriminatorInfo.clear()
        buildDiscriminatorInfo(objects)

        val openApi = OpenAPIV30Model(
            openapi = "3.0.0",
            info = InfoObject(
                title = "Telegram Bot API",
                description = "Auto-generated OpenAPI specification for Telegram Bot API",
                version = apiVersion
            ),
            servers = listOf(
                Server(
                    url = "https://api.telegram.org/bot{token}",
                    description = "Telegram Bot API Server",
                    variables = mapOf(
                        "token" to ServerVariableObject(
                            default = JsonPrimitive("YOUR_BOT_TOKEN"),
                            description = "Bot token obtained from @BotFather"
                        )
                    )
                )
            ),
            paths = generatePaths(methods, objects),
            components = OpenAPIV30Components(
                schemas = generateSchemas(objects)
            ),
            externalDocs = ExternalDocumentation(
                description = "Official Telegram Bot API Documentation",
                url = "https://core.telegram.org/bots/api"
            )
        )

        return OpenAPIV3.encodeToString(openApi)
    }

    /**
     * Build discriminator information by examining union subtypes.
     * For each union type, check if all subtypes have a common field with constant values.
     * 
     * Only processes union types - non-union types are skipped as they don't need discriminators.
     */
    private fun buildDiscriminatorInfo(objects: List<Object>) {
        // Pattern to identify discriminator fields by their description:
        // Common patterns:
        // 1. "Type of the [X], always \"value\"" or "Type of the [X], must be *value*"
        // 2. "Source of the [X], always \"value\""
        // 3. "Scope type, must be *value*"
        // 4. "Error source, must be *value*"
        // 5. "Type of the result, must be *value*"
        // 6. "The member's status in the chat, always \"value\""
        // 
        // Strategy: Look for descriptions that:
        // - Start with common discriminator field prefixes: "Type of the", "Source of the", etc.
        // - Contain keywords indicating constant values: "always", "must be", "must"
        // - End with a quoted or emphasized value
        val discriminatorPattern = Regex(
            """^(?:Type of the .+?|Source of the .+?|Scope type|Error source|Type of the result|The .+?),\s+(always\s+["\u201C\u201D']([^"\u201C\u201D']+)["\u201C\u201D']|must\s+be\s+\*([^*]+)\*|must\s+["\u201C\u201D']([^"\u201C\u201D']+)["\u201C\u201D'])""",
            RegexOption.IGNORE_CASE
        )
        
        // Create a map of object name to object for quick lookup
        val objectMap = objects.associateBy { it.name }

        // For each union type, examine its subtypes
        // IMPORTANT: Only process union types - skip non-union types
        objects.filter { it.isUnionType }.forEach { unionType ->
            val unionTypeName = unionType.name
            val subtypes = unionType.unionSubtypes
            logger.debug { "Examining union type $unionTypeName with subtypes: $subtypes" }

            if (unionType.unionAdditionalTypes.isNotEmpty()) {
                logger.debug {
                    "Skipping discriminator for $unionTypeName because it has non-object union branches: ${unionType.unionAdditionalTypes}"
                }
                return@forEach
            }

            // Collect discriminator candidates from all subtypes
            val discriminatorCandidates =
                mutableMapOf<String, MutableMap<String, String>>() // fieldName -> (subtype -> value)

            subtypes.forEach { subtypeName ->
                val subtype = objectMap[subtypeName] ?: return@forEach

                // Check each field in the subtype for discriminator pattern
                subtype.fields.forEach { field ->
                    val match = discriminatorPattern.find(field.description) ?: return@forEach

                    // Extract the value from either group 2 (always pattern), group 3 (must be pattern), or group 4 (must pattern)
                    val value = match.groupValues[2].ifEmpty {
                        match.groupValues[3].ifEmpty {
                            match.groupValues[4]
                        }
                    }

                    if (value.isNotEmpty()) {
                        discriminatorCandidates
                            .getOrPut(field.name) { mutableMapOf() }[subtypeName] = value
                        logger.debug { "Found discriminator candidate in $subtypeName.${field.name}: $value" }
                    }
                }
            }

            // Find a field that has values for all subtypes
            discriminatorCandidates.forEach { (fieldName, mapping) ->
                if (mapping.size == subtypes.size) {
                    val discriminatorValues = mapping.values
                    if (discriminatorValues.toSet().size == discriminatorValues.size) {
                        // All subtypes have this field with unique constant values.
                        discriminatorInfo[unionTypeName] = fieldName to mapping
                        logger.info { "Added discriminator for $unionTypeName: field=$fieldName, mapping=$mapping" }
                    } else {
                        logger.warn {
                            "Skipping discriminator for $unionTypeName: field=$fieldName has duplicate values in mapping=$mapping"
                        }
                    }
                }
            }
        }
    }

    private fun generatePaths(methods: List<Method>, objects: List<Object>): Map<Path, OpenAPIV30PathItem> {
        return methods.associate { method ->
            Path("/${method.name}") to OpenAPIV30PathItem(
                get = if (method.httpMethod == "GET") generateOperation(method, objects) else null,
                post = if (method.httpMethod == "POST") generateOperation(method, objects) else null
            )
        }
    }

    private fun generateOperation(method: Method, objects: List<Object>): OpenAPIV30Operation {
        // Check if any parameter contains file types (including nested types)
        var hasDirectFileUpload = false
        var hasIndirectFileReference = false

        method.parameters.forEach { param ->
            val typeString = param.type.toString()

            // Check for direct InputFile uploads
            if (DocumentParser.isDirectInputFile(typeString)) {
                hasDirectFileUpload = true
                return@forEach
            }

            // Check for indirect file references (InputMedia, etc. that support attach://)
            val hasFileDesc = param.description.contains("More information on Sending Files", ignoreCase = true) ||
                    param.description.contains("attach://", ignoreCase = false)

            val hasNestedFileRef = when (param.type) {
                is DocumentParser.Type.Simple -> {
                    !DocumentParser.isDirectInputFile(param.type.name) &&
                            DocumentParser.hasNestedFileType(param.type.name, objects)
                }

                is DocumentParser.Type.Generic -> {
                    if (param.type.name == "Array" && param.type.typeArguments.isNotEmpty()) {
                        when (val elementType = param.type.typeArguments[0]) {
                            is DocumentParser.Type.Simple -> {
                                !DocumentParser.isDirectInputFile(elementType.name) &&
                                        DocumentParser.hasNestedFileType(elementType.name, objects)
                            }

                            else -> false
                        }
                    } else {
                        false
                    }
                }

                is DocumentParser.Type.Union -> {
                    // For union types, check if any of the types contains file fields
                    param.type.types.any { unionType ->
                        when (unionType) {
                            is DocumentParser.Type.Simple -> {
                                !DocumentParser.isDirectInputFile(unionType.name) &&
                                        DocumentParser.hasNestedFileType(unionType.name, objects)
                            }

                            is DocumentParser.Type.Generic -> {
                                if (unionType.name == "Array" && unionType.typeArguments.isNotEmpty()) {
                                    when (val elementType = unionType.typeArguments[0]) {
                                        is DocumentParser.Type.Simple -> {
                                            !DocumentParser.isDirectInputFile(elementType.name) &&
                                                    DocumentParser.hasNestedFileType(elementType.name, objects)
                                        }

                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }

                            is DocumentParser.Type.Union -> false // Nested unions are rare, skip for now
                        }
                    }
                }
            }

            if (hasFileDesc || hasNestedFileRef) {
                hasIndirectFileReference = true
            }
        }

        val hasFileParameter = hasDirectFileUpload || hasIndirectFileReference

        return OpenAPIV30Operation(
            summary = method.name,
            description = method.description,
            operationId = method.name,
            parameters = if (method.httpMethod == "GET") generateParameters(method.parameters) else null,
            requestBody = if (method.httpMethod == "POST" && method.parameters.isNotEmpty()) {
                // Only add additionalProperties for indirect file references (attach://)
                generateRequestBody(
                    method.parameters,
                    hasFileParameter,
                    needsAdditionalProperties = hasIndirectFileReference
                )
            } else null,
            responses = generateResponses(method.returnType)
        )
    }

    private fun generateParameters(parameters: List<Method.Parameter>): List<OpenAPIV30ParameterOrReference> {
        return parameters.map { param ->
            OpenAPIV30Parameter(
                name = param.name,
                `in` = OpenAPIV30ParameterLocation.QUERY,
                description = param.description,
                required = param.required,
                schema = convertTypeToSchema(param.type)
            )
        }
    }

    private fun generateRequestBody(
        parameters: List<Method.Parameter>,
        hasFileParameter: Boolean,
        needsAdditionalProperties: Boolean
    ): OpenAPIV30RequestBody {
        val properties = parameters.associate { param ->
            param.name to convertParameterToSchema(param)
        }

        val requiredFields = parameters.filter { it.required }.map { it.name }

        val schema = OpenAPIV30Schema(
            type = schemaType(OpenAPIV30Type.OBJECT),
            properties = properties.ifEmpty { null },
            required = requiredFields.ifEmpty { null },
            // For multipart/form-data with indirect file references (attach://), 
            // allow additional properties to support dynamic attach://<file_attach_name> fields.
            // This is necessary because Telegram Bot API allows sending files with arbitrary field names
            // that are referenced in media arrays using "attach://<file_attach_name>" syntax.
            // Direct InputFile uploads don't need this as they use fixed field names.
            additionalProperties = if (needsAdditionalProperties) {
                OpenAPIV30Schema(
                    type = schemaType(OpenAPIV30Type.STRING),
                    format = "binary",
                    description = "Additional file attachments referenced via attach://<file_attach_name> in media fields"
                )
            } else {
                null
            }
        )

        val contentType = if (hasFileParameter) MediaType("multipart/form-data") else MediaType("application/json")

        return OpenAPIV30RequestBody(
            content = mapOf(
                contentType to OpenAPIV30MediaType(
                    schema = schema
                )
            ),
            required = true
        )
    }

    private fun generateResponses(returnType: DocumentParser.Type): Map<StatusCode, OpenAPIV30ResponseOrReference> {
        val successSchema = OpenAPIV30Schema(
            type = schemaType(OpenAPIV30Type.OBJECT),
            properties = mapOf(
                "ok" to OpenAPIV30Schema(type = schemaType(OpenAPIV30Type.BOOLEAN)),
                "result" to convertTypeToSchema(returnType)
            ),
            required = listOf("ok", "result")
        )

        return mapOf(
            StatusCode("200") to OpenAPIV30Response(
                description = "Successful response",
                content = mapOf(
                    MediaType("application/json") to OpenAPIV30MediaType(
                        schema = successSchema
                    )
                )
            )
        )
    }

    private fun generateSchemas(objects: List<Object>): Map<String, OpenAPIV30SchemaOrReference> {
        val schemas = objects.associate { obj ->
            val schema = if (obj.isUnionType) {
                // For union types, create a schema with oneOf pointing to subtypes
                val discriminator = discriminatorInfo[obj.name]?.let { (fieldName, mapping) ->
                    // OpenAPI discriminator mapping: discriminatorValue -> schemaRef
                    // mapping is currently: subtype -> discriminatorValue
                    // We need to reverse it to: discriminatorValue -> "#/components/schemas/subtype"
                    val reversedMapping = mapping.map { (subtype, value) ->
                        value to "#/components/schemas/$subtype"
                    }.toMap()

                    OpenAPIV30Discriminator(
                        propertyName = fieldName,
                        mapping = reversedMapping
                    )
                }

                val oneOfList = obj.unionAdditionalTypes.map { type ->
                    convertTypeToSchema(type)
                } + obj.unionSubtypes.map { subtype ->
                    OpenAPIV30Reference(ref = Ref("#/components/schemas/$subtype"))
                }

                OpenAPIV30Schema(
                    description = obj.description.ifEmpty { null },
                    oneOf = oneOfList.ifEmpty { null },
                    discriminator = discriminator
                )
            } else {
                // For regular types, create a normal object schema
                OpenAPIV30Schema(
                    type = schemaType(OpenAPIV30Type.OBJECT),
                    description = obj.description.ifEmpty { null },
                    properties = obj.fields.associate { field ->
                        field.name to convertFieldToSchema(field)
                    }.ifEmpty { null },
                    required = obj.fields.filter { it.required }.map { it.name }.ifEmpty { null }
                )
            }
            obj.name to schema
        }

        // Add Error schema for error responses
        val errorSchema = OpenAPIV30Schema(
            type = schemaType(OpenAPIV30Type.OBJECT),
            description = "Error response returned when a request fails",
            properties = mapOf(
                "ok" to OpenAPIV30Schema(
                    type = schemaType(OpenAPIV30Type.BOOLEAN),
                    description = "Always false for error responses"
                ),
                "error_code" to OpenAPIV30Schema(
                    type = schemaType(OpenAPIV30Type.INTEGER),
                    format = "int64",
                    description = "Error code"
                ),
                "description" to OpenAPIV30Schema(
                    type = schemaType(OpenAPIV30Type.STRING),
                    description = "Human-readable description of the error"
                ),
                "parameters" to OpenAPIV30Reference(
                    ref = Ref("#/components/schemas/ResponseParameters")
                )
            ),
            required = listOf("ok", "error_code", "description")
        )

        return schemas + ("Error" to errorSchema)
    }

    private fun convertParameterToSchema(param: Method.Parameter): OpenAPIV30SchemaOrReference {
        return convertToSchemaWithDescription(param.type, param.description)
    }

    private fun convertFieldToSchema(field: Object.Field): OpenAPIV30SchemaOrReference {
        return convertToSchemaWithDescription(field.type, field.description)
    }

    /**
     * Convert a type to schema and optionally add the description if the schema is inline.
     * For references with descriptions, wrap them in an allOf to preserve the description.
     */
    private fun convertToSchemaWithDescription(
        type: DocumentParser.Type,
        description: String
    ): OpenAPIV30SchemaOrReference {
        return when (val baseSchema = convertTypeToSchema(type)) {
            is OpenAPIV30Schema -> {
                if (description.isNotEmpty()) {
                    baseSchema.copy(description = description)
                } else {
                    baseSchema
                }
            }

            is OpenAPIV30Reference -> {
                // For references with descriptions, wrap in allOf to preserve the description
                if (description.isNotEmpty()) {
                    OpenAPIV30Schema(
                        allOf = listOf(baseSchema),
                        description = description
                    )
                } else {
                    baseSchema
                }
            }
        }
    }

    private fun convertTypeToSchema(type: DocumentParser.Type): OpenAPIV30SchemaOrReference {
        return when (type) {
            is DocumentParser.Type.Simple -> {
                val typeName = type.name

                // Handle comma-separated types (e.g., "Type1, Type2, Type3 and Type4")
                // This is kept for backward compatibility with any remaining string-based union types
                splitUnionTypes(typeName, Regex(",|\\s+and\\s+", RegexOption.IGNORE_CASE))?.let { types ->
                    return OpenAPIV30Schema(
                        oneOf = types.map { singleType ->
                            OpenAPIV30Reference(ref = Ref("#/components/schemas/$singleType"))
                        }
                    )
                }

                // Handle single types
                convertSingleTypeToSchema(typeName)
            }

            is DocumentParser.Type.Generic -> {
                if (type.name == "Array" && type.typeArguments.size == 1) {
                    OpenAPIV30Schema(
                        type = schemaType(OpenAPIV30Type.ARRAY),
                        items = convertTypeToSchema(type.typeArguments[0])
                    )
                } else {
                    OpenAPIV30Schema(type = schemaType(OpenAPIV30Type.STRING))
                }
            }

            is DocumentParser.Type.Union -> {
                // Handle union types (e.g., Message or Boolean)
                OpenAPIV30Schema(
                    oneOf = type.types.map { unionType -> convertTypeToSchema(unionType) }
                )
            }
        }
    }

    /**
     * Split a type name into multiple types using the given regex pattern.
     * Returns null if the type name doesn't contain the pattern or results in a single type.
     */
    private fun splitUnionTypes(typeName: String, pattern: Regex): List<String>? {
        if (!pattern.containsMatchIn(typeName)) {
            return null
        }

        val types = typeName.split(pattern)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return if (types.size > 1) types else null
    }

    private fun convertSingleTypeToSchema(typeName: String): OpenAPIV30SchemaOrReference {
        return when (typeName) {
            "String" -> OpenAPIV30Schema(type = schemaType(OpenAPIV30Type.STRING))
            "Integer", "Int" -> OpenAPIV30Schema(type = schemaType(OpenAPIV30Type.INTEGER), format = "int64")
            "Boolean" -> OpenAPIV30Schema(type = schemaType(OpenAPIV30Type.BOOLEAN))
            "Float", "Double" -> OpenAPIV30Schema(type = schemaType(OpenAPIV30Type.NUMBER), format = "double")
            else -> {
                // Always return a reference for custom types (including union types)
                // The union type definition itself will have oneOf in the schemas section
                OpenAPIV30Reference(ref = Ref("#/components/schemas/$typeName"))
            }
        }
    }

    private fun schemaType(type: OpenAPIV30Type): OpenAPIV30SingleType {
        return OpenAPIV30SingleType(type)
    }
}
