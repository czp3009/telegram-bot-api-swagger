package com.hiczp.telegram.bot.api.generator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

fun main() {
    runBlocking(Dispatchers.Default) {
        val document = BotApiDocumentFetcher().fetch()
        logger.info { "Fetched document" }
        println(document)
    }
}
