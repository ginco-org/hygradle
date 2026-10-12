package gg.ginco.hygradle

import com.google.gson.JsonParser
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * End-to-end test for issue #5: `hytale { subPlugin { ... } }` must stamp `ServerVersion`
 * on every SubPlugins manifest entry — the server's sub-plugin loader inherits
 * Group/Version from the parent plugin but NOT ServerVersion, so entries without it log
 * "Plugin 'X' does not specify a target server version. ... hard error in the future".
 *
 * Builds a real consumer project with TestKit (no network — the manifest task has no
 * dependencies to resolve) and inspects the generated manifest.json.
 */
class SubPluginManifestFunctionalTest {

    private lateinit var projectDir: File

    @BeforeTest
    fun setUp() {
        val temp = File(System.getProperty("java.io.tmpdir"), "hygradle-subplugin-${System.nanoTime()}")
        projectDir = File(temp, "consumer").apply { mkdirs() }
        File(projectDir, "settings.gradle.kts").writeText("""rootProject.name = "consumer"""")
    }

    @AfterTest
    fun tearDown() {
        projectDir.parentFile.deleteRecursively()
    }

    @Test
    fun `sub-plugin entries carry ServerVersion`() {
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("gg.ginco.hygradle")
            }

            repositories { removeAll { true } }  // Test isolation: no network

            hytale {
                group = "Ginco"
                mainClass = "com.example.consumer.ConsumerPlugin"
                serverVersion = "0.6.8"
                subPlugin {
                    name = "MinigameFramework"
                    mainClass = "gg.ginco.minigame.Plugin"
                }
                subPlugin {
                    name = "Other"
                    mainClass = "gg.ginco.other.Plugin"
                    serverVersion = "0.7.0"
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments("generatePluginManifest", "--console=plain")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePluginManifest")?.outcome, result.output)

        val manifest = JsonParser.parseString(
            File(projectDir, "build/generated/hytale/manifest.json").readText()
        ).asJsonObject

        assertEquals("=0.6.8", manifest.get("ServerVersion").asString, "parent manifest keeps its ServerVersion")

        val subs = manifest.getAsJsonArray("SubPlugins").map { it.asJsonObject }
        assertEquals(2, subs.size)
        assertEquals("=0.6.8", subs[0].get("ServerVersion").asString,
            "sub-plugin without an explicit version must inherit the parent's serverVersion (issue #5)")
        assertEquals("=0.7.0", subs[1].get("ServerVersion").asString,
            "explicit sub-plugin serverVersion must win over the parent's")
        assertNull(subs[0].get("Description"), "nothing else about the sub-plugin entry should change")
    }
}
