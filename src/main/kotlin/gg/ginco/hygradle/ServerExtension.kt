package gg.ginco.hygradle

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ServerExtension @Inject constructor(project: Project) {
    /** Directory where the dev server runs. Defaults to `<projectDir>/run`. */
    abstract val serverDir: DirectoryProperty

    /** JVM arguments passed to the server process. */
    abstract val jvmArgs: ListProperty<String>

    // Debug
    abstract val debugEnabled: Property<Boolean>
    abstract val debugPort: Property<Int>
    abstract val debugSuspend: Property<Boolean>

    // Session auth
    abstract val sessionAuth: Property<Boolean>

    // HotSwap
    abstract val requireDcevm: Property<Boolean>
    abstract val useHotswapAgent: Property<Boolean>
    abstract val hotswapAgentPath: RegularFileProperty  // optional — no convention
    abstract val jbrHome: Property<String>              // optional — no convention

    init {
        serverDir.convention(project.layout.projectDirectory.dir("run"))
        jvmArgs.convention(listOf("-Xmx4G", "-Xms1G"))
        debugEnabled.convention(false)
        debugPort.convention(5005)
        debugSuspend.convention(false)
        sessionAuth.convention(true)
        requireDcevm.convention(false)
        useHotswapAgent.convention(false)
    }
}
