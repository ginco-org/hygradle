pluginManagement {
    includeBuild("..")
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("gg.ginco.hygradle.settings")
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "example"
