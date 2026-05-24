@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm {
        mainRun {
            mainClass.set("com.hiczp.telegram.bot.api.generator.MainKt")
        }
    }
    mingwX64()
    linuxX64()
    macosX64()
    macosArm64()

    targets.withType<KotlinNativeTarget> {
        binaries.executable {
            entryPoint = "com.hiczp.telegram.bot.api.generator.main"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ksoup)
                implementation(libs.openapi.bindings)
                implementation(libs.kotlinLogging)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.logback.classic)
            }
        }
        nativeMain {
            dependencies {
                implementation(libs.ktor.client.curl)
            }
        }
        val nativeMain by getting
        val unixMain by creating {
            dependsOn(nativeMain)
        }
        val linuxX64Main by getting {
            dependsOn(unixMain)
        }
        val macosX64Main by getting {
            dependsOn(unixMain)
        }
        val macosArm64Main by getting {
            dependsOn(unixMain)
        }
    }
}
