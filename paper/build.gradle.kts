import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("xyz.jpenilla.run-paper")
}

base {
    archivesName = "shulker-stickers-paper"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
    // Claim plugin APIs, compile-only (owner decision 3). Each is used only when installed.
    maven("https://maven.enginehub.org/repo/") {
        name = "EngineHub"
        content { includeGroup("com.sk89q.worldguard"); includeGroup("com.sk89q.worldedit"); includeGroupByRegex("org[.]enginehub.*") }
    }
    maven("https://repo.glaremasters.me/repository/towny/") {
        name = "Towny"
        content { includeGroup("com.palmergames.bukkit.towny") }
    }
    maven("https://jitpack.io") {
        name = "JitPack"
        content { includeGroup("com.github.angeschossen"); includeGroup("com.github.GriefPrevention") }
    }
}

val claimApis = listOf(
    "com.sk89q.worldguard:worldguard-core:${property("worldguard_version")}",
    "com.sk89q.worldguard:worldguard-bukkit:${property("worldguard_version")}",
    "com.sk89q.worldedit:worldedit-core:${property("worldedit_version")}",
    "com.sk89q.worldedit:worldedit-bukkit:${property("worldedit_version")}",
    "com.palmergames.bukkit.towny:towny:${property("towny_version")}",
    "com.github.angeschossen:LandsAPI:${property("lands_api_version")}",
    "com.github.GriefPrevention:GriefPrevention:${property("griefprevention_version")}",
)

val shade = configurations.create("shade")

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    implementation(project(":core"))
    shade(project(":core"))
    claimApis.forEach { compileOnly(it) { isTransitive = false } }

    testImplementation("io.papermc.paper:paper-api:${property("paper_api_version")}")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.3:${property("mockbukkit_version")}")
    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    // Bundle core's classes into the plugin jar; Paper has no jar-in-jar.
    dependsOn(shade)
    from(shade.map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.runServer {
    minecraftVersion(project.property("minecraft_version") as String)
}

runPaper.folia.registerTask()

// Canvas has no downloads API in run-paper, so fetch its jar ourselves.
val canvasJar = layout.buildDirectory.file("canvas/canvas-build.${property("canvas_build")}.jar")
val downloadCanvas = tasks.register("downloadCanvas") {
    val build = project.property("canvas_build") as String
    val target = canvasJar
    outputs.file(target)
    doLast {
        val file = target.get().asFile
        file.parentFile.mkdirs()
        uri("https://jenkins.canvasmc.io/job/Canvas/$build/artifact/canvas-server/build/libs/canvas-build.$build.jar")
            .toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
    }
}

tasks.register<RunServer>("runCanvas") {
    group = "run paper"
    description = "Runs a Canvas (Folia fork) server with this plugin."
    dependsOn(downloadCanvas)
    minecraftVersion(project.property("minecraft_version") as String)
    runJar(canvasJar)
    pluginJars(tasks.jar.flatMap { it.archiveFile })
    runDirectory = layout.projectDirectory.dir("run-canvas")
}

// Paper with real claim plugins (owner decision 3): WorldEdit + WorldGuard and GriefPrevention,
// the 26.3 builds from Modrinth. With -Pselftest it also checks our hooks load and call them.
tasks.register<RunServer>("runClaims") {
    group = "run paper"
    description = "Runs Paper with WorldGuard and GriefPrevention installed."
    minecraftVersion(project.property("minecraft_version") as String)
    pluginJars(tasks.jar.flatMap { it.archiveFile })
    downloadPlugins {
        url("https://cdn.modrinth.com/data/1u6JkXh5/versions/YFnqiUlL/worldedit-bukkit-7.4.6.jar")
        url("https://cdn.modrinth.com/data/DKY9btbd/versions/fiHLM6oY/worldguard-bukkit-7.0.21.jar")
        url("https://cdn.modrinth.com/data/O4o4mKaq/versions/MtwPHMAS/GriefPrevention.jar")
    }
    runDirectory = layout.projectDirectory.dir("run-claims")
    jvmArgs("-Dshulkerstickers.selftest.claims=GriefPrevention,WorldGuard")
}

// `-Pselftest`: accept the EULA, run the spike self-test, then shut the server down.
if (providers.gradleProperty("selftest").isPresent) {
    tasks.withType<RunServer>().configureEach {
        jvmArgs(
            "-Dcom.mojang.eula.agree=true",
            "-Dshulkerstickers.selftest=true",
            "-Dshulkerstickers.selftest.stop=true",
        )
        if (providers.gradleProperty("nodatapack").isPresent) {
            jvmArgs("-Dshulkerstickers.selftest.nodatapack=true")
        }
    }
}

// `-Pperftest`: build a wall of 1,000 decorated boxes, log PERF timings, then shut down (AC-8).
if (providers.gradleProperty("perftest").isPresent) {
    tasks.withType<RunServer>().configureEach {
        jvmArgs(
            "-Dcom.mojang.eula.agree=true",
            "-Dshulkerstickers.perftest=true",
            "-Dshulkerstickers.selftest.stop=true",
        )
        if (providers.gradleProperty("plain").isPresent) {
            jvmArgs("-Dshulkerstickers.perftest.plain=true")
        }
        if (providers.gradleProperty("nonames").isPresent) {
            jvmArgs("-Dshulkerstickers.perftest.nonames=true")
        }
        // Simulate a player next to every box (displays exist only near players).
        if (providers.gradleProperty("nearby").isPresent) {
            jvmArgs("-Dshulkerstickers.perftest.nearby=true")
        }
        // Diagnostic: decorated boxes the plugin never tracks (separates data cost from ours).
        if (providers.gradleProperty("notrack").isPresent) {
            jvmArgs("-Dshulkerstickers.perftest.notrack=true")
        }
    }
}
