package gg.ginco.hygradle.internal

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [ModsDeploy.deploy] rebuilds `mods/` from scratch — no stale jars survive. */
class ModsDeployTest {

    private lateinit var tempDir: File
    private lateinit var runDir: File
    private val logger = ProjectBuilder.builder().build().logger

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "hygradle-deploy-${System.nanoTime()}")
        runDir = File(tempDir, "run").apply { mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun jar(name: String): File = File(tempDir, name).apply { writeText("jar:$name") }

    @Test
    fun `deploy replaces the mods directory content — stale jars are removed`() {
        File(runDir, "mods").apply { mkdirs(); File(this, "old-plugin.jar").writeText("stale") }

        ModsDeploy.deploy(runDir, listOf(jar("new-plugin.jar")), logger)

        val mods = File(runDir, "mods")
        assertEquals(listOf("new-plugin.jar"), mods.listFiles()!!.map { it.name }.sorted())
    }

    @Test
    fun `deploy deploys multiple jars and cleans up the tmp dir`() {
        val jars = listOf(jar("a.jar"), jar("b.jar"))

        ModsDeploy.deploy(runDir, jars, logger)

        val mods = File(runDir, "mods")
        assertTrue(File(mods, "a.jar").isFile)
        assertTrue(File(mods, "b.jar").isFile)
        assertFalse(File(runDir, "mods.tmp").exists())
    }
}