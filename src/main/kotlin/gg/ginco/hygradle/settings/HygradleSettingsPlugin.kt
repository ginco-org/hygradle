package gg.ginco.hygradle.settings

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging

/**
 * Registers the foojay toolchain resolver so toolchains (e.g. the Java 25 toolchain
 * hygradle configures by default) can be auto-provisioned on machines without a
 * matching local JDK.
 *
 * The resolver is bundled as a runtime dependency of this settings plugin (`org.gradle.toolchains:foojay-resolver`),
 * so this works on Gradle 7.6 through 9.x — the previously built-in
 * `foojay-resolver-convention` was removed in Gradle 9.0.
 */
class HygradleSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        try {
            settings.pluginManager.apply("org.gradle.toolchains.foojay-resolver-convention")
        } catch (t: Throwable) {
            Logging.getLogger("hygradle/settings").warn(
                "Could not register the foojay toolchain resolver ({}: {}) — toolchain auto-provisioning will not work; " +
                    "install a matching JDK locally or apply 'org.gradle.toolchains.foojay-resolver-convention' yourself.",
                t.javaClass.simpleName, t.message)
        }
    }
}
