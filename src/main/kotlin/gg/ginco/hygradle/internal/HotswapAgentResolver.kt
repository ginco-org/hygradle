package gg.ginco.hygradle.internal

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

internal object HotswapAgentResolver {
    private const val VERSION = "1.4.2"
    private const val URL = "https://repo1.maven.org/maven2/org/hotswap/agent/hotswap-agent/$VERSION/hotswap-agent-$VERSION.jar"

    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()

    fun resolve(gradleUserHomeDir: File, logger: Logger): String {
        val cacheDir = File(gradleUserHomeDir, "caches/hygradle")
        val agentJar = File(cacheDir, "hotswap-agent-$VERSION.jar")

        if (agentJar.exists() && agentJar.length() > 0) {
            return agentJar.absolutePath
        }

        logger.lifecycle("Downloading HotSwap Agent $VERSION...")
        cacheDir.mkdirs()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(URL))
            .GET()
            .timeout(java.time.Duration.ofMinutes(5))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            throw GradleException(
                "Failed to download HotSwap Agent (HTTP ${response.statusCode()}).\n" +
                "Set server { hotswapAgentPath = \"/path/to/hotswap-agent.jar\" } to provide it manually."
            )
        }

        val tmp = File(cacheDir, "hotswap-agent-$VERSION.jar.tmp")
        try {
            tmp.outputStream().buffered().use { out ->
                response.body().use { it.copyTo(out) }
            }
            // Verify checksum against Maven Central's published .sha256
            val sha256Url = URL.replace(".jar", ".jar.sha256")
            val shaRequest = HttpRequest.newBuilder()
                .uri(URI.create(sha256Url))
                .GET()
                .timeout(java.time.Duration.ofSeconds(30))
                .build()
            val shaResponse = httpClient.send(shaRequest, HttpResponse.BodyHandlers.ofString())
            if (shaResponse.statusCode() !in 200..299) {
                tmp.delete()
                throw GradleException(
                    "Failed to fetch HotSwap Agent checksum (HTTP ${shaResponse.statusCode()}).\n" +
                    "Set server { hotswapAgentPath = \"/path/to/hotswap-agent.jar\" } to provide it manually."
                )
            }
            val expectedSha = shaResponse.body().trim().lowercase()
            val actualSha = java.security.MessageDigest.getInstance("SHA-256").let { md ->
                tmp.inputStream().use { input ->
                    val buf = ByteArray(8192)
                    var read = input.read(buf)
                    while (read != -1) { md.update(buf, 0, read); read = input.read(buf) }
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            if (actualSha != expectedSha) {
                tmp.delete()
                throw GradleException(
                    "HotSwap Agent download checksum mismatch.\n" +
                    "Expected: $expectedSha\nActual:   $actualSha\n" +
                    "Set server { hotswapAgentPath = \"/path/to/hotswap-agent.jar\" } to provide it manually."
                )
            }
            try {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    agentJar.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    tmp.toPath(),
                    agentJar.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Exception) {
            tmp.delete()
            throw GradleException(
                "Failed to download HotSwap Agent: ${e.message}\n" +
                "Set server { hotswapAgentPath = \"/path/to/hotswap-agent.jar\" } to provide it manually.",
                e
            )
        }
        logger.lifecycle("HotSwap Agent cached at ${agentJar.absolutePath}")
        return agentJar.absolutePath
    }
}
