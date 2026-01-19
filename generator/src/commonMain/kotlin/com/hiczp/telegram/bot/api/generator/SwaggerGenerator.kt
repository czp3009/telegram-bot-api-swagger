package com.hiczp.telegram.bot.api.generator

import com.hiczp.telegram.bot.api.generator.DocumentParser.Method
import com.hiczp.telegram.bot.api.generator.DocumentParser.Object
import community.flock.kotlinx.openapi.bindings.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive

private val logger = KotlinLogging.logger {}

object SwaggerGenerator {
    fun generate(methods: List<Method>, objects: List<Object>): String {
        logger.info { "Generating OpenAPI specification for ${methods.size} methods and ${objects.size} objects" }

        val openApi = OpenAPIV3Model(
            openapi = "3.0.3",
            info = InfoObject(
                title = "Telegram Bot API",
                description = "Auto-generated OpenAPI specification for Telegram Bot API",
                version = "7.0"
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
            val typeString = param.type.toString()
            typeString.contains("InputFile", ignoreCase = true) ||
                    typeString.contains("InputMedia", ignoreCase = true)
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
            param.name to convertTypeToSchema(param.type)
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
        return objects.associate { obj ->
            obj.name to OpenAPIV3Schema(
                type = OpenAPIV3Type.OBJECT,
                description = obj.description.ifEmpty { null },
                properties = obj.fields.associate { field ->
                    field.name to convertTypeToSchema(field.type)
                }.ifEmpty { null },
                required = obj.fields.filter { it.required }.map { it.name }.ifEmpty { null }
            )
        }
    }

    private fun convertTypeToSchema(type: DocumentParser.Type): OpenAPIV3SchemaOrReference {
        return when (type) {
            is DocumentParser.Type.Simple -> {
                val typeName = type.name

                // Handle union types (e.g., "Integer or String", "Type1 or Type2")
                if (typeName.contains(" or ", ignoreCase = true)) {
                    val types = typeName.split(Regex("\\s+or\\s+", RegexOption.IGNORE_CASE))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    if (types.size > 1) {
                        return OpenAPIV3Schema(
                            oneOf = types.map { singleType -> convertSingleTypeToSchema(singleType) }
                        )
                    }
                }

                // Handle comma-separated types (e.g., "Type1, Type2, Type3 and Type4")
                if (typeName.contains(",") || typeName.contains(" and ", ignoreCase = true)) {
                    val types = typeName.split(Regex(",|\\s+and\\s+", RegexOption.IGNORE_CASE))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    if (types.size > 1) {
                        return OpenAPIV3Schema(
                            oneOf = types.map { singleType ->
                                OpenAPIV3Reference(ref = Ref("#/components/schemas/$singleType"))
                            }
                        )
                    }
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

    private fun convertSingleTypeToSchema(typeName: String): OpenAPIV3SchemaOrReference {
        return when (typeName) {
            "String" -> OpenAPIV3Schema(type = OpenAPIV3Type.STRING)
            "Integer", "Int" -> OpenAPIV3Schema(type = OpenAPIV3Type.INTEGER, format = "int64")
            "Boolean" -> OpenAPIV3Schema(type = OpenAPIV3Type.BOOLEAN)
            "Float", "Double" -> OpenAPIV3Schema(type = OpenAPIV3Type.NUMBER, format = "double")
            else -> OpenAPIV3Reference(ref = Ref("#/components/schemas/$typeName"))
        }
    }
}
