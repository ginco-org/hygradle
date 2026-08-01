# Hygradle

A Gradle plugin for [Hytale](https://hytale.com) plugin development. Handles manifest generation, server dependency management, and one-command dev server startup.

## Setup

**`settings.gradle.kts`**
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("gg.ginco.hygradle.settings") version "0.2.0"
}
```

**`build.gradle.kts`**
```kotlin
plugins {
    id("gg.ginco.hygradle") version "0.2.0"
    kotlin("jvm") version "2.3.21"  // optional
}
```

## Usage

```kotlin
hytale {
    group = "MyGroup"                          // required
    mainClass = "com.example.myplugin.Plugin"  // required
    serverVersion = "0.5.1"                    // required

    name = "MyPlugin"
    description = "Does cool things"
    website = "https://example.com"
    author("jane doe", email = "jane@example.com")

    // Bundle runtime dependencies (Kotlin stdlib, etc.) into the plugin jar.
    // Required if your plugin uses Kotlin or any library not provided by the server.
    bundleDependencies = true
}
```

### Tasks

| Task | Description |
|------|-------------|
| `generatePluginManifest` | Generates `manifest.json` from the `hytale { }` block |
| `jar` | Builds the plugin jar with the manifest included |
| `runServer` | Downloads server files if missing, deploys your plugin, and starts a dev server |

### Run server

```bash
./gradlew runServer
```

On first run, the server files (`HytaleServer.jar`, `HytaleServer.aot.config`, `Assets.zip`) are downloaded to the `run/` directory. Subsequent runs skip the download if the files are already present for the requested version.

If your server version requires authentication, the task will guide you through a one-time device code login. Credentials are cached locally.

Server options:

```kotlin
hytale {
    // ...

    server {
        serverDir.set(file("run"))              // working directory for the server
        jvmArgs.set(listOf("-Xmx4G", "-Xms1G")) // JVM arguments
    }
}
```

## Configuration reference

### `hytale { }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `group` | `String` | — | Plugin group identifier. **Required.** |
| `mainClass` | `String` | — | Fully-qualified entry point class. **Required.** |
| `serverVersion` | `String` | — | Hytale server version. **Required.** |
| `name` | `String` | Project name | Plugin name |
| `description` | `String` | `""` | Plugin description |
| `website` | `String` | `""` | Plugin website URL |
| `bundleDependencies` | `Boolean` | `false` | Bundle runtime deps into the jar |
| `includesAssetPack` | `Boolean` | `false` | Plugin includes an asset pack |
| `disabledByDefault` | `Boolean` | `false` | Plugin is disabled by default |
| `artifact` | `String` | `com.hypixel.hytale:Server` | Maven coordinates for the server jar |

### Authors

```kotlin
hytale {
    author("jane doe")
    author("john", email = "john@example.com", url = "https://john.dev")
}
```

### Dependencies

```kotlin
hytale {
    depends("OtherGroup:OtherPlugin")                        // hard dependency
    optDepends("OptionalGroup:OptionalPlugin", "^1.0.0")    // optional, with version range
    loadBefore("EarlyGroup:EarlyPlugin")                    // load ordering
}
```

### Sub-plugins

```kotlin
hytale {
    subPlugin {
        name = "SubModule"
        mainClass = "com.example.myplugin.SubModule"
        author("jane doe")
    }
}
```

## License

MIT
