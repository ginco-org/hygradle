package gg.ginco.hygradle.tasks

import gg.ginco.hygradle.internal.ServerDownloader
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.work.DisableCachingByDefault
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Downloads the Hytale server files into the run directory without starting
 * a server. Other build plugins should depend on this task when they need
 * the server files present (e.g. to run their own test harnesses).
 */
@DisableCachingByDefault(because = "Downloads into a user-level cache, not build outputs")
abstract class ProvisionServerTask : DefaultTask() {
    @get:Input
    abstract val serverVersion: Property<String>

    @get:Internal
    abstract val serverDir: DirectoryProperty

    @get:Internal
    abstract val gradleUserHomeDir: DirectoryProperty

    @TaskAction
    fun run() {
        val runDir = serverDir.get().asFile
        val cacheDir = File(gradleUserHomeDir.get().asFile, "caches/hygradle/server")
        val firstDownload = !File(runDir, ".hygradle-version").isFile

        if (firstDownload) {
            logger.lifecycle("Provisioning Hytale server ${serverVersion.get()} into ${runDir.absolutePath}")
        }

        ServerDownloader.ensureServerFiles(serverVersion.get(), runDir, cacheDir, logger)

        if (!firstDownload) {
            logger.lifecycle("Server files for ${serverVersion.get()} already provisioned in ${runDir.absolutePath}")
        }
    }
}
