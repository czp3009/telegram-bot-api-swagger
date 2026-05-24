package com.hiczp.telegram.bot.api.generator

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.client.statement.*

private val logger = KotlinLogging.logger {}

object DocumentFetcher {
    private val client by lazy {
        val proxyUrl = getEnvironmentVariable("HTTPS_PROXY")
            ?: getEnvironmentVariable("HTTP_PROXY")
        if (proxyUrl != null) {
            logger.info { "Using proxy $proxyUrl" }
        }
        createHttpClient(proxyUrl)
    }

    suspend fun fetch() = client.get(URL).bodyAsText()

    private const val URL = "https://core.telegram.org/bots/api"
}
