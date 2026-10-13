package gg.ginco.hygradle.tasks

import gg.ginco.hygradle.internal.ModsDeploy
import gg.ginco.hygradle.internal.ServerLauncher
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
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

    @get:Input
    abstract val sessionAuth: Property<Boolean>

    @get:Input abstract val debugEnabled: Property<Boolean>
    @get:Input abstract val debugPort: Property<Int>
    @get:Input abstract val debugSuspend: Property<Boolean>
    @get:Input abstract val requireDcevm: Property<Boolean>
    @get:Input abstract val useHotswapAgent: Property<Boolean>
    @get:PathSensitive(PathSensitivity.ABSOLUTE) @get:InputFile @get:Optional abstract val hotswapAgentPath: RegularFileProperty
    @get:Input @get:Optional abstract val jbrHome: Property<String>

    @get:Internal
    abstract val gradleUserHomeDir: DirectoryProperty
    @TaskAction
    fun run() {
        val modJars = pluginJars.files
        check(modJars.isNotEmpty()) {
            "runAllMods: no mod jars configured. Ensure subprojects apply gg.ginco.hygradle and are not all set as hostProject."
        }
        val runDir = serverDir.get().asFile
        val cacheDir = File(gradleUserHomeDir.get().asFile, "caches/hygradle/server")
        // Server files are provisioned by the provisionServer task (see dependsOn);
        // this task only references them by path.
        ModsDeploy.deploy(runDir, modJars, logger)

        logger.lifecycle("Starting Hytale server ${serverVersion.get()} with ${modJars.size} mod(s)")
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
