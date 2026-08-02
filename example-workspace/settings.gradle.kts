pluginManagement {
    includeBuild("..")
    repositories { gradlePluginPortal() }
}

plugins {
    id("gg.ginco.hygradle.settings")
}

rootProject.name = "example-workspace"
include(":mod-alpha", ":mod-beta")
