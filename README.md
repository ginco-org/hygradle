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
    id("gg.ginco.hygradle.settings") version "0.3.1"
}
```

**`build.gradle.kts`**
```kotlin
plugins {
    id("gg.ginco.hygradle") version "0.3.1"
    kotlin("jvm") version "2.3.21"  // optional
}
```

## Usage

```kotlin
hytale {
    group = "MyGroup"                          // required
    mainClass = "com.example.myplugin.Plugin"  // required
    serverVersion = "0.6.2"                    // required

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

On first run, the server files (`HytaleServer.jar`, `HytaleServer.aot.config`, `Assets.zip`) are downloaded to `~/.gradle/caches/hygradle/server/<version>/`. The `run/` directory is used only as the server's working directory (mods, logs, config). Subsequent runs skip the download if the files are already cached for the requested version.

If your server version requires authentication, the task will guide you through a one-time device code login. Credentials are cached locally.

By default, the server also starts fully authenticated: hygradle exchanges a one-time `auth:server` login (client `hytale-server`, cached separately from the download token) for a **game session** and passes `--session-token` / `--identity-token` to the server process — no interactive `/auth` in the server console. Set `server { sessionAuth = false }` to disable this and authenticate manually instead.

Server options:

```kotlin
hytale {
    // ...

    server {
        serverDir.set(file("run"))              // working directory for the server
        jvmArgs.set(listOf("-Xmx4G", "-Xms1G")) // JVM arguments
        sessionAuth = true                      // launch with --session-token/--identity-token (default)
    }
}
```

### Debug and HotSwap

**Basic debugger attachment**

```kotlin
server {
    debugEnabled = true   // enables the JDWP debug agent
    debugPort = 5005      // listens on 127.0.0.1:5005 (default)
    debugSuspend = false  // set to true to wait for debugger before starting
}
```

Start the server with `./gradlew runServer`, then in IntelliJ go to **Run → Attach to Process** and select port 5005 (or use a Remote JVM Debug run configuration pointing at `localhost:5005`).

Set `debugSuspend = true` if you need breakpoints to fire during server initialisation — the JVM will pause until a debugger connects.

**HotSwap with JetBrains Runtime**

For full class redefinition (method bodies, fields, and more) you need a JetBrains Runtime (JBR) with DCEVM support:

```kotlin
server {
    debugEnabled = true
    requireDcevm = true        // fails fast if the JVM doesn't support enhanced class redefinition
    useHotswapAgent = true     // injects HotSwap Agent as -javaagent; auto-downloaded on first use
    jbrHome = "/path/to/jbr"  // path to your JBR installation
    // hotswapAgentPath.set(file("/path/to/hotswap-agent.jar"))  // optional: override auto-download
}
```

`useHotswapAgent` downloads `hotswap-agent-1.4.2.jar` automatically the first time it is needed. Set `hotswapAgentPath` to point at your own copy if you prefer not to use the auto-download.

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

### `server { }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `serverDir` | `DirectoryProperty` | `run/` | Working directory for the server process |
| `jvmArgs` | `ListProperty<String>` | `[]` | Extra JVM arguments passed to the server |
| `debugEnabled` | `Boolean` | `false` | Enables the JDWP debug agent |
| `debugPort` | `Int` | `5005` | Port the debug agent listens on (`127.0.0.1` only) |
| `debugSuspend` | `Boolean` | `false` | If `true`, JVM waits for a debugger to attach before starting |
| `sessionAuth` | `Boolean` | `true` | Creates a game session from the `auth:server` token and passes `--session-token`/`--identity-token` so no in-server `/auth` is needed. Fail-soft: on failure the server starts unauthenticated |
| `requireDcevm` | `Boolean` | `false` | Validates that the JVM supports `-XX:+AllowEnhancedClassRedefinition` before launch |
| `useHotswapAgent` | `Boolean` | `false` | Injects HotSwap Agent (`hotswap-agent-1.4.2.jar`) as `-javaagent`; auto-downloaded on first use |
| `hotswapAgentPath` | `RegularFileProperty` | — | Explicit path to a `hotswap-agent.jar`; overrides auto-download |
| `jbrHome` | `String` | — | Path to a JetBrains Runtime installation to use instead of the current JVM |

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

## Monorepo / Workspace

For monorepos with multiple mod subprojects, apply `gg.ginco.hygradle.workspace` to the **root project**. It propagates `serverVersion` to every subproject that has `gg.ginco.hygradle` applied and provides a `runAllMods` task that builds all mods, deploys them to a shared `mods/` directory, and starts a single dev server.

### Setup

**`settings.gradle.kts`** (root)
```kotlin
pluginManagement {
    repositories { gradlePluginPortal() }
}

plugins {
    id("gg.ginco.hygradle.settings") version "0.3.1"
}

rootProject.name = "my-workspace"
include(":mod-alpha", ":mod-beta")
```

**`build.gradle.kts`** (root)
```kotlin
plugins {
    id("gg.ginco.hygradle.workspace") version "0.3.1"
}

hytaleWorkspace {
    serverVersion = "0.6.2"  // propagated to all mod subprojects
}
```

**`mod-alpha/build.gradle.kts`** (subproject)
```kotlin
plugins {
    id("gg.ginco.hygradle")
    kotlin("jvm") version "2.3.21"
}

version = "1.0.0"

hytale {
    group = "MyGroup"
    name = "ModAlpha"
    mainClass = "com.example.alpha.AlphaPlugin"
    // serverVersion is inherited from hytaleWorkspace; override here if needed
}
```

### Running all mods

```bash
./gradlew runAllMods
```

Builds every mod subproject and starts a shared dev server with all mods loaded from a single `mods/` directory.

### Selective mod inclusion

By default all subprojects with `gg.ginco.hygradle` applied are included. To restrict to specific subprojects:

```kotlin
hytaleWorkspace {
    serverVersion = "0.6.2"
    modProjects = listOf(":mod-alpha", ":mod-beta")
}
```

### `hytaleWorkspace { }` reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `serverVersion` | `String` | — | Shared server version propagated to all mod subprojects. **Required.** |
| `modProjects` | `List<String>` | `[]` (auto-detect) | Explicit subproject paths to include |
| `hostProject` | `String` | — | Subproject excluded from `runAllMods` jar deployment |
| `serverDir` | `Directory` | `./run` | Shared server run directory |
| `jvmArgs` | `List<String>` | `["-Xmx4G", "-Xms1G"]` | JVM args for the dev server |

## License

MIT
