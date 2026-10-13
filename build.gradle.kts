plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
    implementation("com.google.code.gson:gson:2.13.1")
    // Bundled so gg.ginco.hygradle.settings can register toolchain auto-provisioning on
    // Gradle 9.x too (the built-in foojay-resolver-convention was removed in Gradle 9.0).
    // 1.0.0 is the first version that supports Gradle 9; it requires Java 17+, which
    // hygradle consumers need anyway for the Java 25 toolchain.
    implementation("org.gradle.toolchains:foojay-resolver:1.0.0")
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    website = "https://github.com/ginco-org/hygradle"
    vcsUrl = "https://github.com/ginco-org/hygradle.git"

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
        create("hytaleWorkspace") {
            id = "gg.ginco.hygradle.workspace"
            implementationClass = "gg.ginco.hygradle.HytaleWorkspacePlugin"
            displayName = "Hygradle Workspace"
            description = "Multi-project workspace support for Hytale mod monorepos"
            tags = listOf("hytale", "workspace", "monorepo")
        }
    }
}
