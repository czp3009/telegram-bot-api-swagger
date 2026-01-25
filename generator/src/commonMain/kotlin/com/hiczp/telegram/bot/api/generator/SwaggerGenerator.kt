package com.hiczp.telegram.bot.api.generator

import com.hiczp.telegram.bot.api.generator.DocumentParser.Method
import com.hiczp.telegram.bot.api.generator.DocumentParser.Object
import community.flock.kotlinx.openapi.bindings.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive

private val logger = KotlinLogging.logger {}

object SwaggerGenerator {
    // Store union type information for reference during schema conversion
    private val unionTypes = mutableMapOf<String, List<String>>()

    fun generate(methods: List<Method>, objects: List<Object>, apiVersion: String = "1.0.0"): String {
        logger.info { "Generating OpenAPI specification for ${methods.size} methods and ${objects.size} objects" }

        // Build union type map
        unionTypes.clear()
        objects.filter { it.isUnionType }.forEach { obj ->
            unionTypes[obj.name] = obj.unionSubtypes
        }

        val openApi = OpenAPIV3Model(
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
            paths = generatePaths(methods),
            components = OpenAPIV3Components(
                schemas = generateSchemas(objects)
            ),
            externalDocs = ExternalDocumentation(
                description = "Official Telegram Bot API Documentation",
                url = "https://core.telegram.org/bots/api"
            )
        )

        return OpenAPIV3.encodeToString(openApi)
    }

    private fun generatePaths(methods: List<Method>): Map<Path, OpenAPIV3PathItem> {
        return methods.associate { method ->
            Path("/${method.name}") to OpenAPIV3PathItem(
                get = if (method.httpMethod == "GET") generateOperation(method) else null,
                post = if (method.httpMethod == "POST") generateOperation(method) else null
            )
        }
    }

    private fun generateOperation(method: Method): OpenAPIV3Operation {
        val hasFileParameter = method.parameters.any { param ->
            DocumentParser.hasFileType(param.type.toString())
        }

        return OpenAPIV3Operation(
            summary = method.name,
            description = method.description,
            operationId = method.name,
            parameters = if (method.httpMethod == "GET") generateParameters(method.parameters) else null,
            requestBody = if (method.httpMethod == "POST") generateRequestBody(
                method.parameters,
                hasFileParameter
            ) else null,
            responses = generateResponses(method.returnType)
        )
    }

    private fun generateParameters(parameters: List<Method.Parameter>): List<OpenAPIV3ParameterOrReference> {
        return parameters.map { param ->
            OpenAPIV3Parameter(
                name = param.name,
                `in` = OpenAPIV3ParameterLocation.QUERY,
                description = param.description,
                required = param.required,
                schema = convertTypeToSchema(param.type)
            )
        }
    }

    private fun generateRequestBody(
        parameters: List<Method.Parameter>,
        hasFileParameter: Boolean
    ): OpenAPIV3RequestBody {
        val properties = parameters.associate { param ->
            param.name to convertParameterToSchema(param)
        }

        val requiredFields = parameters.filter { it.required }.map { it.name }

        val schema = OpenAPIV3Schema(
            type = OpenAPIV3Type.OBJECT,
            properties = properties.ifEmpty { null },
            required = requiredFields.ifEmpty { null }
        )

        val contentType = if (hasFileParameter) MediaType("multipart/form-data") else MediaType("application/json")

        return OpenAPIV3RequestBody(
            content = mapOf(
                contentType to OpenAPIV3MediaType(
                    schema = schema
                )
            ),
            required = true
        )
    }

    private fun generateResponses(returnType: DocumentParser.Type): Map<StatusCode, OpenAPIV3ResponseOrReference> {
        val successSchema = OpenAPIV3Schema(
            type = OpenAPIV3Type.OBJECT,
            properties = mapOf(
                "ok" to OpenAPIV3Schema(type = OpenAPIV3Type.BOOLEAN),
                "result" to convertTypeToSchema(returnType)
            ),
            required = listOf("ok", "result")
        )

        return mapOf(
            StatusCode("200") to OpenAPIV3Response(
                description = "Successful response",
                content = mapOf(
                    MediaType("application/json") to OpenAPIV3MediaType(
                        schema = successSchema
                    )
                )
            )
        )
    }

