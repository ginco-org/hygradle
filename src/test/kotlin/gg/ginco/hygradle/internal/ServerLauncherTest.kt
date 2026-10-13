package gg.ginco.hygradle.internal

import org.gradle.api.GradleException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.process.ExecOperations
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [ServerLauncher.commandLine] — the shared launch-command assembly used by
 * RunServerTask and RunAllModsTask. `sessionAuth = false` in every spec: the session path
 * performs network calls (token cache + game session creation).
 */
class ServerLauncherTest {

    private lateinit var tempDir: File
    private lateinit var cacheDir: File
    private val project = ProjectBuilder.builder().build()
    private val execOps: ExecOperations =
        (project as ProjectInternal).services.get(ExecOperations::class.java)
    private val logger = project.logger

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "hygradle-launch-${System.nanoTime()}")
        cacheDir = File(tempDir, "gradle-home/caches/hygradle/server")
        val versionCache = File(cacheDir, "1.0.0")
        versionCache.mkdirs()
        File(versionCache, "HytaleServer.jar").writeText("fake-jar")
        File(versionCache, "Assets.zip").writeText("fake-assets")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun spec(configure: ServerLauncher.Spec.() -> Unit = {}): ServerLauncher.Spec =
        ServerLauncher.Spec(
            serverVersion = "1.0.0",
            cacheDir = cacheDir,
            gradleUserHomeDir = File(tempDir, "gradle-home"),
            jvmArgs = listOf("-Xmx1G"),
            sessionAuth = false,
        ).apply(configure)

    @Test
    fun `launch command — java jvm args jar and assets`() {
        val command = ServerLauncher.commandLine(spec(), execOps, logger)

        assertTrue(command.first().endsWith("java"))
        assertEquals(listOf("-Xmx1G", "-jar"), command.subList(1, 3))
        val jarIndex = command.indexOf("-jar")
        assertEquals(File(cacheDir, "1.0.0/HytaleServer.jar").absolutePath, command[jarIndex + 1])
        assertEquals(
            listOf("--assets", File(cacheDir, "1.0.0/Assets.zip").absolutePath),
            command.takeLast(2),
        )
    }

    @Test
    fun `launch command — assets arg omitted when Assets zip is missing`() {
        File(cacheDir, "1.0.0/Assets.zip").delete()

        val command = ServerLauncher.commandLine(spec(), execOps, logger)

        assertFalse("--assets" in command)
    }

    @Test
    fun `launch command — debug agent args when enabled`() {
        val command = ServerLauncher.commandLine(
            spec { debugEnabled = true; debugPort = 5005; debugSuspend = true }, execOps, logger)

        assertTrue("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5005" in command)
    }

    @Test
    fun `launch command — out-of-range debug port fails`() {
        assertFailsWith<IllegalArgumentException> {
            ServerLauncher.commandLine(spec { debugEnabled = true; debugPort = 0 }, execOps, logger)
        }
    }

    @Test
    fun `launch command — explicit hotswap agent path is used without downloading`() {
        val agent = File(tempDir, "hotswap-agent.jar").apply { writeText("fake-agent") }

        val command = ServerLauncher.commandLine(
            spec { useHotswapAgent = true; hotswapAgentPath = agent.absolutePath }, execOps, logger)

        assertTrue("-javaagent:${agent.absolutePath}" in command)
        assertFalse(File(tempDir, "gradle-home/caches/hygradle/hotswap-agent-1.4.2.jar").isFile)
    }

    @Test
    fun `launch command — jbrHome without a java executable fails`() {
        assertFailsWith<IllegalArgumentException> {
            ServerLauncher.commandLine(spec { jbrHome = tempDir.absolutePath }, execOps, logger)
        }
    }

    @Test
    fun `launch command — requireDcevm fails on a JVM without enhanced class redefinition`() {
        // Stock OpenJDK builds (CI, this sandbox) reject the flag; only DCEVM-capable JVMs
        // (JBR) support it, so the negative case is the deterministic one to assert.
        assertFailsWith<GradleException> {
            ServerLauncher.commandLine(spec { requireDcevm = true }, execOps, logger)
        }
    }
}