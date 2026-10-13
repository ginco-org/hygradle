package gg.ginco.hygradle.tasks

import gg.ginco.hygradle.internal.ModsDeploy
import gg.ginco.hygradle.internal.ServerLauncher
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
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

    /** Dependency plugin jars copied into mods/ beside [pluginJar]. */
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:InputFiles
    abstract val extraMods: ConfigurableFileCollection

    @get:Input
    abstract val serverVersion: Property<String>

    @get:Internal
    abstract val serverDir: DirectoryProperty

    @get:Input
    abstract val jvmArgs: ListProperty<String>

    @get:Input abstract val debugEnabled: Property<Boolean>
    @get:Input abstract val debugPort: Property<Int>
    @get:Input abstract val debugSuspend: Property<Boolean>
    @get:Input abstract val sessionAuth: Property<Boolean>
    @get:Input abstract val requireDcevm: Property<Boolean>
    @get:Input abstract val useHotswapAgent: Property<Boolean>
    @get:PathSensitive(PathSensitivity.ABSOLUTE) @get:InputFile @get:Optional abstract val hotswapAgentPath: RegularFileProperty
    @get:Input @get:Optional abstract val jbrHome: Property<String>
    @get:Internal abstract val gradleUserHomeDir: DirectoryProperty

    @TaskAction
    fun run() {
        val runDir = serverDir.get().asFile
        val cacheDir = File(gradleUserHomeDir.get().asFile, "caches/hygradle/server")
        // Server files are provisioned by the provisionServer task (see dependsOn);
        // this task only references them by path.

        // mods/ is rebuilt from scratch — stale jars from previous builds (renamed or
        // re-versioned plugins) would otherwise accumulate and get loaded twice.
        val jar = pluginJar.get().asFile
        ModsDeploy.deploy(runDir, listOf(jar) + extraMods.files, logger)

        logger.lifecycle("Starting Hytale server ${serverVersion.get()} in ${runDir.absolutePath}")
        val command = ServerLauncher.commandLine(
            ServerLauncher.Spec(
                serverVersion = serverVersion.get(),
                cacheDir = cacheDir,
                gradleUserHomeDir = gradleUserHomeDir.get().asFile,
                jvmArgs = jvmArgs.get(),
                debugEnabled = debugEnabled.get(),
                debugPort = debugPort.get(),
                debugSuspend = debugSuspend.get(),
                requireDcevm = requireDcevm.get(),
                useHotswapAgent = useHotswapAgent.get(),
                hotswapAgentPath = hotswapAgentPath.orNull?.asFile?.absolutePath,
                jbrHome = jbrHome.orNull,
                sessionAuth = sessionAuth.get(),
            ),
            execOps,
            logger,
        )

        execOps.exec {
            workingDir = runDir
            standardInput = System.`in`
            commandLine(command)
        }
    }
}
