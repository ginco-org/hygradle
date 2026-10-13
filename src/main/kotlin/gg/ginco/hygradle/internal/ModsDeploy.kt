package gg.ginco.hygradle.internal

import org.gradle.api.logging.Logger
import java.io.File

/**
 * Deploys mod jars into `<runDir>/mods` by building a fresh `mods.tmp` directory and
 * swapping it into place. Rebuilding from scratch (instead of copying over the existing
 * directory) removes stale jars from previous builds — renamed or re-versioned plugins
 * would otherwise accumulate and get loaded twice.
 */
internal object ModsDeploy {
    fun deploy(runDir: File, modJars: Collection<File>, logger: Logger) {
        val modsDir = File(runDir, "mods")
        val modsTmp = File(runDir, "mods.tmp").also {
            it.deleteRecursively()
            it.mkdirs()
        }

        modJars.forEach { jar ->
            jar.copyTo(File(modsTmp, jar.name), overwrite = true)
        }
        modsDir.deleteRecursively()
        try {
            java.nio.file.Files.move(
                modsTmp.toPath(), modsDir.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                modsTmp.toPath(), modsDir.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
        logger.lifecycle("Deployed ${modJars.size} mod(s) to ${modsDir.absolutePath}")
    }
}