package gg.ginco.hygradle.internal

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.slf4j.Marker
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerDownloaderTest {

    private lateinit var tempDir: File
    private lateinit var cacheDir: File
    private lateinit var targetDir: File
    private val logger = NoopLogger

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "hygradle-test-${System.nanoTime()}")
        cacheDir = File(tempDir, "cache")
        targetDir = File(tempDir, "run")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `cache hit — returns cache dir without downloading`() {
        val versionCache = File(cacheDir, "1.0.0")
        versionCache.mkdirs()

        File(versionCache, "HytaleServer.jar").writeText("fake-jar")
        File(versionCache, "HytaleServer.aot.config").writeText("fake-aot")
        File(versionCache, "Assets.zip").writeText("fake-assets")
        File(versionCache, ".hygradle-version").writeText("1.0.0")

        targetDir.mkdirs()
        File(targetDir, ".hygradle-version").writeText("1.0.0")

        val result = ServerDownloader.ensureServerFiles("1.0.0", targetDir, cacheDir, logger)

        assertEquals(versionCache.absolutePath, result.absolutePath)
        assertTrue(File(targetDir, ".hygradle-version").isFile)
        assertEquals("1.0.0", File(targetDir, ".hygradle-version").readText().trim())
    }

    @Test
    fun `cache hit — target marker missing, writes it and returns cache dir`() {
        val versionCache = File(cacheDir, "2.0.0")
        versionCache.mkdirs()

        File(versionCache, "HytaleServer.jar").writeText("fake-jar")
        File(versionCache, "HytaleServer.aot.config").writeText("fake-aot")
        File(versionCache, "Assets.zip").writeText("fake-assets")
        File(versionCache, ".hygradle-version").writeText("2.0.0")

        targetDir.mkdirs()

        val result = ServerDownloader.ensureServerFiles("2.0.0", targetDir, cacheDir, logger)

        assertEquals(versionCache.absolutePath, result.absolutePath)
        assertTrue(File(targetDir, ".hygradle-version").isFile)
        assertEquals("2.0.0", File(targetDir, ".hygradle-version").readText().trim())
    }

    @Test
    fun `cache hit — target marker has stale version, updates it`() {
        val versionCache = File(cacheDir, "1.0.0")
        versionCache.mkdirs()

        File(versionCache, "HytaleServer.jar").writeText("fake-jar")
        File(versionCache, "HytaleServer.aot.config").writeText("fake-aot")
        File(versionCache, "Assets.zip").writeText("fake-assets")
        File(versionCache, ".hygradle-version").writeText("1.0.0")

        targetDir.mkdirs()
        File(targetDir, ".hygradle-version").writeText("0.9.0")

        val result = ServerDownloader.ensureServerFiles("1.0.0", targetDir, cacheDir, logger)

        assertEquals(versionCache.absolutePath, result.absolutePath)
        assertEquals("1.0.0", File(targetDir, ".hygradle-version").readText().trim())
    }

    @Test
    fun `different versions share same cache parent but separate version dirs`() {
        val v1Cache = File(cacheDir, "1.0.0")
        val v2Cache = File(cacheDir, "2.0.0")

        v1Cache.mkdirs()
        File(v1Cache, "HytaleServer.jar").writeText("v1-jar")
        File(v1Cache, "HytaleServer.aot.config").writeText("v1-aot")
        File(v1Cache, "Assets.zip").writeText("v1-assets")
        File(v1Cache, ".hygradle-version").writeText("1.0.0")

        v2Cache.mkdirs()
        File(v2Cache, "HytaleServer.jar").writeText("v2-jar")
        File(v2Cache, "HytaleServer.aot.config").writeText("v2-aot")
        File(v2Cache, "Assets.zip").writeText("v2-assets")
        File(v2Cache, ".hygradle-version").writeText("2.0.0")

        val targetV1 = File(tempDir, "project-a")
        targetV1.mkdirs()
        File(targetV1, ".hygradle-version").writeText("1.0.0")

        val r1 = ServerDownloader.ensureServerFiles("1.0.0", targetV1, cacheDir, logger)
        assertEquals(v1Cache.absolutePath, r1.absolutePath)

        val targetV2 = File(tempDir, "project-b")
        targetV2.mkdirs()

        val r2 = ServerDownloader.ensureServerFiles("2.0.0", targetV2, cacheDir, logger)
        assertEquals(v2Cache.absolutePath, r2.absolutePath)

        assertTrue(v1Cache.isDirectory)
        assertTrue(v2Cache.isDirectory)
        assertEquals("v1-jar", File(v1Cache, "HytaleServer.jar").readText())
        assertEquals("v2-jar", File(v2Cache, "HytaleServer.jar").readText())
    }
}

