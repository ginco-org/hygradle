package com.example.beta

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import java.util.logging.Level

class BetaPlugin(init: JavaPluginInit) : JavaPlugin(init) {
    override fun setup() {
        logger.at(Level.INFO).log("ModBeta set up")
    }

    override fun start() {
        logger.at(Level.INFO).log("ModBeta started")
    }

    override fun shutdown() {
        logger.at(Level.INFO).log("ModBeta shut down")
    }
}
