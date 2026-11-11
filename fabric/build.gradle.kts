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
