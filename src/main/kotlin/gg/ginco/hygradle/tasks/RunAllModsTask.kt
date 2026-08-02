package gg.ginco.hygradle.tasks

import gg.ginco.hygradle.internal.ServerDownloader
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

@UntrackedTask(because = "Always re-runs to start the dev server")
abstract class RunAllModsTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Classpath
    abstract val pluginJars: ConfigurableFileCollection

    @get:Input
    abstract val serverVersion: Property<String>

    @get:Internal
    abstract val serverDir: DirectoryProperty

    @get:Input
    abstract val jvmArgs: ListProperty<String>

    @TaskAction
    fun run() {
        check(pluginJars.files.isNotEmpty()) {
            "runAllMods: no mod jars configured. Ensure subprojects apply gg.ginco.hygradle and are not all set as hostProject."
        }

        val runDir = serverDir.get().asFile
        ServerDownloader.ensureServerFiles(serverVersion.get(), runDir, logger)

        val modsDir = File(runDir, "mods")
        val modsTmp = File(runDir, "mods.tmp").also {
            it.deleteRecursively()
            it.mkdirs()
        }

        pluginJars.forEach { jar ->
            jar.copyTo(File(modsTmp, jar.name), overwrite = true)
        }
        modsDir.deleteRecursively()
        try {
            java.nio.file.Files.move(
                modsTmp.toPath(), modsDir.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                modsTmp.toPath(), modsDir.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
        logger.lifecycle("Deployed ${pluginJars.files.size} mod(s) to ${modsDir.absolutePath}")

        val serverJar = File(runDir, "HytaleServer.jar")
        val assets = File(runDir, "Assets.zip")
        logger.lifecycle("Starting Hytale server ${serverVersion.get()} with ${pluginJars.files.size} mod(s)")

        execOps.exec {
            workingDir = runDir
            standardInput = System.`in`
            commandLine(buildList {
                add(Jvm.current().javaExecutable.absolutePath)
                addAll(jvmArgs.get())
                add("-jar")
                add(serverJar.absolutePath)
                if (assets.isFile) {
                    add("--assets")
                    add(assets.absolutePath)
                }
            })
        }
    }
}
