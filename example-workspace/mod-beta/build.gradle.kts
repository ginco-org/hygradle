plugins {
    id("gg.ginco.hygradle")
    kotlin("jvm") version "2.3.21"
}

version = "1.0.0"

hytale {
    group = "Example"
    name = "ModBeta"
    mainClass = "com.example.beta.BetaPlugin"
    // serverVersion inherited from hytaleWorkspace
}