    private fun generateSchemas(objects: List<Object>): Map<String, OpenAPIV3SchemaOrReference> {
        val schemas = objects.associate { obj ->
            val schema = if (obj.isUnionType) {
                // For union types, create a schema with oneOf pointing to subtypes
                OpenAPIV3Schema(
                    description = obj.description.ifEmpty { null },
                    oneOf = obj.unionSubtypes.map { subtype ->
                        OpenAPIV3Reference(ref = Ref("#/components/schemas/$subtype"))
                    }.ifEmpty { null }
                )
            } else {
                // For regular types, create a normal object schema
                OpenAPIV3Schema(
                    type = OpenAPIV3Type.OBJECT,
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
        val errorSchema = OpenAPIV3Schema(
            type = OpenAPIV3Type.OBJECT,
            description = "Error response returned when a request fails",
            properties = mapOf(
                "ok" to OpenAPIV3Schema(
                    type = OpenAPIV3Type.BOOLEAN,
                    description = "Always false for error responses"
                ),
                "error_code" to OpenAPIV3Schema(
                    type = OpenAPIV3Type.INTEGER,
                    format = "int64",
                    description = "Error code"
                ),
                "description" to OpenAPIV3Schema(
                    type = OpenAPIV3Type.STRING,
                    description = "Human-readable description of the error"
                ),
                "parameters" to OpenAPIV3Reference(
                    ref = Ref("#/components/schemas/ResponseParameters")
                )
            ),
            required = listOf("ok", "error_code", "description")
        )

        return schemas + ("Error" to errorSchema)
    }

    private fun convertParameterToSchema(param: Method.Parameter): OpenAPIV3SchemaOrReference {
        return convertToSchemaWithDescription(param.type, param.description)
    }

    private fun convertFieldToSchema(field: Object.Field): OpenAPIV3SchemaOrReference {
        return convertToSchemaWithDescription(field.type, field.description)
    }

    /**
     * Convert a type to schema and optionally add the description if the schema is inline.
     * For references with descriptions, wrap them in an allOf to preserve the description.
     */
    private fun convertToSchemaWithDescription(
        type: DocumentParser.Type,
        description: String
    ): OpenAPIV3SchemaOrReference {
        return when (val baseSchema = convertTypeToSchema(type)) {
            is OpenAPIV3Schema -> {
                if (description.isNotEmpty()) {
                    baseSchema.copy(description = description)
                } else {
                    baseSchema
                }
            }

            is OpenAPIV3Reference -> {
                // For references with descriptions, wrap in allOf to preserve the description
                if (description.isNotEmpty()) {
                    OpenAPIV3Schema(
                        allOf = listOf(baseSchema),
                        description = description
                    )
                } else {
                    baseSchema
                }
            }
        }
    }

    private fun convertTypeToSchema(type: DocumentParser.Type): OpenAPIV3SchemaOrReference {
        return when (type) {
            is DocumentParser.Type.Simple -> {
                val typeName = type.name

                // Handle union types (e.g., "Integer or String", "Type1 or Type2")
                splitUnionTypes(typeName, Regex("\\s+or\\s+", RegexOption.IGNORE_CASE))?.let { types ->
                    return OpenAPIV3Schema(
                        oneOf = types.map { singleType -> convertSingleTypeToSchema(singleType) }
                    )
                }

                // Handle comma-separated types (e.g., "Type1, Type2, Type3 and Type4")
                splitUnionTypes(typeName, Regex(",|\\s+and\\s+", RegexOption.IGNORE_CASE))?.let { types ->
                    return OpenAPIV3Schema(
                        oneOf = types.map { singleType ->
                            OpenAPIV3Reference(ref = Ref("#/components/schemas/$singleType"))
                        }
                    )
                }

                // Handle single types
                convertSingleTypeToSchema(typeName)
            }

            is DocumentParser.Type.Generic -> {
                if (type.name == "Array" && type.typeArguments.size == 1) {
                    OpenAPIV3Schema(
                        type = OpenAPIV3Type.ARRAY,
                        items = convertTypeToSchema(type.typeArguments[0])
                    )
                } else {
                    OpenAPIV3Schema(type = OpenAPIV3Type.STRING)
                }
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

    private fun convertSingleTypeToSchema(typeName: String): OpenAPIV3SchemaOrReference {
        return when (typeName) {
            "String" -> OpenAPIV3Schema(type = OpenAPIV3Type.STRING)
            "Integer", "Int" -> OpenAPIV3Schema(type = OpenAPIV3Type.INTEGER, format = "int64")
            "Boolean" -> OpenAPIV3Schema(type = OpenAPIV3Type.BOOLEAN)
            "Float", "Double" -> OpenAPIV3Schema(type = OpenAPIV3Type.NUMBER, format = "double")
            else -> {
                // Always return a reference for custom types (including union types)
                // The union type definition itself will have oneOf in the schemas section
                OpenAPIV3Reference(ref = Ref("#/components/schemas/$typeName"))
            }
        }
    }
}
