package com.hiczp.telegram.bot.api.generator

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.mkdir

@OptIn(ExperimentalForeignApi::class)
internal actual fun createDirectory(path: String) {
    mkdir(path)
}
