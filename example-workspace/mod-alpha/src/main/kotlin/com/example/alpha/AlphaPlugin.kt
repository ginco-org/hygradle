package com.example.alpha

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import java.util.logging.Level

class AlphaPlugin(init: JavaPluginInit) : JavaPlugin(init) {
    override fun setup() {
        logger.at(Level.INFO).log("ModAlpha set up")
    }

    override fun start() {
        logger.at(Level.INFO).log("ModAlpha started")
    }

    override fun shutdown() {
        logger.at(Level.INFO).log("ModAlpha shut down")
    }
}
