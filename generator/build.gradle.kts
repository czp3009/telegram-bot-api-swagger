import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("plugin.serialization") version "2.3.0"
}

kotlin {
    applyDefaultHierarchyTemplate()
    
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
        val nativeMain by getting
        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }
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

    @Suppress("OPT_IN_USAGE")
    dependencies {
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.curl)
        implementation(libs.ksoup)
        implementation(libs.openapi.bindings)
        implementation(libs.kotlinLogging)
    }
}
