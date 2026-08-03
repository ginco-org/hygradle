package gg.ginco.hygradle.internal

import com.google.gson.JsonParser
import org.gradle.api.logging.Logger
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.ZipInputStream

/**
 * Downloads Hytale server files (HytaleServer.jar, HytaleServer.aot.config, Assets.zip)
 * into a system-wide cache directory. Each project's run directory references the
 * cached files by path — no duplication.
 */
object ServerDownloader {
    private const val ASSETS_API = "https://account-data.hytale.com/game-assets"
    private const val MAVEN_BASE = "https://maven.hytale.com"
    private val CHANNELS = listOf("release", "pre-release")

    private val SERVER_FILES = listOf("HytaleServer.jar", "HytaleServer.aot.config", "Assets.zip")
    private val ZIP_ENTRIES = mapOf(
        "Server/HytaleServer.jar" to "HytaleServer.jar",
        "Server/HytaleServer.aot.config" to "HytaleServer.aot.config",
        "Assets.zip" to "Assets.zip",
    )
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()

    /**
     * Ensures all server files for [version] are cached in [cacheDir]/<version>/
     * and a version marker is written in [targetDir].
     * Tries the authenticated R2 full-build first, falls back to Maven JAR-only.
     * Returns the version cache directory so callers can reference files by path.
     */
    fun ensureServerFiles(version: String, targetDir: File, cacheDir: File, logger: Logger): File {
        val marker = File(targetDir, ".hygradle-version")
        val versionCacheDir = File(cacheDir, version)
        if (marker.isFile && marker.readText().trim() == version
            && SERVER_FILES.all { File(versionCacheDir, it).isFile }) {
            logger.lifecycle("Server files for $version already present, skipping download")
            return versionCacheDir
        }

        // Download into cache if not already there
        if (!File(versionCacheDir, ".hygradle-version").isFile
            || File(versionCacheDir, ".hygradle-version").readText().trim() != version
            || !SERVER_FILES.all { File(versionCacheDir, it).isFile }) {

            versionCacheDir.mkdirs()

            try {
                fullBuild(version, versionCacheDir, logger)
            } catch (e: Exception) {
                logger.lifecycle("Full build download failed: ${e.message}")
                logger.lifecycle("Falling back to JAR-only download from Maven")
                jarOnly(version, versionCacheDir, logger)
            }

            File(versionCacheDir, ".hygradle-version").writeText(version)
        }

        targetDir.mkdirs()
        marker.writeText(version)
        return versionCacheDir
    }

    // ---- Full build (authenticated R2) -----------------------------------------

    private fun fullBuild(version: String, targetDir: File, logger: Logger) {
        val token = HytaleAuth.getAccessToken(logger)

        for (channel in CHANNELS) {
            try {
                val r2Url = resolveR2Url(token, channel, version)
                logger.lifecycle("Downloading full build ($channel/$version)...")
                val zipFile = downloadTo(r2Url, File(targetDir, ".build.zip"), logger)
                extractEntries(zipFile, targetDir)
                zipFile.delete()
                logger.lifecycle("Extracted server files to ${targetDir.absolutePath}")
                return
            } catch (e: Exception) {
                logger.lifecycle("  $channel: ${e.message}")
            }
        }
        throw IllegalStateException("R2 unavailable on all channels")
    }

    private fun resolveR2Url(token: String, channel: String, version: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$ASSETS_API/builds/$channel/$version.zip"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
            val json = JsonParser.parseString(response.body()).asJsonObject
            if (json.has("url")) return json.get("url").asString
        }

        throw IllegalStateException("HTTP ${response.statusCode()} for $channel/$version")
    }

    // ---- JAR-only (public Maven, no auth) --------------------------------------

    private fun jarOnly(version: String, targetDir: File, logger: Logger) {
        for (channel in CHANNELS) {
            val url = "$MAVEN_BASE/$channel/com/hypixel/hytale/Server/$version/Server-$version.jar"
            try {
                val dest = File(targetDir, "HytaleServer.jar")
                downloadTo(url, dest, logger)
                logger.lifecycle("Downloaded server JAR from Maven ($channel)")
                return
            } catch (_: Exception) { /* try next channel */ }
        }
        throw IllegalStateException("Server JAR unavailable on all Maven channels")
    }

    // ---- Shared helpers --------------------------------------------------------

    private fun downloadTo(url: String, dest: File, logger: Logger): File {
        // Follow redirects for the actual download
        val followingClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = followingClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Download failed: HTTP ${response.statusCode()}")
        }

        val contentLength = response.headers().firstValueAsLong("content-length").orElse(-1)

        dest.outputStream().buffered().use { out ->
            response.body().use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                var lastLog = 0L

                var read = input.read(buf)
                while (read != -1) {
                    out.write(buf, 0, read)
                    total += read
                    if (total - lastLog >= 10 * 1024 * 1024) {
                        val msg = if (contentLength > 0) {
                            "  ${total / (1024 * 1024)} / ${contentLength / (1024 * 1024)} MB"
                        } else {
                            "  ${total / (1024 * 1024)} MB"
                        }
                        logger.lifecycle(msg)
                        lastLog = total
                    }
                    read = input.read(buf)
                }
            }
        }

        return dest
    }

    private fun extractEntries(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val destName = ZIP_ENTRIES[entry.name]
                if (destName != null) {
                    File(targetDir, destName).outputStream().buffered().use { out ->
                        zip.copyTo(out)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
