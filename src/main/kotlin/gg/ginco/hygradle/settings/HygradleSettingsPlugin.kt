package gg.ginco.hygradle.settings

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

class HygradleSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        settings.pluginManager.apply("org.gradle.toolchains.foojay-resolver-convention")
    }
}
