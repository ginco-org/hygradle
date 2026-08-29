package gg.ginco.hygradle.internal

import com.google.gson.JsonParser
import org.gradle.api.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Creates Hytale game sessions so the dev server can be launched with
 * --session-token / --identity-token instead of interactive in-server /auth.
 *
 * Mirrors hyctl's flow: an `auth:server` access token (client `hytale-server`)
 * is exchanged at sessions.hytale.com for a game session bound to one of the
 * account's game profiles.
 */
object GameSession {
    private const val SESSIONS_BASE = "https://sessions.hytale.com"
    private const val ACCOUNT_DATA_BASE = "https://account-data.hytale.com"

    // The sessions/account-data APIs verify launcher identification headers.
    private const val LAUNCHER_VERSION = "2026.05.29-125f35f"
    private const val LAUNCHER_USER_AGENT = "hytale-launcher/$LAUNCHER_VERSION"
    private const val LAUNCHER_BRANCH = "release"

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()

    data class SessionTokens(val sessionToken: String, val identityToken: String)

    /**
     * Creates a game session for the first game profile of the account behind
     * [accessToken] (an `auth:server`-scoped token).
     */
    fun create(accessToken: String, logger: Logger): SessionTokens {
        val profile = resolveProfile(accessToken, logger)

        logger.lifecycle("Creating game session for profile '${profile.second}'...")
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$SESSIONS_BASE/game-session/new"))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .header("User-Agent", LAUNCHER_USER_AGENT)
            .header("X-Hytale-Launcher-Version", LAUNCHER_VERSION)
            .header("X-Hytale-Launcher-Branch", LAUNCHER_BRANCH)
            .POST(HttpRequest.BodyPublishers.ofString("""{"uuid":"${profile.first}"}"""))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "game-session/new failed: HTTP ${response.statusCode()} — ${response.body().take(200)}")
        }
        return parseSessionTokens(response.body())
    }

    private fun resolveProfile(accessToken: String, logger: Logger): Pair<String, String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$ACCOUNT_DATA_BASE/my-account/get-profiles"))
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .header("User-Agent", LAUNCHER_USER_AGENT)
            .header("X-Hytale-Launcher-Version", LAUNCHER_VERSION)
            .header("X-Hytale-Launcher-Branch", LAUNCHER_BRANCH)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "get-profiles failed: HTTP ${response.statusCode()} — ${response.body().take(200)}")
        }
        val profile = parseFirstProfile(response.body())
        logger.lifecycle("Using game profile '${profile.second}' (${profile.first})")
        return profile
    }

    /** Returns (uuid, username) of the account's first game profile. */
    internal fun parseFirstProfile(json: String): Pair<String, String> {
        val root = JsonParser.parseString(json).asJsonObject
        val profiles = root.getAsJsonArray("profiles")
            ?: throw IllegalStateException("profiles response missing 'profiles'")
        if (profiles.size() == 0) {
            throw IllegalStateException("account has no game profiles")
        }
        val first = profiles[0].asJsonObject
        val uuid = first.get("uuid")?.asString
            ?: throw IllegalStateException("profile missing 'uuid'")
        val username = first.get("username")?.asString ?: uuid
        return uuid to username
    }

    internal fun parseSessionTokens(json: String): SessionTokens {
        val obj = JsonParser.parseString(json).asJsonObject
        val sessionToken = obj.get("sessionToken")?.asString
            ?: throw IllegalStateException("session response missing 'sessionToken'")
        val identityToken = obj.get("identityToken")?.asString
            ?: throw IllegalStateException("session response missing 'identityToken'")
        return SessionTokens(sessionToken, identityToken)
    }
}
