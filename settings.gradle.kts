rootProject.name = "telegram-bot-api-swagger"

pluginManagement {
    repositories {
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

include(":generator")
