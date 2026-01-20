plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
}

group = "com.hiczp"
version = "2025.12.31"

subprojects {
    group = rootProject.group
    version = rootProject.version
}
