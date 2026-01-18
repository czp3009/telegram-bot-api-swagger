import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    mingwX64()
    linuxX64()
    macosX64()
    macosArm64()

    targets.withType<KotlinNativeTarget> {
        binaries.executable {
            entryPoint = "com.hiczp.telegram.bot.api.generator.main"
        }
    }

    @Suppress("OPT_IN_USAGE")
    dependencies {
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.curl)
        implementation(libs.ksoup)
        implementation(libs.openapi.bindings)
        implementation(libs.kotlinLogging)
    }
}
