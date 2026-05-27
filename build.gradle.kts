plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
    implementation("com.google.code.gson:gson:2.13.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    website = "https://github.com/ginco/hygradle"
    vcsUrl = "https://github.com/ginco/hygradle.git"

    plugins {
        create("hytale") {
            id = "gg.ginco.hygradle"
            implementationClass = "gg.ginco.hygradle.HytalePlugin"
            displayName = "Hygradle"
            description = "Gradle plugin for Hytale plugin development"
            tags = listOf("hytale", "game")
        }
        create("hytaleSettings") {
            id = "gg.ginco.hygradle.settings"
            implementationClass = "gg.ginco.hygradle.settings.HygradleSettingsPlugin"
            displayName = "Hygradle Settings"
            description = "Applies the foojay toolchain resolver for Hygradle projects"
            tags = listOf("hytale", "toolchain")
        }
    }
}
