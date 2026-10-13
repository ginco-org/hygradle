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
        if (marker.isFile && marker.readText().trim() == version && isProvisioned(versionCacheDir, version)) {
            logger.lifecycle("Server files for $version already present, skipping download")
            return versionCacheDir
        }

        // Download into cache if not already there
        if (!isProvisioned(versionCacheDir, version)) {

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

    /**
     * Cache directory holding the server files for [version] —
     * `<cacheDir>/<version>/`. Callers that have already run a provisioning
     * step can reference files by path without re-checking the cache.
     */
    fun versionCacheDir(cacheDir: File, version: String): File = File(cacheDir, version)

    /**
     * A version cache counts as provisioned when the full server file set is present, or
     * when a previous run fell back to the JAR-only Maven download (`.hygradle-jaronly`
     * marker) — re-attempting the failing full build on every run would just repeat it.
     * Delete the version cache dir to retry the full build.
     */
    private fun isProvisioned(versionCacheDir: File, version: String): Boolean {
        val cacheMarker = File(versionCacheDir, ".hygradle-version")
        if (!cacheMarker.isFile || cacheMarker.readText().trim() != version) return false
        if (SERVER_FILES.all { File(versionCacheDir, it).isFile }) return true
        return File(versionCacheDir, "HytaleServer.jar").isFile &&
            File(versionCacheDir, ".hygradle-jaronly").isFile
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
                // Fail the channel when the zip layout changed and entries went missing —
                // otherwise a channel-1 mismatch would mark an empty cache as complete.
                val missing = SERVER_FILES.filter { !File(targetDir, it).isFile }
                if (missing.isNotEmpty()) {
                    throw IllegalStateException("build zip did not contain ${missing.joinToString()}")
                }
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
                downloadTo(url, File(targetDir, "HytaleServer.jar"), logger)
                logger.lifecycle("Downloaded server JAR from Maven ($channel)")
                // Marks the cache as JAR-only (assets unavailable) so later runs skip the
                // failing full-build attempt instead of repeating it on every provision.
                File(targetDir, ".hygradle-jaronly").writeText(
                    "Assets.zip/HytaleServer.aot.config unavailable — authenticated full build failed")
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

        // Download to a temp file and move into place — an interrupted download must not
        // leave a truncated file behind that later passes the present-file cache check.
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        try {
            tmp.outputStream().buffered().use { out ->
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
            try {
                java.nio.file.Files.move(
                    tmp.toPath(), dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    tmp.toPath(), dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
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
