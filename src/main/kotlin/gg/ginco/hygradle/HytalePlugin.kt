package gg.ginco.hygradle

import gg.ginco.hygradle.internal.DependencyBundleFilter
import gg.ginco.hygradle.tasks.GenerateManifestTask
import gg.ginco.hygradle.tasks.ProvisionServerTask
import gg.ginco.hygradle.tasks.RunServerTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*

class HytalePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JavaLibraryPlugin::class)

        project.repositories.mavenCentral()

        val ext = project.extensions.create<HytaleExtension>("hytale", project)

        project.afterEvaluate {
            // Hytale publishes to channel-prefixed paths; both channels are added when a
            // server version is configured (the hytaleApi dependency needs them) — projects
            // that only generate the manifest don't get extra repositories.
            if (!ext.serverVersion.isPresent) return@afterEvaluate
            project.repositories.maven { url = project.uri("https://maven.hytale.com/release") }
            project.repositories.maven { url = project.uri("https://maven.hytale.com/pre-release") }
        }

        project.afterEvaluate {
            // Default toolchain 25 (the server runs a very new JDK), but never override a
            // toolchain the build configures itself — a hard requirement on JDK 25 fails
            // builds on machines that can't detect or provision it.
            val javaExt = project.extensions.getByType(JavaPluginExtension::class)
            if (javaExt.toolchain.languageVersion.isPresent) return@afterEvaluate
            javaExt.toolchain.languageVersion.set(JavaLanguageVersion.of(25))

            // When the Kotlin plugin is present, align its toolchain too so compileKotlin
            // targets 25. Reflective: no build-time coupling to the Kotlin plugin version.
            try {
                val kotlinExt = project.extensions.findByName("kotlin")
                if (kotlinExt == null) return@afterEvaluate
                val kotlinHasOwnToolchain = runCatching {
                    val spec = kotlinExt.javaClass.methods
                        .firstOrNull { (it.name == "jvmToolchain" || it.name == "getJvmToolchain") && it.parameterCount == 0 }
                        ?.invoke(kotlinExt)
                    val languageVersion = spec?.javaClass?.methods
                        ?.firstOrNull { it.name == "getLanguageVersion" }?.invoke(spec)
                    (languageVersion as? Property<*>)?.isPresent == true
                }.getOrDefault(false)
                if (!kotlinHasOwnToolchain) {
                    kotlinExt.javaClass.getMethod("jvmToolchain", Int::class.javaPrimitiveType)
                        .invoke(kotlinExt, 25)
                }
            } catch (t: Throwable) {
                project.logger.lifecycle(
                    "hygradle: could not align Kotlin toolchain to 25 ({}); set kotlin {{ jvmToolchain(25) }} explicitly",
                    t.message)
            }
        }

        // When the hytest plugin is also applied, forward the same extraMods
        // into its test-server deployment (reflective: no build-time coupling,
        // graceful absence on either side).
        project.afterEvaluate {
            if (!project.plugins.hasPlugin("gg.ginco.hytest")) return@afterEvaluate
            try {
                val hytestExt = project.extensions.findByName("hytest") ?: return@afterEvaluate
                val prop = hytestExt.javaClass.methods.firstOrNull { it.name == "getExtraMods" }
                    ?: return@afterEvaluate
                @Suppress("UNCHECKED_CAST")
                val into = prop.invoke(hytestExt) as org.gradle.api.provider.ListProperty<java.nio.file.Path>
                val paths = ext.server.extraMods.files.map { it.toPath() }
                into.addAll(paths)
                paths.forEach { p ->
                    project.logger.debug("hygradle: forwarding extra mod {} into hytest extraMods", p)
                }
            } catch (t: Throwable) {
                project.logger.lifecycle(
                    "hygradle: could not auto-forward extraMods to hytest ({}); set hytest {{}} extraMods manually",
                    t.message)
            }
        }

        project.afterEvaluate {
            check(ext.group.isPresent) { "hytale { group } is required" }
            check(ext.mainClass.isPresent) { "hytale { mainClass } is required" }
            check(project.version.toString() != "unspecified") {
                "hytale plugins must set project.version — the manifest's 'Version' field would otherwise be 'unspecified'"
            }
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
                    // Bundle every runtime jar except the ones the user excluded/limited via
                    // hytale { bundleExclude(...)/bundleOnly(...) } — resolved by group:artifact
                    // coordinates so a shared-framework thin jar can be left out (issue #4).
                    // Read lazily (inside the provider) so the final configured values are used.
                    DependencyBundleFilter(ext.bundleIncludes.get(), ext.bundleExcludes.get())
                        .select(cfg.incoming.artifacts)
                        .map { project.zipTree(it) }
                })
            }
        }

        val provisionServer = project.tasks.register<ProvisionServerTask>("provisionServer") {
            group = "hytale"
            description = "Downloads the Hytale server files into the run directory"
            serverVersion.set(ext.serverVersion)
            serverDir.set(ext.server.serverDir)
            gradleUserHomeDir.set(project.layout.dir(project.provider { project.gradle.gradleUserHomeDir }))
        }

        project.tasks.register<RunServerTask>("runServer") {
            group = "hytale"
            description = "Starts a Hytale dev server with this plugin loaded"

            pluginJar.set(project.tasks.named<Jar>("jar").flatMap { it.archiveFile })
            extraMods.setFrom(ext.server.extraMods)
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
            // gradleUserHomeDir must be wired even though provisionServer already got it:
            // RunServerTask.run() reads it to locate the shared version cache (issue #2)
            gradleUserHomeDir.set(project.layout.dir(project.provider { project.gradle.gradleUserHomeDir }))
            dependsOn("jar", provisionServer)
        }
    }
}
