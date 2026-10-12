package gg.ginco.hygradle

import gg.ginco.hygradle.internal.DependencyBundleFilter
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

data class AuthorInfo(val name: String, val email: String? = null, val url: String? = null) : java.io.Serializable

/** Configure in the top-level `hytale { }` block only. Mutations after wiring are invisible to Gradle up-to-date checks. */
class SubPluginSpec : java.io.Serializable {
    var name: String = ""
    var mainClass: String? = null
    var description: String? = null
    var website: String? = null
    var disabledByDefault: Boolean = false
    var includesAssetPack: Boolean = false
    val authors: MutableList<AuthorInfo> = mutableListOf()
    val dependencies: MutableMap<String, String> = mutableMapOf()
    val optionalDependencies: MutableMap<String, String> = mutableMapOf()
    val loadBefore: MutableMap<String, String> = mutableMapOf()

    fun author(name: String, email: String? = null, url: String? = null) {
        authors.add(AuthorInfo(name, email, url))
    }

    fun depends(pluginId: String, range: String = "*") {
        require(":" in pluginId) { "Plugin ID must be in 'Group:Name' format, got: '$pluginId'" }
        dependencies[pluginId] = range
    }
    fun optDepends(pluginId: String, range: String = "*") {
        require(":" in pluginId) { "Plugin ID must be in 'Group:Name' format, got: '$pluginId'" }
        optionalDependencies[pluginId] = range
    }
    fun loadBefore(pluginId: String, range: String = "*") {
        require(":" in pluginId) { "Plugin ID must be in 'Group:Name' format, got: '$pluginId'" }
        loadBefore[pluginId] = range
    }
}

abstract class HytaleExtension @Inject constructor(project: Project) {
    /**
     * Plugin group — part of the identifier (`Group:Name`).
     * Use a display name like `"Ginco"`, not a Java package.
     */
    abstract val group: Property<String>

    /** Plugin name — part of the identifier (`Group:Name`). Defaults to project name. */
    abstract val name: Property<String>

    abstract val description: Property<String>

    abstract val website: Property<String>

    /** Fully-qualified class name of the plugin entrypoint (extends JavaPlugin). */
    abstract val mainClass: Property<String>

    /**
     * Hytale server version string, e.g. `"2026.02.19-1a311a592"`.
     * Drives the `hytaleApi` compile dependency and the `ServerVersion` manifest field.
     */
    abstract val serverVersion: Property<String>

    abstract val includesAssetPack: Property<Boolean>

    abstract val disabledByDefault: Property<Boolean>

    /** When `true`, bundles all `runtimeClasspath` dependencies into the plugin jar. */
    abstract val bundleDependencies: Property<Boolean>

    /**
     * `group:artifact` patterns (full-match regex) of `runtimeClasspath` dependencies to
     * **skip** when [bundleDependencies] is `true` — add via [bundleExclude].
     * Wins over [bundleIncludes]. Empty (default) excludes nothing.
     */
    abstract val bundleExcludes: ListProperty<String>

    /**
     * Positive list: when non-empty, only `runtimeClasspath` dependencies matching these
     * `group:artifact` patterns (full-match regex) are bundled when [bundleDependencies]
     * is `true` — add via [bundleOnly]. Empty (default) means bundle everything.
     */
    abstract val bundleIncludes: ListProperty<String>

    /** Authors list — add entries via [author]. */
    abstract val authors: ListProperty<AuthorInfo>

    /**
     * Hard dependencies: `"Group:Name"` to semver-range string.
     * Plugin will refuse to load if any are missing.
     */
    abstract val dependencies: MapProperty<String, String>

    /** Optional dependencies: `"Group:Name"` to semver-range. */
    abstract val optionalDependencies: MapProperty<String, String>

    /** `"Group:Name"` entries this plugin must load before. */
    abstract val loadBefore: MapProperty<String, String>

    /** Sub-plugins bundled in the same jar — add via [subPlugin]. */
    abstract val subPlugins: ListProperty<SubPluginSpec>

    /**
     * Maven `group:artifact` for the Hytale server jar.
     * Used as the compile-only `hytaleApi` dep and for `runServer`.
     */
    abstract val artifact: Property<String>

    val server: ServerExtension = project.objects.newInstance(ServerExtension::class.java, project)

    fun server(configure: ServerExtension.() -> Unit) = server.configure()

    fun author(name: String, email: String? = null, url: String? = null) {
        authors.add(AuthorInfo(name, email, url))
    }

    fun subPlugin(configure: SubPluginSpec.() -> Unit) {
        subPlugins.add(SubPluginSpec().apply(configure))
    }

    /**
     * Excludes dependencies from bundling. Accepts `group:artifact` coordinates or regexes
     * (full match), e.g.:
     *
     * ```kotlin
     * hytale {
     *     bundleDependencies = true
     *     bundleExclude("gg.ginco:hytale-minigame-framework") // shared framework plugin in mods/
     *     bundleExclude("gg.ginco:hytale-codec-.*")         // regex
     * }
     * ```
     *
     * Only meaningful when [bundleDependencies] is `true`.
     */
    fun bundleExclude(vararg patterns: String) {
        bundleExcludes.addAll(patterns.map { DependencyBundleFilter.validate(it) })
    }

    /**
     * Positive list: when given, **only** matching dependencies are bundled.
     * e.g. `bundleOnly("org.jetbrains.kotlin:kotlin-stdlib.*")`.
     *
     * Only meaningful when [bundleDependencies] is `true`. [bundleExclude] patterns still win.
     */
    fun bundleOnly(vararg patterns: String) {
        bundleIncludes.addAll(patterns.map { DependencyBundleFilter.validate(it) })
    }

    fun depends(pluginId: String, range: String = "*") {
        require(":" in pluginId) { "Plugin ID must be in 'Group:Name' format, got: '$pluginId'" }
        dependencies.put(pluginId, range)
    }
    fun optDepends(pluginId: String, range: String = "*") {
        require(":" in pluginId) { "Plugin ID must be in 'Group:Name' format, got: '$pluginId'" }
        optionalDependencies.put(pluginId, range)
    }
    fun loadBefore(pluginId: String, range: String = "*") {
        require(":" in pluginId) { "Plugin ID must be in 'Group:Name' format, got: '$pluginId'" }
        loadBefore.put(pluginId, range)
    }

    init {
        name.convention(project.name)
        description.convention("")
        website.convention("")
        includesAssetPack.convention(false)
        disabledByDefault.convention(false)
        bundleDependencies.convention(false)
        bundleExcludes.convention(emptyList())
        bundleIncludes.convention(emptyList())
        authors.convention(emptyList())
        dependencies.convention(emptyMap())
        optionalDependencies.convention(emptyMap())
        loadBefore.convention(emptyMap())
        subPlugins.convention(emptyList())
        artifact.convention("com.hypixel.hytale:Server")
    }
}
