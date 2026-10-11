package gg.ginco.hygradle

import gg.ginco.hygradle.tasks.GenerateManifestTask
import gg.ginco.hygradle.tasks.ProvisionServerTask
import gg.ginco.hygradle.tasks.RunServerTask
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Optional
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.lang.reflect.Method
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the wiring [HytalePlugin] performs between the project and its tasks.
 *
 * Task *logic* is covered by the other test classes; these tests apply the real
 * plugin and assert that every task property the plugin is responsible for
 * actually has a value. Regression test for issue #2, where `runServer`'s
 * `gradleUserHomeDir` was never set by the plugin, so `./gradlew runServer`
 * always failed with:
 *
 * `Cannot query the value of task ':runServer' property 'gradleUserHomeDir'
 * because it has no value available.`
 */
class HytalePluginWiringTest {

    private lateinit var tempDir: File
    private lateinit var gradleUserHome: File
    private lateinit var project: Project

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "hygradle-wiring-${System.nanoTime()}")
        tempDir.mkdirs()
        gradleUserHome = File(tempDir, "gradle-home").apply { mkdirs() }
        project = ProjectBuilder.builder()
            .withProjectDir(File(tempDir, "project").apply { mkdirs() })
            .withGradleUserHomeDir(gradleUserHome)
            .build()
        project.pluginManager.apply(HytalePlugin::class.java)

        val ext = project.extensions.getByType(HytaleExtension::class.java)
        ext.group.set("Ginco")
        ext.mainClass.set("com.example.Plugin")
        ext.serverVersion.set("0.6.8")
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `runServer gradleUserHomeDir is wired to the Gradle user home`() {
        val runServer = project.tasks.named("runServer", RunServerTask::class.java).get()
        assertTrue(runServer.gradleUserHomeDir.isPresent,
            "runServer.gradleUserHomeDir must be set by the plugin — otherwise the task fails at " +
                "execution with 'Cannot query the value of task ':runServer' property " +
                "'gradleUserHomeDir' because it has no value available' (issue #2)")
        assertEquals(
            gradleUserHome.absoluteFile,
            runServer.gradleUserHomeDir.get().asFile.absoluteFile
        )
    }

    @Test
    fun `provisionServer gradleUserHomeDir is wired to the Gradle user home`() {
        val provisionServer = project.tasks.named("provisionServer", ProvisionServerTask::class.java).get()
        assertTrue(provisionServer.gradleUserHomeDir.isPresent)
        assertEquals(
            gradleUserHome.absoluteFile,
            provisionServer.gradleUserHomeDir.get().asFile.absoluteFile
        )
    }

    @Test
    fun `every required property of plugin-registered tasks has a value`() {
        // Sweeps all Provider-valued properties (Property, ListProperty, DirectoryProperty,
        // RegularFileProperty, ...) of the tasks HytalePlugin registers and fails if any
        // non-@Optional one has no value. Catches any future "plugin forgot to wire X" bug
        // — the class of bug that shipped in 0.5.0 — without having to execute the tasks.
        val checks = listOf(
            "generatePluginManifest" to GenerateManifestTask::class.java,
            "provisionServer" to ProvisionServerTask::class.java,
            "runServer" to RunServerTask::class.java,
        )
        for ((taskName, taskClass) in checks) {
            val task = project.tasks.named(taskName, taskClass).get()
            for (getter in providerGetters(taskClass)) {
                val provider = getter.invoke(task) as Provider<*>
                assertTrue(provider.isPresent,
                    "Task '$taskName' property '${getter.name.removePrefix("get").replaceFirstChar { it.lowercase() }}' " +
                        "has no value — HytalePlugin must wire it (a missing value fails the task at execution time)")
            }
        }
    }

    /** Zero-arg [Provider]-valued getters declared by the task class itself (excludes Gradle
     *  built-ins like `Task.getTimeout()` which are legitimately unset by default), excluding [Optional] ones. */
    private fun providerGetters(taskClass: Class<*>): List<Method> =
        taskClass.methods
            .filter { it.declaringClass == taskClass }
            .filter { it.parameterCount == 0 }
            .filter { Provider::class.java.isAssignableFrom(it.returnType) }
            .filter { !it.isAnnotationPresent(Optional::class.java) }
}
