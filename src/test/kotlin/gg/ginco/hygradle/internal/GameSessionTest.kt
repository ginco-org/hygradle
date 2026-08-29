package gg.ginco.hygradle.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameSessionTest {

    @Test
    fun `parseFirstProfile — returns uuid and username of first profile`() {
        val json = """
            {
              "owner": "owner-uuid",
              "profiles": [
                { "uuid": "profile-1", "username": "taya" },
                { "uuid": "profile-2", "username": "other" }
              ]
            }
        """.trimIndent()

        val profile = GameSession.parseFirstProfile(json)

        assertEquals("profile-1" to "taya", profile)
    }

    @Test
    fun `parseFirstProfile — empty profiles array fails`() {
        val json = """{ "profiles": [] }"""

        val error = assertFailsWith<IllegalStateException> { GameSession.parseFirstProfile(json) }

        assertEquals("account has no game profiles", error.message)
    }

    @Test
    fun `parseFirstProfile — missing profiles key fails`() {
        val json = """{ "owner": "owner-uuid" }"""

        val error = assertFailsWith<IllegalStateException> { GameSession.parseFirstProfile(json) }

        assertEquals("profiles response missing 'profiles'", error.message)
    }

    @Test
    fun `parseFirstProfile — missing username falls back to uuid`() {
        val json = """{ "profiles": [ { "uuid": "profile-1" } ] }"""

        val profile = GameSession.parseFirstProfile(json)

        assertEquals("profile-1" to "profile-1", profile)
    }

    @Test
    fun `parseSessionTokens — parses camelCase fields`() {
        val json = """
            {
              "sessionToken": "st-123",
              "identityToken": "it-456",
              "expiresAt": "2026-08-29T20:00:00Z"
            }
        """.trimIndent()

        val tokens = GameSession.parseSessionTokens(json)

        assertEquals("st-123", tokens.sessionToken)
        assertEquals("it-456", tokens.identityToken)
    }

    @Test
    fun `parseSessionTokens — missing identityToken fails`() {
        val json = """{ "sessionToken": "st-123" }"""

        val error = assertFailsWith<IllegalStateException> { GameSession.parseSessionTokens(json) }

        assertEquals("session response missing 'identityToken'", error.message)
    }
}
