package com.hiczp.telegram.bot.api.generator

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun getEnvironmentVariable(name: String): String? {
    return getenv(name)?.toKString()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun fileExists(path: String): Boolean {
    return access(path, F_OK) == 0
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun writeFile(path: String, content: String) {
    val file = fopen(path, "w")
    if (file != null) {
        try {
            fputs(content, file)
        } finally {
            fclose(file)
        }
    } else {
        error("Failed to open file: $path")
    }
}
