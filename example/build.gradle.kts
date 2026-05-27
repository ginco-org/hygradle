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

    serverVersion = "0.5.1"

    bundleDependencies = true

    mainClass = "com.example.myplugin.Plugin"
}
