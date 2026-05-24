package com.hiczp.telegram.bot.api.generator

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import platform.posix.mkdir

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual fun createDirectory(path: String) {
    mkdir(path, 0x1EDu) // 0755 (rwxr-xr-x)
}
