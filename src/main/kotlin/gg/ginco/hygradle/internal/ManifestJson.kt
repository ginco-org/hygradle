package gg.ginco.hygradle.internal

import com.google.gson.GsonBuilder
import com.google.gson.internal.LinkedTreeMap

/**
 * Converts a bare version string into a semver range expression.
 * - Already a range operator (`=`, `^`, `~`, `>`, `<`, `*`, `x`, `X`) → pass through
 * - Bare digit (e.g. `"1.2.3"`) → `=1.2.3`
 * - Empty → empty
 */
fun toSemverRange(version: String): String {
    val trimmed = version.trim()
    if (trimmed.isEmpty()) return trimmed
    val first = trimmed.first()
    if (first in setOf('=', '^', '~', '>', '<', '*', 'x', 'X')) return trimmed
    if (first.isDigit()) return "=$trimmed"
    return trimmed
}

private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

/** Serializes a manifest map to pretty-printed JSON. */
fun serializeManifest(map: Map<String, Any>): String = gson.toJson(map)
