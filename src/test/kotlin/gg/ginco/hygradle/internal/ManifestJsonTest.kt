package gg.ginco.hygradle.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class ManifestJsonTest {

    // -- toSemverRange() ------------------------------------------------------

    @Test
    fun `bare version gets equals prefix`() {
        assertEquals("=1.0.0", toSemverRange("1.0.0"))
        assertEquals("=0.5.1", toSemverRange("0.5.1"))
    }

    @Test
    fun `range operators pass through`() {
        assertEquals("^1.0.0", toSemverRange("^1.0.0"))
        assertEquals("~1.0.0", toSemverRange("~1.0.0"))
        assertEquals(">=1.0.0", toSemverRange(">=1.0.0"))
        assertEquals(">1.0.0", toSemverRange(">1.0.0"))
        assertEquals("<1.0.0", toSemverRange("<1.0.0"))
        assertEquals("=1.0.0", toSemverRange("=1.0.0"))
    }

    @Test
    fun `wildcards pass through`() {
        assertEquals("*", toSemverRange("*"))
        assertEquals("x", toSemverRange("x"))
        assertEquals("X", toSemverRange("X"))
    }

    @Test
    fun `empty and blank return empty`() {
        assertEquals("", toSemverRange(""))
        assertEquals("", toSemverRange("  "))
    }

    @Test
    fun `version with leading whitespace is trimmed`() {
        assertEquals("=1.0.0", toSemverRange("  1.0.0  "))
    }

    // -- serializeManifest() via Gson -----------------------------------------

    @Test
    fun `serializeManifest produces valid JSON with correct keys`() {
        val json = serializeManifest(linkedMapOf(
            "Group" to "Example",
            "Version" to "1.0.0",
            "Active" to true
        ))
        assertEquals(
            """
            {
              "Group": "Example",
              "Version": "1.0.0",
              "Active": true
            }
            """.trimIndent(),
            json
        )
    }

    @Test
    fun `serializeManifest handles nested structures`() {
        val json = serializeManifest(linkedMapOf(
            "Name" to "test",
            "Authors" to listOf(
                linkedMapOf("Name" to "Alice", "Email" to "a@b.com")
            )
        ))
        assertEquals(
            """
            {
              "Name": "test",
              "Authors": [
                {
                  "Name": "Alice",
                  "Email": "a@b.com"
                }
              ]
            }
            """.trimIndent(),
            json
        )
    }

    @Test
    fun `serializeManifest handles strings with special characters`() {
        val json = serializeManifest(linkedMapOf("Desc" to "line1\nline2\ttab\"quote"))
        assertEquals(
            """
            {
              "Desc": "line1\nline2\ttab\"quote"
            }
            """.trimIndent(),
            json
        )
    }

    @Test
    fun `serializeManifest handles empty map`() {
        assertEquals("{}", serializeManifest(emptyMap()))
    }
}
