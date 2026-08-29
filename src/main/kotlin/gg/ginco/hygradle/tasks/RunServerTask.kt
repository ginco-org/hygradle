package gg.ginco.hygradle.tasks

import gg.ginco.hygradle.internal.GameSession
import gg.ginco.hygradle.internal.HotswapAgentResolver
import gg.ginco.hygradle.internal.HytaleAuth
import gg.ginco.hygradle.internal.ServerDownloader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
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

        val versionCache = ServerDownloader.ensureServerFiles(serverVersion.get(), runDir, cacheDir, logger)

        val modsDir = File(runDir, "mods").also { it.mkdirs() }
        val jar = pluginJar.get().asFile
        jar.copyTo(File(modsDir, jar.name), overwrite = true)
        logger.lifecycle("Deployed ${jar.name} to ${modsDir.absolutePath}")

        val serverJar = File(versionCache, "HytaleServer.jar")
        val assets = File(versionCache, "Assets.zip")
        logger.lifecycle("Starting Hytale server ${serverVersion.get()} in ${runDir.absolutePath}")
        val binName = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
        val javaExec = if (jbrHome.isPresent) {
            File(File(jbrHome.get(), "bin"), binName).absolutePath.also { path ->
                require(File(path).isFile) {
                    "jbrHome '${jbrHome.get()}' does not contain a java executable at '$path'"
                }
            }
        } else {
            Jvm.current().javaExecutable.absolutePath
        }

        val isDcevm = if (requireDcevm.get()) checkDcevm(javaExec) else false

        if (debugEnabled.get()) {
            require(debugPort.get() in 1..65535) { "debugPort must be in range 1..65535, got ${debugPort.get()}" }
        }

        val agentJarPath: String? = when {
            useHotswapAgent.get() && hotswapAgentPath.isPresent -> hotswapAgentPath.get().asFile.absolutePath
            useHotswapAgent.get() -> HotswapAgentResolver.resolve(gradleUserHomeDir.get().asFile, logger)
            else -> null
        }

        execOps.exec {
            workingDir = runDir
            standardInput = System.`in`
            commandLine(buildList {
                add(javaExec)
                addAll(jvmArgs.get())
                if (debugEnabled.get()) {
                    val suspend = if (debugSuspend.get()) "y" else "n"
                    add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=127.0.0.1:${debugPort.get()}")
                    logger.lifecycle("Debug listening on port ${debugPort.get()} (suspend=$suspend)")
                }
                if (agentJarPath != null) {
                    if (isDcevm) add("-XX:+AllowEnhancedClassRedefinition")
                    add("-javaagent:$agentJarPath")
                }
                add("-jar")
                add(serverJar.absolutePath)
                if (assets.isFile) {
                    add("--assets")
                    add(assets.absolutePath)
                }
                addSessionAuthArgs()
            })
        }
    }

    /**
     * Exchanges the cached `auth:server` token for a game session and appends
     * --session-token / --identity-token so the server starts fully
     * authenticated (no in-server /auth needed). Fail-soft: if session
     * creation fails, the server still starts and can be authed manually.
     */
    private fun MutableList<String>.addSessionAuthArgs() {
        if (!sessionAuth.getOrElse(true)) return
        try {
            val token = HytaleAuth.getAccessToken(logger, HytaleAuth.SERVER)
            val session = GameSession.create(token, logger)
            add("--session-token")
            add(session.sessionToken)
            add("--identity-token")
            add(session.identityToken)
        } catch (e: Exception) {
            logger.warn("Session auth failed: ${e.message}")
            logger.warn("Starting server WITHOUT session tokens — run /auth in the server console if it requires authentication.")
        }
    }

    private fun checkDcevm(javaExec: String): Boolean {
        val out = java.io.ByteArrayOutputStream()
        val result = execOps.exec {
            commandLine(javaExec, "-XX:+AllowEnhancedClassRedefinition", "-version")
            standardOutput = out
            errorOutput = out
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            val versionOutput = out.toString(Charsets.UTF_8)
            throw org.gradle.api.GradleException(
                "requireDcevm is enabled but the JVM at '$javaExec' does not support -XX:+AllowEnhancedClassRedefinition (DCEVM/JBR required).\n" +
                "JVM output: ${versionOutput.lines().take(3).joinToString("\n")}\n" +
                "Set server { jbrHome = \"/path/to/jbr\" } to point to a JetBrains Runtime with DCEVM."
            )
        }
        val version = out.toString(Charsets.UTF_8).lines().firstOrNull()?.trim() ?: "unknown"
        logger.lifecycle("DCEVM capability confirmed: $version")
        return true
    }
}
