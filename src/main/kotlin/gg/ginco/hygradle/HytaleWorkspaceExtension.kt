package gg.ginco.hygradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class HytaleWorkspaceExtension {
    abstract val serverVersion: Property<String>
    abstract val modProjects: ListProperty<String>
    abstract val hostProject: Property<String>
    abstract val serverDir: DirectoryProperty
    abstract val jvmArgs: ListProperty<String>
}
