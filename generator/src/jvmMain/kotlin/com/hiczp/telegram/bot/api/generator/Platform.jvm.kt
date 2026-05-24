package com.hiczp.telegram.bot.api.generator

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import java.nio.file.Files
import java.nio.file.Paths
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

internal actual fun createHttpClient(proxyUrl: String?): HttpClient {
    return HttpClient(CIO) {
        engine {
            if (proxyUrl != null) {
                proxy = ProxyBuilder.http(Url(proxyUrl))
            }
            https {
                trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
            }
        }
    }
}

internal actual fun getEnvironmentVariable(name: String): String? {
    return System.getenv(name)
}

internal actual fun fileExists(path: String): Boolean {
    return Files.exists(Paths.get(path))
}

internal actual fun createDirectory(path: String) {
    Files.createDirectories(Paths.get(path))
}

internal actual fun writeFile(path: String, content: String) {
    Files.write(Paths.get(path), content.toByteArray(Charsets.UTF_8))
}
