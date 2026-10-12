package gg.ginco.hygradle.internal

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the `group:artifact` pattern matching behind
 * `hytale { bundleExclude(...) }` / `hytale { bundleOnly(...) }` (issue #4).
 */
class DependencyBundleFilterTest {

    private fun module(group: String, artifact: String, version: String): ComponentIdentifier =
        object : ModuleComponentIdentifier {
            override fun getGroup() = group
            override fun getModule() = artifact
            override fun getVersion() = version
            override fun getModuleIdentifier() = throw UnsupportedOperationException()
            override fun getDisplayName() = "$group:$artifact:$version"
        }

    private fun projectComponent(path: String): ComponentIdentifier =
        object : ProjectComponentIdentifier {
            override fun getProjectPath() = path
            override fun getBuildTreePath() = ":"
            override fun getProjectName() = path.removePrefix(":")
            override fun getBuild() = throw UnsupportedOperationException()
            override fun getDisplayName() = path
        }

    private fun frameworkJar() = File("/cache/gg/ginco/hytale-minigame-framework/1.0.0/hytale-minigame-framework-1.0.0.jar")

    // -- default: everything bundles ---------------------------------------------------------

    @Test
    fun `empty filter bundles everything`() {
        val filter = DependencyBundleFilter()
        assertTrue(filter.shouldBundle(module("gg.ginco", "hytale-minigame-framework", "1.0.0"), frameworkJar()))
        assertTrue(filter.shouldBundle(module("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"), File("/cache/kotlin-stdlib-2.3.21.jar")))
    }

    // -- excludes ------------------------------------------------------------------------

    @Test
    fun `exact group-artifact is excluded`() {
        val filter = DependencyBundleFilter(excludes = listOf("gg.ginco:hytale-minigame-framework"))
        assertFalse(filter.shouldBundle(module("gg.ginco", "hytale-minigame-framework", "1.0.0"), frameworkJar()))
        assertTrue(filter.shouldBundle(module("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"), File("/cache/kotlin-stdlib-2.3.21.jar")))
    }

    @Test
    fun `regex exclusion matches whole coordinates`() {
        // The exact issue #4 example: one shared framework plus any hytale-codec-* artifact stay out.
        val filter = DependencyBundleFilter(
            excludes = listOf("gg.ginco:hytale-minigame-framework", "gg.ginco:hytale-codec-.*")
        )
        assertFalse(filter.shouldBundle(module("gg.ginco", "hytale-minigame-framework", "1.0.0"), frameworkJar()))
        assertFalse(filter.shouldBundle(module("gg.ginco", "hytale-codec-json", "0.2.0"), File("/cache/hytale-codec-json-0.2.0.jar")))
        assertFalse(filter.shouldBundle(module("gg.ginco", "hytale-codec-nbt", "0.2.0"), File("/cache/hytale-codec-nbt-0.2.0.jar")))
        assertTrue(filter.shouldBundle(module("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"), File("/cache/kotlin-stdlib-2.3.21.jar")))
    }

    @Test
    fun `bare artifact name pattern excludes without group`() {
        val filter = DependencyBundleFilter(excludes = listOf("hytale-codec-.*"))
        assertFalse(filter.shouldBundle(module("gg.ginco", "hytale-codec-json", "0.2.0"), File("/cache/hytale-codec-json-0.2.0.jar")))
        assertTrue(filter.shouldBundle(module("gg.ginco", "hytale-minigame-framework", "1.0.0"), frameworkJar()))
    }

    @Test
    fun `group-artifact-version pattern matches specific version only`() {
        val filter = DependencyBundleFilter(excludes = listOf("gg.ginco:hytale-minigame-framework:1.0.0"))
        assertFalse(filter.shouldBundle(module("gg.ginco", "hytale-minigame-framework", "1.0.0"), frameworkJar()))
        assertTrue(filter.shouldBundle(module("gg.ginco", "hytale-minigame-framework", "1.1.0"),
            File("/cache/hytale-minigame-framework-1.1.0.jar")))
    }

    @Test
    fun `patterns are full-match not substring`() {
        val filter = DependencyBundleFilter(excludes = listOf("framework"))
        // "framework" alone must not swallow gg.ginco:hytale-minigame-framework
        assertTrue(filter.shouldBundle(module("gg.ginco", "hytale-minigame-framework", "1.0.0"), frameworkJar()))
        assertFalse(filter.shouldBundle(module("example", "framework", "1.0"), File("/cache/framework-1.0.jar")))
    }

    // -- includes (positive list) -----------------------------------------------------------

    @Test
    fun `non-empty include list bundles only matches`() {
        val filter = DependencyBundleFilter(includes = listOf("org.jetbrains.kotlin:kotlin-stdlib"))
        assertFalse(filter.shouldBundle(module("gg.ginco", "hytale-minigame-framework", "1.0.0"), frameworkJar()))
        assertFalse(filter.shouldBundle(module("gg.ginco", "hytale-codec-json", "0.2.0"), File("/cache/hytale-codec-json-0.2.0.jar")))
        assertTrue(filter.shouldBundle(module("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"), File("/cache/kotlin-stdlib-2.3.21.jar")))
    }

    @Test
    fun `exclude wins over include`() {
        val filter = DependencyBundleFilter(
            includes = listOf("org.jetbrains.kotlin:.*"),
            excludes = listOf(".*:kotlin-reflect"),
        )
        assertTrue(filter.shouldBundle(module("org.jetbrains.kotlin", "kotlin-stdlib", "2.3.21"), File("/cache/kotlin-stdlib-2.3.21.jar")))
        assertFalse(filter.shouldBundle(module("org.jetbrains.kotlin", "kotlin-reflect", "2.3.21"), File("/cache/kotlin-reflect-2.3.21.jar")))
    }

    // -- non-module dependencies ----------------------------------------------------------

    @Test
    fun `project dependency matched by project path`() {
        val filter = DependencyBundleFilter(excludes = listOf(":shared-framework"))
        assertFalse(filter.shouldBundle(projectComponent(":shared-framework"), File("/cache/shared-framework-1.0.0.jar")))
        assertTrue(filter.shouldBundle(projectComponent(":other-mod"), File("/cache/other-mod-1.0.0.jar")))
    }

    @Test
    fun `plain file dependency matched by file name`() {
        val filter = DependencyBundleFilter(excludes = listOf("some-lib-.*"))
        assertFalse(filter.shouldBundle(null, File("/libs/some-lib-1.2.3.jar")))
        assertTrue(filter.shouldBundle(null, File("/libs/other-lib-1.0.jar")))
    }

    // -- pattern validation ----------------------------------------------------------------

    @Test
    fun `validate rejects invalid regex with a helpful message`() {
        val e = assertFailsWith<IllegalArgumentException> { DependencyBundleFilter.validate("[unclosed") }
        assertTrue("Invalid bundle dependency pattern '[unclosed'" in (e.message ?: ""), e.message)
    }

    @Test
    fun `validate rejects blank patterns`() {
        assertFailsWith<IllegalArgumentException> { DependencyBundleFilter.validate("") }
        assertFailsWith<IllegalArgumentException> { DependencyBundleFilter.validate("   ") }
    }

    @Test
    fun `validate trims surrounding whitespace`() {
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib", DependencyBundleFilter.validate("  org.jetbrains.kotlin:kotlin-stdlib  "))
    }
}
