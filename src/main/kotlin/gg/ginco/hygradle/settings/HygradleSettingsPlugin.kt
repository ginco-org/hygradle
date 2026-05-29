package gg.ginco.hygradle.settings

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.util.GradleVersion
class HygradleSettingsPlugin : Plugin<Settings> {
   override fun apply(settings: Settings) {
        // Built-in foojay-resolver-convention was removed in Gradle 9.x.
        // Consumers on Gradle 9+ should apply the external version themselves
        // or configure toolchain repositories via jvm-toolchain-management.
        if (GradleVersion.current() < GradleVersion.version("9.0")) {
            settings.pluginManager.apply("org.gradle.toolchains.foojay-resolver-convention")
        }
   }
}
