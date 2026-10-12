package gg.ginco.hygradle.internal

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.io.File
import java.util.regex.PatternSyntaxException

/**
 * Selects which `runtimeClasspath` artifacts are bundled into the plugin jar when
 * `hytale { bundleDependencies = true }` (issue #4: shared-framework packaging).
 *
 * A pattern is a **full-match regex** ([Regex.matches], not substring) compared against a
 * dependency's candidate names:
 *  - Maven modules: `group:artifact` (e.g. `gg.ginco:hytale-minigame-framework`),
 *    `group:artifact:version`, and the bare `artifact` name
 *  - Project dependencies: the project path (`:framework`) and the bare project name
 *  - Anything else (plain file dependencies): the file name without extension
 *
 * Rules:
 *  - [includes], when non-empty, is the positive list: only matching dependencies are bundled.
 *  - [excludes] always wins: matching dependencies are never bundled.
 *  - Both empty (the default) bundles every jar — the all-or-nothing behaviour from before.
 */
class DependencyBundleFilter(
    includes: List<String> = emptyList(),
    excludes: List<String> = emptyList(),
) {
    private val includes: List<Regex> = includes.map { it.toRegex() }
    private val excludes: List<Regex> = excludes.map { it.toRegex() }

    /** Returns `true` when the artifact identified by [identifier]/[file] should be bundled. */
    fun shouldBundle(identifier: ComponentIdentifier?, file: File): Boolean {
        val names = candidateNames(identifier, file)
        val included = this.includes.isEmpty() || names.any { name -> includes.any { it.matches(name) } }
        val excluded = excludes.isNotEmpty() && names.any { name -> excludes.any { it.matches(name) } }
        return included && !excluded
    }

    /**
     * The jar files of [artifacts] that pass [shouldBundle]. This is the artifact-level
     * equivalent of the previous `cfg.filter { it.name.endsWith(".jar") }` file filter —
     * but resolves `group:artifact` coordinates so patterns can match modules, not file names.
     */
    fun select(artifacts: ArtifactCollection): List<File> =
        artifacts.artifacts
            .filter { it.file.name.endsWith(".jar") && shouldBundle(it.id.componentIdentifier, it.file) }
            .map { it.file }

    private fun candidateNames(identifier: ComponentIdentifier?, file: File): List<String> {
        val names = when (identifier) {
            is ModuleComponentIdentifier -> listOf(
                "${identifier.group}:${identifier.module}",
                "${identifier.group}:${identifier.module}:${identifier.version}",
                identifier.module,
            )
            is ProjectComponentIdentifier -> listOf(
                identifier.projectPath,
                identifier.projectPath.removePrefix(":"),
                identifier.projectName,
            )
            else -> emptyList()
        }
        return names + file.nameWithoutExtension
    }

    companion object {
        /**
         * Validates a user-supplied pattern at configuration time (via `bundleExclude` /
         * `bundleOnly`) instead of failing deep inside jar execution.
         */
        fun validate(pattern: String): String {
            if (pattern.isBlank()) {
                throw IllegalArgumentException("Blank bundle dependency pattern")
            }
            val trimmed = pattern.trim()
            try {
                trimmed.toRegex()
            } catch (e: PatternSyntaxException) {
                throw IllegalArgumentException("Invalid bundle dependency pattern '$trimmed': ${e.description}", e)
            }
            return trimmed
        }
    }
}
