package gg.ginco.hygradle.tasks

import gg.ginco.hygradle.AuthorInfo
import gg.ginco.hygradle.SubPluginSpec
import gg.ginco.hygradle.internal.serializeManifest
import gg.ginco.hygradle.internal.toSemverRange
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

@CacheableTask
abstract class GenerateManifestTask : DefaultTask() {
    @get:Input abstract val pluginGroup: Property<String>
    @get:Input abstract val pluginName: Property<String>
    @get:Input abstract val pluginVersion: Property<String>
    @get:Input @get:Optional abstract val pluginDescription: Property<String>
    @get:Input @get:Optional abstract val website: Property<String>
    @get:Input @get:Optional abstract val mainClass: Property<String>
    @get:Input @get:Optional abstract val serverVersion: Property<String>
    @get:Input abstract val authors: ListProperty<AuthorInfo>
    @get:Input abstract val dependencies: MapProperty<String, String>
    @get:Input abstract val optionalDependencies: MapProperty<String, String>
    @get:Input abstract val loadBefore: MapProperty<String, String>
    @get:Input abstract val includesAssetPack: Property<Boolean>
    @get:Input abstract val disabledByDefault: Property<Boolean>
    @get:Input abstract val subPlugins: ListProperty<SubPluginSpec>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val manifest = linkedMapOf<String, Any>()
        manifest["Group"] = pluginGroup.get()
        manifest["Name"] = pluginName.get()
        manifest["Version"] = pluginVersion.get()
        pluginDescription.orNull?.takeIf { it.isNotEmpty() }?.let { manifest["Description"] = it }

        val authorsList = authors.get()
        if (authorsList.isNotEmpty()) {
            manifest["Authors"] = authorsList.map { a ->
                linkedMapOf<String, String>().apply {
                    put("Name", a.name)
                    a.email?.let { put("Email", it) }
                    a.url?.let { put("Url", it) }
                }
            }
        }

        website.orNull?.takeIf { it.isNotEmpty() }?.let { manifest["Website"] = it }
        mainClass.orNull?.takeIf { it.isNotEmpty() }?.let { manifest["Main"] = it }
        serverVersion.orNull?.takeIf { it.isNotEmpty() }?.let { manifest["ServerVersion"] = toSemverRange(it) }
        dependencies.get().takeIf { it.isNotEmpty() }?.let { manifest["Dependencies"] = it }
        optionalDependencies.get().takeIf { it.isNotEmpty() }?.let { manifest["OptionalDependencies"] = it }
        loadBefore.get().takeIf { it.isNotEmpty() }?.let { manifest["LoadBefore"] = it }
        if (disabledByDefault.get()) manifest["DisabledByDefault"] = true
        if (includesAssetPack.get()) manifest["IncludesAssetPack"] = true

        subPlugins.get().takeIf { it.isNotEmpty() }?.let { list ->
            manifest["SubPlugins"] = list.map { sub -> subPluginMap(sub) }
        }

        val out = outputDir.get().asFile.also { it.mkdirs() }
        File(out, "manifest.json").writeText(serializeManifest(manifest))

        logger.lifecycle(
            "Generated manifest.json for '${pluginGroup.get()}:${pluginName.get()}' v${pluginVersion.get()}"
        )
    }

    private fun subPluginMap(sub: SubPluginSpec): Map<String, Any> = linkedMapOf<String, Any>().apply {
        put("Name", sub.name)
        sub.mainClass?.let { put("Main", it) }
        sub.description?.takeIf { it.isNotEmpty() }?.let { put("Description", it) }
        sub.authors.takeIf { it.isNotEmpty() }?.let { list ->
            put("Authors", list.map { a ->
                linkedMapOf<String, String>().apply {
                    put("Name", a.name)
                    a.email?.let { put("Email", it) }
                    a.url?.let { put("Url", it) }
                }
            })
        }
        sub.website?.takeIf { it.isNotEmpty() }?.let { put("Website", it) }
        sub.dependencies.takeIf { it.isNotEmpty() }?.let { put("Dependencies", it) }
        sub.optionalDependencies.takeIf { it.isNotEmpty() }?.let { put("OptionalDependencies", it) }
        sub.loadBefore.takeIf { it.isNotEmpty() }?.let { put("LoadBefore", it) }
        if (sub.disabledByDefault) put("DisabledByDefault", true)
        if (sub.includesAssetPack) put("IncludesAssetPack", true)
    }
}
