package com.example.myplugin

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import java.util.logging.Level

class Plugin(init: JavaPluginInit) : JavaPlugin(init) {
    override fun setup() {
        logger.at(Level.INFO).log("ExamplePlugin set up")
    }

    override fun start() {
        logger.at(Level.INFO).log("ExamplePlugin started")
    }

    override fun shutdown() {
        logger.at(Level.INFO).log("ExamplePlugin shut down")
    }
}
