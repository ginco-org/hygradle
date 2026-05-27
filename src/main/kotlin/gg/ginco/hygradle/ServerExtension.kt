package gg.ginco.hygradle

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

abstract class ServerExtension @Inject constructor(project: Project) {
    /** Directory where the dev server runs. Defaults to `<projectDir>/run`. */
    abstract val serverDir: DirectoryProperty

    /** JVM arguments passed to the server process. */
    abstract val jvmArgs: ListProperty<String>

    init {
        serverDir.convention(project.layout.projectDirectory.dir("run"))
        jvmArgs.convention(listOf("-Xmx4G", "-Xms1G"))
    }
}
