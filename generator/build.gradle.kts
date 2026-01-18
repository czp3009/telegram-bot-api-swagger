plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    mingwX64 {
        binaries {
            executable {
                entryPoint = "com.hiczp.telegram.bot.api.generator.main"
            }
        }
    }

    @Suppress("OPT_IN_USAGE")
    dependencies {
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.cio)
    }
}
