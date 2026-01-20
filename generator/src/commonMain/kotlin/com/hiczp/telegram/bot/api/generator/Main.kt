package com.hiczp.telegram.bot.api.generator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

fun main() {
    runBlocking(Dispatchers.Default) {
        val html = DocumentFetcher.fetch()
        logger.info { "Fetched document" }
        val apiVersion = DocumentParser.extractVersion(html)
        logger.info { "Extracted API version: $apiVersion" }
        val (methods, objects) = DocumentParser.parse(html)
        logger.info { "Parsed document" }
        val swaggerJson = SwaggerGenerator.generate(methods, objects, apiVersion)
        logger.info { "Generated swagger" }

        // Write to the swagger folder
        val outputDir = "swagger"
        val outputFile = "$outputDir/telegram-bot-api.json"

        // Create the directory if it doesn't exist
        if (!fileExists(outputDir)) {
            createDirectory(outputDir)
            logger.info { "Created directory: $outputDir" }
        }

        // Write JSON to the file
        writeFile(outputFile, swaggerJson)
        logger.info { "Swagger JSON written to: $outputFile" }
    }
}
