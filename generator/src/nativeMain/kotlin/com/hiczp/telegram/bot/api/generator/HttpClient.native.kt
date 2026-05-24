package com.hiczp.telegram.bot.api.generator

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.http.*

internal actual fun createHttpClient(proxyUrl: String?): HttpClient {
    return HttpClient(Curl) {
        engine {
            if (proxyUrl != null) {
                proxy = ProxyBuilder.http(Url(proxyUrl))
            }
            sslVerify = false
        }
    }
}
