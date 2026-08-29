package gg.ginco.hygradle

import gg.ginco.hygradle.tasks.GenerateManifestTask
import gg.ginco.hygradle.tasks.RunServerTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*

class HytalePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JavaLibraryPlugin::class)

        // Hytale publishes to channel-prefixed paths; add both so pre-release versions resolve too
        project.repositories.maven { url = project.uri("https://maven.hytale.com/release") }
        project.repositories.maven { url = project.uri("https://maven.hytale.com/pre-release") }
        project.repositories.mavenCentral()

        project.extensions.configure<JavaPluginExtension> {
            toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
        }

        // When Kotlin plugin is present, align its toolchain too so compileKotlin targets 25
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            val kotlinExt = project.extensions.findByName("kotlin")
            if (kotlinExt != null) {
                kotlinExt.javaClass.getMethod("jvmToolchain", Int::class.javaPrimitiveType)
                    .invoke(kotlinExt, 25)
            }
        }

        val ext = project.extensions.create<HytaleExtension>("hytale", project)

        project.afterEvaluate {
            check(ext.group.isPresent) { "hytale { group } is required" }
            check(ext.mainClass.isPresent) { "hytale { mainClass } is required" }
        }
        setupConfigurations(project, ext)
        setupTasks(project, ext)
    }

    private fun setupConfigurations(project: Project, ext: HytaleExtension) {
        // hytaleApi: compileOnly — server provides the same classes at runtime
        val hytaleApi = project.configurations.create("hytaleApi") {
            isCanBeConsumed = false
            isCanBeResolved = false
            description = "Hytale server jar — provided at runtime, compile-only in plugin jars"
        }
        project.configurations.named("compileOnly") { extendsFrom(hytaleApi) }
        project.configurations.named("testCompileOnly") { extendsFrom(hytaleApi) }

        project.afterEvaluate {
            val version = ext.serverVersion.orNull ?: return@afterEvaluate
            project.dependencies.add("hytaleApi", "${ext.artifact.get()}:$version")
        }
    }

    private fun setupTasks(project: Project, ext: HytaleExtension) {
        val generateManifest = project.tasks.register<GenerateManifestTask>("generatePluginManifest") {
            group = "hytale"
            description = "Generates manifest.json from the hytale { } extension"

            pluginGroup.set(ext.group)
            pluginName.set(ext.name)
            pluginVersion.set(project.provider { project.version.toString() })
            pluginDescription.set(ext.description)
            authors.set(ext.authors)
            website.set(ext.website)
            mainClass.set(ext.mainClass)
            serverVersion.set(ext.serverVersion)
            dependencies.set(ext.dependencies)
            optionalDependencies.set(ext.optionalDependencies)
            loadBefore.set(ext.loadBefore)
            includesAssetPack.set(ext.includesAssetPack)
            disabledByDefault.set(ext.disabledByDefault)
            subPlugins.set(ext.subPlugins)
            outputDir.set(project.layout.buildDirectory.dir("generated/hytale"))
        }

        project.tasks.named<Jar>("jar") {
            from(generateManifest.flatMap { it.outputDir })
            if (ext.bundleDependencies.get()) {
                from(project.configurations.named("runtimeClasspath").map { cfg ->
                    cfg.filter { it.name.endsWith(".jar") }.map { project.zipTree(it) }
                })
            }
        }

        project.tasks.register<RunServerTask>("runServer") {
            group = "hytale"
            description = "Starts a Hytale dev server with this plugin loaded"

            pluginJar.set(project.tasks.named<Jar>("jar").flatMap { it.archiveFile })
            serverVersion.set(ext.serverVersion)
            serverDir.set(ext.server.serverDir)
            jvmArgs.set(ext.server.jvmArgs)
            debugEnabled.set(ext.server.debugEnabled)
            debugPort.set(ext.server.debugPort)
            debugSuspend.set(ext.server.debugSuspend)
            sessionAuth.set(ext.server.sessionAuth)
            requireDcevm.set(ext.server.requireDcevm)
            useHotswapAgent.set(ext.server.useHotswapAgent)
            hotswapAgentPath.set(ext.server.hotswapAgentPath)
            jbrHome.set(ext.server.jbrHome)
            gradleUserHomeDir.set(project.layout.dir(project.provider { project.gradle.gradleUserHomeDir }))

            dependsOn("jar")
        }
    }
}
