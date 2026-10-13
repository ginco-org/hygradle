package gg.ginco.hygradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class HytaleWorkspaceExtension {
    abstract val serverVersion: Property<String>
    abstract val modProjects: ListProperty<String>
    abstract val hostProject: Property<String>
    abstract val serverDir: DirectoryProperty
    abstract val jvmArgs: ListProperty<String>
    abstract val sessionAuth: Property<Boolean>

    // Debug
    abstract val debugEnabled: Property<Boolean>
    abstract val debugPort: Property<Int>
    abstract val debugSuspend: Property<Boolean>

    // HotSwap
    abstract val requireDcevm: Property<Boolean>
    abstract val useHotswapAgent: Property<Boolean>
    abstract val hotswapAgentPath: RegularFileProperty // optional — no convention
    abstract val jbrHome: Property<String>             // optional — no convention

    init {
        debugEnabled.convention(false)
        debugPort.convention(5005)
        debugSuspend.convention(false)
        requireDcevm.convention(false)
        useHotswapAgent.convention(false)
    }
}