/** Logger that discards everything — keeps tests silent. */
private object NoopLogger : Logger {
    override fun getName(): String = "noop"
    override fun isTraceEnabled(): Boolean = false
    override fun isTraceEnabled(marker: Marker?): Boolean = false
    override fun isDebugEnabled(): Boolean = false
    override fun isDebugEnabled(marker: Marker?): Boolean = false
    override fun isInfoEnabled(): Boolean = false
    override fun isInfoEnabled(marker: Marker?): Boolean = false
    override fun isWarnEnabled(): Boolean = false
    override fun isWarnEnabled(marker: Marker?): Boolean = false
    override fun isErrorEnabled(): Boolean = false
    override fun isErrorEnabled(marker: Marker?): Boolean = false
    override fun isLifecycleEnabled(): Boolean = false
    override fun isQuietEnabled(): Boolean = false
    override fun isEnabled(level: LogLevel?): Boolean = false

    override fun trace(message: String) {}
    override fun trace(message: String, throwable: Throwable?) {}
    override fun trace(message: String, vararg objects: Any?) {}
    override fun trace(message: String, o1: Any?) {}
    override fun trace(message: String, o1: Any?, o2: Any?) {}
    override fun trace(marker: Marker?, message: String) {}
    override fun trace(marker: Marker?, message: String, throwable: Throwable?) {}
    override fun trace(marker: Marker?, message: String, vararg objects: Any?) {}
    override fun trace(marker: Marker?, message: String, o1: Any?) {}
    override fun trace(marker: Marker?, message: String, o1: Any?, o2: Any?) {}

    override fun debug(message: String) {}
    override fun debug(message: String, throwable: Throwable?) {}
    override fun debug(message: String, vararg objects: Any?) {}
    override fun debug(message: String, o1: Any?) {}
    override fun debug(message: String, o1: Any?, o2: Any?) {}
    override fun debug(marker: Marker?, message: String) {}
    override fun debug(marker: Marker?, message: String, throwable: Throwable?) {}
    override fun debug(marker: Marker?, message: String, vararg objects: Any?) {}
    override fun debug(marker: Marker?, message: String, o1: Any?) {}
    override fun debug(marker: Marker?, message: String, o1: Any?, o2: Any?) {}

    override fun info(message: String) {}
    override fun info(message: String, throwable: Throwable?) {}
    override fun info(message: String, vararg objects: Any?) {}
    override fun info(message: String, o1: Any?) {}
    override fun info(message: String, o1: Any?, o2: Any?) {}
    override fun info(marker: Marker?, message: String) {}
    override fun info(marker: Marker?, message: String, throwable: Throwable?) {}
    override fun info(marker: Marker?, message: String, vararg objects: Any?) {}
    override fun info(marker: Marker?, message: String, o1: Any?) {}
    override fun info(marker: Marker?, message: String, o1: Any?, o2: Any?) {}

    override fun warn(message: String) {}
    override fun warn(message: String, throwable: Throwable?) {}
    override fun warn(message: String, vararg objects: Any?) {}
    override fun warn(message: String, o1: Any?) {}
    override fun warn(message: String, o1: Any?, o2: Any?) {}
    override fun warn(marker: Marker?, message: String) {}
    override fun warn(marker: Marker?, message: String, throwable: Throwable?) {}
    override fun warn(marker: Marker?, message: String, vararg objects: Any?) {}
    override fun warn(marker: Marker?, message: String, o1: Any?) {}
    override fun warn(marker: Marker?, message: String, o1: Any?, o2: Any?) {}

    override fun error(message: String) {}
    override fun error(message: String, throwable: Throwable?) {}
    override fun error(message: String, vararg objects: Any?) {}
    override fun error(message: String, o1: Any?) {}
    override fun error(message: String, o1: Any?, o2: Any?) {}
    override fun error(marker: Marker?, message: String) {}
    override fun error(marker: Marker?, message: String, throwable: Throwable?) {}
    override fun error(marker: Marker?, message: String, vararg objects: Any?) {}
    override fun error(marker: Marker?, message: String, o1: Any?) {}
    override fun error(marker: Marker?, message: String, o1: Any?, o2: Any?) {}

    override fun lifecycle(message: String) {}
    override fun lifecycle(message: String, throwable: Throwable?) {}
    override fun lifecycle(message: String, vararg objects: Any?) {}
    override fun quiet(message: String) {}
    override fun quiet(message: String, throwable: Throwable?) {}
    override fun quiet(message: String, vararg objects: Any?) {}

    override fun log(level: LogLevel?, message: String) {}
    override fun log(level: LogLevel?, message: String, throwable: Throwable?) {}
    override fun log(level: LogLevel?, message: String, vararg objects: Any?) {}
}
