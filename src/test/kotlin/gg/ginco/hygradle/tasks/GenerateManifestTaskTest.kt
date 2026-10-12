package gg.ginco.hygradle.tasks

import com.google.gson.JsonParser
import gg.ginco.hygradle.SubPluginSpec
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Manifest-level tests for [GenerateManifestTask]'s sub-plugin entries, focused on
 * issue #5: the server's sub-plugin loader (`PendingLoadJavaPlugin`) inherits
 * Group/Version from the parent plugin but NOT ServerVersion, so every sub-plugin
 * logged "Plugin 'X' does not specify a target server version. ... This will be a
 * hard error in the future" unless the SubPlugins entry carries ServerVersion itself.
 */
class GenerateManifestTaskTest {

    private lateinit var tempDir: File
    private lateinit var task: GenerateManifestTask

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "hygradle-manifest-${System.nanoTime()}")
        tempDir.mkdirs()

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        task = project.tasks.register("gen", GenerateManifestTask::class.java).get()
        task.pluginGroup.set("Ginco")
        task.pluginName.set("Host")
        task.pluginVersion.set("1.0.0")
        task.serverVersion.set("0.6.8")
        task.authors.set(emptyList())
        task.dependencies.set(emptyMap())
        task.optionalDependencies.set(emptyMap())
        task.loadBefore.set(emptyMap())
        task.includesAssetPack.set(false)
        task.disabledByDefault.set(false)
        task.outputDir.set(project.layout.projectDirectory.dir("generated"))
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun subPlugin(configure: SubPluginSpec.() -> Unit): SubPluginSpec =
        SubPluginSpec().apply {
            name = "SubModule"
            mainClass = "com.example.SubModule"
            apply(configure)
        }

    /** Runs the task and returns the `SubPlugins` array, or null if the manifest has none. */
    private fun generateSubPlugins(subPlugins: List<SubPluginSpec>): List<Map<String, Any?>>? {
        task.subPlugins.set(subPlugins)
        task.generate()
        val manifest = File(task.outputDir.get().asFile, "manifest.json").readText()
        val root = JsonParser.parseString(manifest).asJsonObject
        return if (root.has("SubPlugins")) {
            root.getAsJsonArray("SubPlugins").map { entry ->
                entry.asJsonObject.entrySet().associate { (k, v) -> k to v.takeIf { !it.isJsonNull }?.toString()?.trim('"') }
            }
        } else null
    }

    @Test
    fun `sub-plugin inherits parent serverVersion when none is given`() {
        // The fix for issue #5: existing subPlugin blocks have no serverVersion field, so
        // the parent's value must be stamped on the entry to silence the server warning.
        val subs = generateSubPlugins(listOf(subPlugin { }))

        assertEquals("=0.6.8", subs!!.single()["ServerVersion"])
    }

    @Test
    fun `explicit sub-plugin serverVersion wins over the parent's`() {
        val subs = generateSubPlugins(listOf(subPlugin { serverVersion = "0.7.0" }))

        assertEquals("=0.7.0", subs!!.single()["ServerVersion"])
    }

    @Test
    fun `sub-plugin serverVersion range operators pass through`() {
        val subs = generateSubPlugins(listOf(subPlugin { serverVersion = ">=0.6.0" }))

        assertEquals(">=0.6.0", subs!!.single()["ServerVersion"])
    }

    @Test
    fun `no serverVersion anywhere omits the field instead of writing empty`() {
        task.serverVersion.set("")
        val subs = generateSubPlugins(listOf(subPlugin { serverVersion = " " }))

        assertNull(subs!!.single()["ServerVersion"], "expected no ServerVersion key, got: ${subs.single()}")
    }

    @Test
    fun `blank explicit serverVersion falls back to the parent's`() {
        val subs = generateSubPlugins(listOf(subPlugin { serverVersion = " " }))

        assertEquals("=0.6.8", subs!!.single()["ServerVersion"])
    }

    @Test
    fun `each sub-plugin entry is stamped independently`() {
        val subs = generateSubPlugins(
            listOf(
                subPlugin { name = "A" },
                subPlugin { name = "B"; serverVersion = "0.9.0" },
            )
        )

        assertEquals("=0.6.8", subs!![0]["ServerVersion"])
        assertEquals("=0.9.0", subs[1]["ServerVersion"])
    }

    @Test
    fun `sub-plugin entry keeps its other fields`() {
        val subs = generateSubPlugins(
            listOf(
                subPlugin {
                    description = "a sub"
                    author("jane doe")
                    depends("Other:Plugin", "^1.0.0")
                }
            )
        )!!

        val entry = subs.single()
        assertEquals("SubModule", entry["Name"])
        assertEquals("com.example.SubModule", entry["Main"])
        assertEquals("a sub", entry["Description"])
        assertFalse(entry.containsKey("Website"))
    }
}
