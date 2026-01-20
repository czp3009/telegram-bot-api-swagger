import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

// Task to generate Version.kt with the project version using KotlinPoet
val generateVersionFile by tasks.registering(GenerateVersionTask::class) {
    projectVersion.set(project.version.toString())
    packageName.set("com.hiczp.telegram.bot.api.generator")
    outputDirectory.set(layout.buildDirectory.dir("generated/kotlin"))
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
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.curl)
                implementation(libs.ksoup)
                implementation(libs.openapi.bindings)
                implementation(libs.kotlinLogging)
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

// Make sure the version file is generated before Kotlin compilation
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateVersionFile)
}
