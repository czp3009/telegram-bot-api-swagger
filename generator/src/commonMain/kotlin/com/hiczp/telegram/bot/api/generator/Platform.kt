package com.hiczp.telegram.bot.api.generator

internal expect fun getEnvironmentVariable(name: String): String?

internal expect fun fileExists(path: String): Boolean

internal expect fun createDirectory(path: String)

internal expect fun writeFile(path: String, content: String)
