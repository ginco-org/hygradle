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
    id("gg.ginco.hygradle.settings") version "0.7.0"
}
```

**`build.gradle.kts`**
```kotlin
plugins {
    id("gg.ginco.hygradle") version "0.7.0"
    kotlin("jvm") version "2.3.21"  // optional
}
```

## Usage

```kotlin
hytale {
    group = "MyGroup"                          // required
    mainClass = "com.example.myplugin.Plugin"  // required
    serverVersion = "0.7.2"                    // required

    name = "MyPlugin"
    description = "Does cool things"
    website = "https://example.com"
    author("jane doe", email = "jane@example.com")

    // Bundle runtime dependencies (Kotlin stdlib, etc.) into the plugin jar.
    // Required if your plugin uses Kotlin or any library not provided by the server.
    bundleDependencies = true
}
```

### Shared framework packaging (`bundleExclude` / `bundleOnly`)

On multi-plugin servers a shared framework plugin usually lives in `mods/` once, and consumer
plugins resolve its classes through the `PluginBridgeClassLoader` (hard manifest dependencies
are searched first). Bundling the framework jar into every consumer would shadow the shared
framework — `PluginClassLoader` is child-first, so bundled classes win and you get split static
state and duplicate registrations.

With `bundleDependencies = true` you can keep specific Maven dependencies out of the jar:

```kotlin
hytale {
    bundleDependencies = true
    bundleExclude("gg.ginco:hytale-minigame-framework")  // the shared framework stays in mods/ once
    bundleExclude("gg.ginco:hytale-codec-.*")            // patterns are full-match regexes
}
```

Or flip it around with a positive list — bundle *only* what matches:

```kotlin
hytale {
    bundleDependencies = true
    bundleOnly("org.jetbrains.kotlin:kotlin-stdlib")
}
```

Patterns are full-match regular expressions, matched against each runtime dependency's
coordinates: `group:artifact`, `group:artifact:version`, the bare `artifact` name, the project
path for project dependencies (`:lib`), or the file name for plain file dependencies. If both
are used, `bundleExclude` wins over `bundleOnly`. A consumer typically also declares the
framework as a hard manifest dependency so the bridge class loader resolves its classes:

```kotlin
hytale {
    depends("Ginco:MinigameFramework", ">=1.0.0")
}
```

### Tasks

| Task | Description |
|------|-------------|
| `generatePluginManifest` | Generates `manifest.json` from the `hytale { }` block |
| `jar` | Builds the plugin jar with the manifest included |
| `provisionServer` | Downloads the Hytale server files into the run directory — without starting a server |
| `runServer` | Deploys your plugin and starts a dev server (depends on `provisionServer`) |

### Provision server files

```bash
./gradlew provisionServer
```

Downloads the Hytale server files (`HytaleServer.jar`, `HytaleServer.aot.config`, `Assets.zip`) into the run directory **without starting a server**. It is idempotent: if the files are already present for the requested version, it is a no-op. This is the task other build plugins should depend on to ensure the server files exist — e.g. to prepare a test harness or CI environment:

```kotlin
tasks.named("myTestTask") {
    dependsOn("provisionServer")
}
```

`runServer` depends on `provisionServer` automatically, so the server files are always in place before launch.

When the authenticated full build is unavailable, hygradle falls back to a JAR-only download from Maven (no `Assets.zip`/`HytaleServer.aot.config`) and remembers the fallback so it doesn't retry the failing full build on every run. Delete `~/.gradle/caches/hygradle/server/<version>/` to retry the full build.

Downloads always go to a temp file first and are moved into place afterwards, so an interrupted download can never leave a truncated server file behind.

### Run server

```bash
./gradlew runServer
```

On first run, the server files (`HytaleServer.jar`, `HytaleServer.aot.config`, `Assets.zip`) are downloaded to `~/.gradle/caches/hygradle/server/<version>/`. The `run/` directory is used only as the server's working directory (mods, logs, config) — `mods/` is rebuilt from scratch on every launch, so stale jars from previous builds are removed. Subsequent runs skip the download if the files are already cached for the requested version.

If your server version requires authentication, the task will guide you through a one-time device code login. Credentials are cached locally.

By default, the server also starts fully authenticated: hygradle exchanges a one-time `auth:server` login (client `hytale-server`, cached separately from the download token) for a **game session** and passes `--session-token` / `--identity-token` to the server process — no interactive `/auth` in the server console. Set `server { sessionAuth = false }` to disable this and authenticate manually instead.

> Tokens are passed on the server process command line, so they are visible in the
> process list (`ps`) to other local users of the machine. Set
> `server { sessionAuth = false }` if that matters in your environment.

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
| `bundleExclude()` | `vararg String` | — | Deps to **skip** when bundling: `group:artifact` or full-match regex (e.g. `"gg.ginco:hytale-codec-.*"`). Wins over `bundleOnly()` |
| `bundleOnly()` | `vararg String` | — | Positive list: when non-empty, bundle **only** matching deps |
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
        serverVersion = "0.7.2"   // optional — defaults to the parent hytale { serverVersion }
        author("jane doe")
    }
}
```

Each `subPlugin { }` block emits a `SubPlugins` manifest entry. The server inherits
`Group`/`Version` from the parent plugin for these entries, but **not** `ServerVersion` —
without it the server logs `Plugin 'X' does not specify a target server version. ... This
will be a hard error in the future` for every sub-plugin. Hygradle therefore stamps
`ServerVersion` on every entry: an explicit `serverVersion` in the block wins, otherwise
the parent's `hytale { serverVersion }` is used.

## Monorepo / Workspace

For monorepos with multiple mod subprojects, apply `gg.ginco.hygradle.workspace` to the **root project**. It propagates `serverVersion` to every subproject that has `gg.ginco.hygradle` applied and provides a `runAllMods` task that builds all mods, deploys them to a shared `mods/` directory, and starts a single dev server.

### Setup

**`settings.gradle.kts`** (root)
```kotlin
pluginManagement {
    repositories { gradlePluginPortal() }
}

plugins {
    id("gg.ginco.hygradle.settings") version "0.7.0"
}

rootProject.name = "my-workspace"
include(":mod-alpha", ":mod-beta")
```

**`build.gradle.kts`** (root)
```kotlin
plugins {
    id("gg.ginco.hygradle.workspace") version "0.7.0"
}

hytaleWorkspace {
    serverVersion = "0.7.2"  // propagated to all mod subprojects
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

`runAllMods` supports the same debug and HotSwap options as `runServer`, configured on the root project via `hytaleWorkspace { }`.

### Selective mod inclusion

By default all subprojects with `gg.ginco.hygradle` applied are included. To restrict to specific subprojects:

```kotlin
hytaleWorkspace {
    serverVersion = "0.7.2"
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
| `sessionAuth` | `Boolean` | `true` | Create a game session and pass `--session-token`/`--identity-token` on launch |
| `debugEnabled` / `debugPort` / `debugSuspend` | `Boolean` / `Int` / `Boolean` | `false` / `5005` / `false` | Debugger attachment — same semantics as `server { }` |
| `requireDcevm` / `useHotswapAgent` / `hotswapAgentPath` / `jbrHome` | `Boolean` / `Boolean` / `RegularFile` / `String` | `false` / `false` / — / — | HotSwap support — same semantics as `server { }` |

## License

MIT
