plugins {
    id("net.fabricmc.fabric-loom")
}

base {
    archivesName = "shulker-stickers-fabric"
}

repositories {
    maven("https://maven.nucleoid.xyz") { name = "Nucleoid" }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    implementation("eu.pb4:polymer-virtual-entity:${property("polymer_version")}")
    include("eu.pb4:polymer-virtual-entity:${property("polymer_version")}")

    implementation(project(":core"))
    include(project(":core"))

    // Optional integrations: used only when the server has them installed (not bundled).
    compileOnly("me.lucko:fabric-permissions-api:0.7.0")
    compileOnly("eu.pb4:common-protection-api:2.0.0")
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "shulker_stickers_test"
        enableClientGameTests = false
        eula = true
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to project.property("minecraft_version"),
        "fabric_loader_version" to project.property("fabric_loader_version"),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

// `-Pperftest`: also run the 1,000-box performance GameTest (AC-8). Off by default because it is slow.
if (providers.gradleProperty("perftest").isPresent) {
    tasks.matching { it.name == "runGameTest" }.configureEach {
        (this as JavaExec).jvmArgs("-Dshulkerstickers.perftest=true")
    }
}
