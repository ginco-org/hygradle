package gg.ginco.hygradle.tasks

import gg.ginco.hygradle.internal.ServerDownloader
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

@UntrackedTask(because = "Always re-runs to start the dev server")
abstract class RunServerTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:InputFile
    abstract val pluginJar: RegularFileProperty

    @get:Input
    abstract val serverVersion: Property<String>

    @get:Internal
    abstract val serverDir: DirectoryProperty

    @get:Input
    abstract val jvmArgs: ListProperty<String>

    @TaskAction
    fun run() {
        val runDir = serverDir.get().asFile

        ServerDownloader.ensureServerFiles(serverVersion.get(), runDir, logger)

        val modsDir = File(runDir, "mods").also { it.mkdirs() }
        val jar = pluginJar.get().asFile
        jar.copyTo(File(modsDir, jar.name), overwrite = true)
        logger.lifecycle("Deployed ${jar.name} to ${modsDir.absolutePath}")

        val serverJar = File(runDir, "HytaleServer.jar")
        val assets = File(runDir, "Assets.zip")
        logger.lifecycle("Starting Hytale server ${serverVersion.get()} in ${runDir.absolutePath}")

        execOps.exec {
            workingDir = runDir
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
