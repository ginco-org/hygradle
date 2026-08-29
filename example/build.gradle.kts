// Single-project example. For monorepo/workspace setup, see README.md § Monorepo / Workspace.
plugins {
    id("gg.ginco.hygradle")
    kotlin("jvm") version "2.3.21"
}

version = "1.0.0"

hytale {
    group = "Example"
    name = "MyPlugin"
    description = "Example Hytale plugin"
    website = "example.com"
    author("jane doe", email = "jane.doe@example.com")

    serverVersion = "0.6.2"

    bundleDependencies = true

    mainClass = "com.example.myplugin.Plugin"

    server {
        serverDir.set(file("run"))
        jvmArgs.set(listOf("-Xmx4G", "-Xms1G"))

        // Uncomment to attach a debugger (connect IntelliJ to localhost:5005)
        // debugEnabled = true
        // debugPort = 5005
        // debugSuspend = false  // set to true to wait for debugger before starting

        // Uncomment for full HotSwap support with JetBrains Runtime
        // requireDcevm = true
        // useHotswapAgent = true  // auto-downloads hotswap-agent.jar on first use
        // jbrHome = "/path/to/jbr"
    }
}
