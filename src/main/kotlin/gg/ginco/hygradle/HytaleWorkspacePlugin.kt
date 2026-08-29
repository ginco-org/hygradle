package gg.ginco.hygradle

import gg.ginco.hygradle.tasks.RunAllModsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*

class HytaleWorkspacePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        check(project == project.rootProject) {
            "gg.ginco.hygradle.workspace must only be applied to the root project"
        }

        val ext = project.extensions.create<HytaleWorkspaceExtension>("hytaleWorkspace")
        ext.modProjects.convention(emptyList())
        ext.serverDir.convention(project.layout.projectDirectory.dir("run"))
        ext.jvmArgs.convention(listOf("-Xmx4G", "-Xms1G"))
        ext.sessionAuth.convention(true)

        val runAllMods = project.tasks.register<RunAllModsTask>("runAllMods") {
            group = "hytale"
            description = "Builds all Hytale mod subprojects and starts a shared dev server"
            serverVersion.set(ext.serverVersion)
            serverDir.set(ext.serverDir)
            jvmArgs.set(ext.jvmArgs)
            sessionAuth.set(ext.sessionAuth)
            gradleUserHomeDir.set(project.layout.dir(project.provider { project.gradle.gradleUserHomeDir }))
        }

        // Collect subprojects as they get the hygradle plugin applied
        val modSubprojects = java.util.Collections.synchronizedList(mutableListOf<Project>())

        project.subprojects {
            val subproject = this
            pluginManager.withPlugin("gg.ginco.hygradle") {
                val childExt = subproject.extensions.getByType<HytaleExtension>()
                // Convention resolves lazily; the workspace serverVersion is set during root project evaluation
                // which completes before any subproject's afterEvaluate fires, so the hytaleApi dependency
                // wiring in HytalePlugin.setupConfigurations will correctly see the propagated version.
                childExt.serverVersion.convention(ext.serverVersion)
                modSubprojects.add(subproject)
            }
        }

        // Wire jars after all projects have been evaluated
        project.gradle.projectsEvaluated {
            // Validate serverVersion
            check(ext.serverVersion.isPresent) {
                "hytaleWorkspace { serverVersion } is required"
            }

            val hostPath = ext.hostProject.orNull?.takeIf { it.isNotEmpty() }

            // Validate hostProject if set
            if (hostPath != null) {
                val hostSub = project.findProject(hostPath)
                checkNotNull(hostSub) {
                    "hytaleWorkspace.hostProject references '$hostPath' but no such subproject exists"
                }
            }

            // Validate explicit modProjects list if provided
            val explicitMods = ext.modProjects.get()
            if (explicitMods.isNotEmpty()) {
                explicitMods.forEach { path ->
                    val sub = project.findProject(path)
                    checkNotNull(sub) {
                        "hytaleWorkspace.modProjects references '$path' but no such subproject exists"
                    }
                    check(sub.plugins.hasPlugin("gg.ginco.hygradle")) {
                        "hytaleWorkspace.modProjects references '$path' but it does not have the gg.ginco.hygradle plugin applied"
                    }
                }
            }

            // Wire mod jars into runAllMods; explicit modProjects list overrides auto-detection
            val projectsToWire = if (explicitMods.isNotEmpty()) {
                explicitMods.mapNotNull { project.findProject(it) }
            } else {
                modSubprojects.toList()
            }
            projectsToWire.forEach { subproject ->
                if (subproject.path != hostPath) {
                    val jarTask = subproject.tasks.named<Jar>("jar")
                    runAllMods.configure {
                        pluginJars.from(jarTask)  // from(taskProvider) sets builtBy implicitly
                    }
                }
            }
        }
    }
}
