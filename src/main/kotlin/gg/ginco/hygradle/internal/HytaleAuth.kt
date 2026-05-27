package gg.ginco.hygradle.internal

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.logging.Logger
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * OAuth2 device-code authentication against the Hytale account service.
 * Tokens are cached locally and refreshed automatically.
 */
object HytaleAuth {
    private const val OAUTH_BASE = "https://oauth.accounts.hytale.com/oauth2"
    private const val CLIENT_ID = "hytale-downloader"
    private const val SCOPE = "offline auth:downloader"

    private val httpClient = HttpClient.newBuilder().build()

    private data class TokenCache(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochMs: Long,
    )

    private fun tokenPath(): Path {
        val os = System.getProperty("os.name").lowercase()
        val base: Path = when {
            os.contains("win") -> Path.of(System.getenv("APPDATA")
                ?: System.getProperty("user.home"))
            os.contains("mac") -> Path.of(System.getProperty("user.home"),
                "Library", "Application Support")
            else -> Path.of(System.getenv("XDG_DATA_HOME")
                ?: "${System.getProperty("user.home")}/.local/share")
        }
        return base.resolve("hygradle").resolve("token.json")
    }

    private fun loadCache(): TokenCache? {
        val path = tokenPath()
        if (!Files.exists(path)) return null
        return try {
            val json = JsonParser.parseString(path.readText()).asJsonObject
            TokenCache(
                accessToken = json.get("access_token").asString,
                refreshToken = json.get("refresh_token").asString,
                expiresAtEpochMs = json.get("expires_at").asLong,
            )
        } catch (_: Exception) { null }
    }

    private fun saveCache(cache: TokenCache) {
        val path = tokenPath()
        Files.createDirectories(path.parent)
        val json = JsonObject().apply {
            addProperty("access_token", cache.accessToken)
            addProperty("refresh_token", cache.refreshToken)
            addProperty("expires_at", cache.expiresAtEpochMs)
        }
        path.writeText(GsonBuilder().setPrettyPrinting().create().toJson(json))
    }

    private fun refreshToken(refreshToken: String): TokenCache {
        val body = "client_id=$CLIENT_ID&grant_type=refresh_token&refresh_token=$refreshToken"
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$OAUTH_BASE/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException("Token refresh failed: HTTP ${response.statusCode()}")
        }
        val json = JsonParser.parseString(response.body()).asJsonObject
        val cache = TokenCache(
            accessToken = json.get("access_token").asString,
            refreshToken = if (json.has("refresh_token"))
                json.get("refresh_token").asString else refreshToken,
            expiresAtEpochMs = System.currentTimeMillis() +
                    json.get("expires_in").asLong * 1000,
        )
        saveCache(cache)
        return cache
    }

    private fun deviceCodeFlow(logger: Logger): TokenCache {
        val body = "client_id=$CLIENT_ID&scope=${URLEncoder.encode(SCOPE, "UTF-8")}"
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$OAUTH_BASE/device/auth"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IllegalStateException("Device auth request failed: HTTP ${response.statusCode()}")
        }
        val json = JsonParser.parseString(response.body()).asJsonObject
        val deviceCode = json.get("device_code").asString
        val userCode = json.get("user_code").asString
        val verificationUri = json.get("verification_uri").asString
        val interval = json.get("interval")?.asLong ?: 5L

        logger.lifecycle("")
        logger.lifecycle("To authenticate, visit: $verificationUri")
        logger.lifecycle("Enter code: $userCode")
        logger.lifecycle("")

        while (true) {
            Thread.sleep(interval * 1000)

            val pollBody = "grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                    "&device_code=$deviceCode&client_id=$CLIENT_ID"
            val pollRequest = HttpRequest.newBuilder()
                .uri(URI.create("$OAUTH_BASE/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(pollBody))
                .build()
            val pollResponse = httpClient.send(pollRequest, HttpResponse.BodyHandlers.ofString())
            val pollJson = JsonParser.parseString(pollResponse.body()).asJsonObject

            if (pollJson.has("access_token")) {
                val cache = TokenCache(
                    accessToken = pollJson.get("access_token").asString,
                    refreshToken = pollJson.get("refresh_token").asString,
                    expiresAtEpochMs = System.currentTimeMillis() +
                            pollJson.get("expires_in").asLong * 1000,
                )
                saveCache(cache)
                logger.lifecycle("Authentication successful.")
                return cache
            }

            val error = pollJson.get("error")?.asString
            if (error != "authorization_pending" && error != "slow_down") {
                throw IllegalStateException(
                    "Device auth failed: ${pollJson.get("error_description")?.asString ?: error}")
            }
        }
    }

    fun getAccessToken(logger: Logger): String {
        val cached = loadCache()
        if (cached != null) {
            if (cached.expiresAtEpochMs > System.currentTimeMillis() + 60_000) {
                return cached.accessToken
            }
            try {
                return refreshToken(cached.refreshToken).accessToken
            } catch (_: Exception) { /* fall through to device code */ }
        }
        return deviceCodeFlow(logger).accessToken
    }
}
