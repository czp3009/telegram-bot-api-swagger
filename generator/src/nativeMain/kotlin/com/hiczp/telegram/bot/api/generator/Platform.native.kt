package com.hiczp.telegram.bot.api.generator

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun getEnvironmentVariable(name: String): String? {
    return getenv(name)?.toKString()
}
