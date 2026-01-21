package com.hiczp.telegram.bot.api.generator

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

private val logger = KotlinLogging.logger {}

object DocumentFetcher {
    private val client by lazy {
        HttpClient(Curl) {
            engine {
                val proxyUrl = getEnvironmentVariable("HTTPS_PROXY")
                    ?: getEnvironmentVariable("HTTP_PROXY")
                if (proxyUrl != null) {
                    logger.info { "Using proxy $proxyUrl" }
                    proxy = ProxyBuilder.http(Url(proxyUrl))
                }
                sslVerify = false
            }
        }
    }

    suspend fun fetch() = client.get(URL).bodyAsText()

    private const val URL = "https://core.telegram.org/bots/api"
}
