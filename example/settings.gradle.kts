pluginManagement {
    includeBuild("..")
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("gg.ginco.hygradle.settings") // registers the (bundled) foojay toolchain resolver
}

rootProject.name = "example"
