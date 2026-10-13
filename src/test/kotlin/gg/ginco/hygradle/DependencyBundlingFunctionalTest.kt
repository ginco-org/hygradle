package gg.ginco.hygradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end test for issue #4: `hytale { bundleDependencies = true }` must be able to
 * exclude shared-framework dependencies from the plugin jar.
 *
 * Builds a real consumer project (TestKit, no network — all dependencies come from a
 * fixture maven repo) against a fake "shared framework" setup:
 *
 *  - `gg.ginco:hytale-minigame-framework` — the shared framework thin jar living in `mods/`
 *  - `gg.ginco:hytale-codec-json` — a framework-provided library
 *  - `org.jetbrains.kotlin:kotlin-stdlib` — a dependency the consumer itself needs bundled
 */
class DependencyBundlingFunctionalTest {

    private lateinit var projectDir: File
    private lateinit var repoDir: File

    @BeforeTest
    fun setUp() {
        val temp = File(System.getProperty("java.io.tmpdir"), "hygradle-bundling-${System.nanoTime()}")
        projectDir = File(temp, "consumer").apply { mkdirs() }
        repoDir = File(temp, "repo")
        File(projectDir, "settings.gradle.kts").writeText("""rootProject.name = "consumer"""")

        publishFixture("gg.ginco", "hytale-minigame-framework", "1.0.0", "gg/ginco/framework/SharedFramework.class")
        publishFixture("gg.ginco", "hytale-codec-json", "0.2.0", "gg/ginco/codec/CodecJson.class")
        publishFixture("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21", "kotlin/Unit.class")
    }

    @AfterTest
    fun tearDown() {
        projectDir.parentFile.deleteRecursively()
    }

    @Test
    fun `bundleDependencies bundles everything when no filter is given`() {
        writeBuildScript(exclusions = null)

        val jar = buildAndOpenJar()

        assertTrue("manifest.json" in jar, "manifest.json must be in the plugin jar")
        assertTrue("gg/ginco/framework/SharedFramework.class" in jar)
        assertTrue("gg/ginco/codec/CodecJson.class" in jar)
        assertTrue("kotlin/Unit.class" in jar)
    }

    @Test
    fun `bundleExclude keeps shared framework and codec out of the consumer jar`() {
        // The exact scenario from issue #4.
        writeBuildScript(
            exclusions = """
                bundleExclude("gg.ginco:hytale-minigame-framework")
                bundleExclude("gg.ginco:hytale-codec-.*")
            """
        )

        val jar = buildAndOpenJar()

        assertTrue("manifest.json" in jar)
        assertTrue("kotlin/Unit.class" in jar, "kotlin-stdlib (a first-party need) must stay bundled")
        assertTrue("gg/ginco/framework/SharedFramework.class" !in jar,
            "the shared framework thin jar must not be bundled — its classes live in mods/ once and are " +
                "resolved through PluginBridgeClassLoader; bundling them shadows the framework's class " +
                "loader (child-first) and duplicates static state")
        assertTrue("gg/ginco/codec/CodecJson.class" !in jar, "regex exclusion must keep codec artifacts out")
    }

    @Test
    fun `bundleOnly bundles just the positive list`() {
        writeBuildScript(
            exclusions = """
                bundleOnly("org.jetbrains.kotlin:kotlin-stdlib")
            """
        )

        val jar = buildAndOpenJar()

        assertTrue("kotlin/Unit.class" in jar)
        assertTrue("gg/ginco/framework/SharedFramework.class" !in jar)
        assertTrue("gg/ginco/codec/CodecJson.class" !in jar)
    }

    @Test
    fun `invalid bundleExclude pattern fails the build at configuration time`() {
        writeBuildScript(
            exclusions = """
                bundleExclude("[unclosed")
            """
        )

        val result = gradleRunner().buildAndFail()
        assertTrue(
            result.output.contains("Invalid bundle dependency pattern '[unclosed'"),
            "expected a clear configuration-time failure, got:\n${result.output}"
        )
    }

    // -- fixtures ---------------------------------------------------------------------------

    private fun writeBuildScript(exclusions: String?) {
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("gg.ginco.hygradle")
            }

            version = "1.0.0"

            java {
                // Hermetic: pin the consumer toolchain to the JVM running this test build —
                // the sandbox may have no auto-provisionable JDK 25 for the plugin's default.
                toolchain { languageVersion.set(JavaLanguageVersion.of(Runtime.version().feature())) }
            }

            repositories {
                // Test isolation: drop the remote repos the plugin adds; the fixture repo is enough.
                removeAll { true }
                maven { url = uri("${repoDir.toURI()}") }
            }

            dependencies {
                implementation("gg.ginco:hytale-minigame-framework:1.0.0")
                implementation("gg.ginco:hytale-codec-json:0.2.0")
                implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
            }

            hytale {
                group = "Ginco"
                name = "Consumer"
                mainClass = "com.example.consumer.ConsumerPlugin"
                serverVersion = "0.6.8"
                bundleDependencies = true
                ${exclusions ?: ""}
            }
            """.trimIndent()
        )
    }

    private fun gradleRunner(): GradleRunner =
        GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments("jar", "--console=plain")
            // Forked (not withDebug): the in-process runner executes the consumer build with
            // this repo as its working directory and litters it with extracted classpath
            // resources.

    private fun buildAndOpenJar(): Set<String> {
        val result = gradleRunner().build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome, result.output)

        val jarFile = File(projectDir, "build/libs").listFiles { f -> f.name.endsWith(".jar") }!!.single()
        return ZipFile(jarFile).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
    }

    /** Publishes a minimal maven module (pom + jar containing [entry]) into the fixture repo. */
    private fun publishFixture(group: String, artifact: String, version: String, entry: String) {
        val moduleDir = File(repoDir, "${group.replace('.', '/')}/$artifact/$version")
        moduleDir.mkdirs()

        File(moduleDir, "$artifact-$version.pom").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId>
              <artifactId>$artifact</artifactId>
              <version>$version</version>
              <packaging>jar</packaging>
            </project>
            """.trimIndent()
        )

        File(moduleDir, "$artifact-$version.jar").outputStream().use { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry(entry))
                zip.write(ByteArray(4))
                zip.closeEntry()
            }
        }
    }
}
