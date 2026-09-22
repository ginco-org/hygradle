package gg.ginco.hygradle.internal

import gg.ginco.hygradle.tasks.ProvisionServerTask
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task-level tests for [ProvisionServerTask]. The server cache is pre-seeded
 * the same way [ServerDownloader] leaves it after a download, so no network
 * access is needed and the cache-hit (no-op) path is what gets exercised.
 */
class ProvisionServerTaskTest {

    private lateinit var tempDir: File
    private lateinit var cacheDir: File
    private lateinit var runDir: File
    private lateinit var task: ProvisionServerTask

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "hygradle-test-${System.nanoTime()}")
        tempDir.mkdirs()

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        task = project.tasks.register("prov", ProvisionServerTask::class.java).get()
        task.serverVersion.set("1.0.0")
        task.gradleUserHomeDir.set(project.layout.projectDirectory.dir("gradle-home"))
        task.serverDir.set(project.layout.projectDirectory.dir("run"))

        cacheDir = File(task.gradleUserHomeDir.get().asFile, "caches/hygradle/server")
        runDir = task.serverDir.get().asFile
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun seedVersionCache(version: String) {
        val versionCache = File(cacheDir, version)
        versionCache.mkdirs()
        File(versionCache, "HytaleServer.jar").writeText("fake-jar")
        File(versionCache, "HytaleServer.aot.config").writeText("fake-aot")
        File(versionCache, "Assets.zip").writeText("fake-assets")
        File(versionCache, ".hygradle-version").writeText(version)
    }

    @Test
    fun `writes version marker into run dir when server files are cached`() {
        seedVersionCache("1.0.0")

        task.run()

        assertTrue(File(runDir, ".hygradle-version").isFile)
        assertEquals("1.0.0", File(runDir, ".hygradle-version").readText().trim())
    }

    @Test
    fun `second invocation is a no-op — no download, files untouched`() {
        seedVersionCache("1.0.0")

        task.run()

        val markerBefore = File(runDir, ".hygradle-version").readText()
        val jarBefore = File(cacheDir, "1.0.0/HytaleServer.jar").readText()
        val lastModified = File(cacheDir, "1.0.0/HytaleServer.jar").lastModified()

        task.run()

        // Marker and cached files are byte-identical and not re-downloaded
        assertEquals(markerBefore, File(runDir, ".hygradle-version").readText())
        assertEquals(jarBefore, File(cacheDir, "1.0.0/HytaleServer.jar").readText())
        assertEquals(lastModified, File(cacheDir, "1.0.0/HytaleServer.jar").lastModified())
        assertEquals("fake-jar", File(cacheDir, "1.0.0/HytaleServer.jar").readText())
    }

    @Test
    fun `stale run-dir marker is refreshed to the requested version`() {
        seedVersionCache("1.0.0")
        runDir.mkdirs()
        File(runDir, ".hygradle-version").writeText("0.9.0")

        task.run()

        assertEquals("1.0.0", File(runDir, ".hygradle-version").readText().trim())
    }
}
