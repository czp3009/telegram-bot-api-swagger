package com.hiczp.telegram.bot.api.generator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

fun main() {
    runBlocking(Dispatchers.Default) {
        val html = DocumentFetcher.fetch()
        logger.info { "Fetched document" }
        val (methods, objects) = DocumentParser.parse(html)
        logger.info { "Parsed document" }
        println(methods)
        println(objects)
        SwaggerGenerator.generate(methods, objects)
        logger.info { "Generated swagger" }
    }
}
