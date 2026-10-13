package gg.ginco.hygradle.internal

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import java.io.File

/**
 * Assembles the command line for launching a Hytale dev server — shared by
 * [gg.ginco.hygradle.tasks.RunServerTask] and [gg.ginco.hygradle.tasks.RunAllModsTask]
 * so both get the same debug / hotswap / session-auth behaviour.
 */
internal object ServerLauncher {
    class Spec(
        var serverVersion: String,
        var cacheDir: File,
        var gradleUserHomeDir: File,
        var jvmArgs: List<String>,
        var debugEnabled: Boolean = false,
        var debugPort: Int = 5005,
        var debugSuspend: Boolean = false,
        var requireDcevm: Boolean = false,
        var useHotswapAgent: Boolean = false,
        var hotswapAgentPath: String? = null,
        var jbrHome: String? = null,
        var sessionAuth: Boolean = true,
    )

    fun commandLine(spec: Spec, execOps: ExecOperations, logger: Logger): List<String> {
        val versionCache = ServerDownloader.versionCacheDir(spec.cacheDir, spec.serverVersion)
        val serverJar = File(versionCache, "HytaleServer.jar")
        val assets = File(versionCache, "Assets.zip")

        val javaExec = javaExecutable(spec.jbrHome)
        val isDcevm = if (spec.requireDcevm) checkDcevm(javaExec, execOps, logger) else false
        val agentJarPath = when {
            spec.useHotswapAgent && spec.hotswapAgentPath != null -> spec.hotswapAgentPath
            spec.useHotswapAgent -> HotswapAgentResolver.resolve(spec.gradleUserHomeDir, logger)
            else -> null
        }

        return buildList {
            add(javaExec)
            addAll(spec.jvmArgs)
            if (spec.debugEnabled) {
                require(spec.debugPort in 1..65535) { "debugPort must be in range 1..65535, got ${spec.debugPort}" }
                val suspend = if (spec.debugSuspend) "y" else "n"
                add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=127.0.0.1:${spec.debugPort}")
                logger.lifecycle("Debug listening on port ${spec.debugPort} (suspend=$suspend)")
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
            addAll(sessionArgs(spec.sessionAuth, logger))
        }
    }

    private fun javaExecutable(jbrHome: String?): String {
        val binName = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
        return if (jbrHome != null) {
            File(File(jbrHome, "bin"), binName).absolutePath.also { path ->
                require(File(path).isFile) {
                    "jbrHome '$jbrHome' does not contain a java executable at '$path'"
                }
            }
        } else {
            org.gradle.internal.jvm.Jvm.current().javaExecutable.absolutePath
        }
    }

    private fun checkDcevm(javaExec: String, execOps: ExecOperations, logger: Logger): Boolean {
        val out = java.io.ByteArrayOutputStream()
        val result = execOps.exec {
            commandLine(javaExec, "-XX:+AllowEnhancedClassRedefinition", "-version")
            standardOutput = out
            errorOutput = out
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            val versionOutput = out.toString(Charsets.UTF_8)
            throw GradleException(
                "requireDcevm is enabled but the JVM at '$javaExec' does not support -XX:+AllowEnhancedClassRedefinition (DCEVM/JBR required).\n" +
                "JVM output: ${versionOutput.lines().take(3).joinToString("\n")}\n" +
                "Set server { jbrHome = \"/path/to/jbr\" } to point to a JetBrains Runtime with DCEVM."
            )
        }
        val version = out.toString(Charsets.UTF_8).lines().firstOrNull()?.trim() ?: "unknown"
        logger.lifecycle("DCEVM capability confirmed: $version")
        return true
    }

    /**
     * Exchanges the cached `auth:server` token for a game session and appends
     * --session-token / --identity-token so the server starts fully
     * authenticated (no in-server /auth needed). Fail-soft: if session
     * creation fails, the server still starts and can be authed manually.
     */
    private fun sessionArgs(sessionAuth: Boolean, logger: Logger): List<String> {
        if (!sessionAuth) return emptyList()
        return try {
            val token = HytaleAuth.getAccessToken(logger, HytaleAuth.SERVER)
            val session = GameSession.create(token, logger)
            listOf("--session-token", session.sessionToken, "--identity-token", session.identityToken)
        } catch (e: Exception) {
            logger.warn("Session auth failed: ${e.message}")
            logger.warn("Starting server WITHOUT session tokens — run /auth in the server console if it requires authentication.")
            emptyList()
        }
    }
}