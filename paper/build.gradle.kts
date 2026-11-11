import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("xyz.jpenilla.run-paper")
}

base {
    archivesName = "shulker-stickers-paper"
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
}

val shade = configurations.create("shade")

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
    implementation(project(":core"))
    shade(project(":core"))
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
    minecraftVersion(property("minecraft_version") as String)
}

runPaper.folia.registerTask()

// Canvas has no downloads API in run-paper, so fetch its jar ourselves.
val canvasJar = layout.buildDirectory.file("canvas/canvas-build.${property("canvas_build")}.jar")
val downloadCanvas = tasks.register("downloadCanvas") {
    val build = property("canvas_build") as String
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
    runJar(canvasJar)
    pluginJars(tasks.jar.flatMap { it.archiveFile })
    runDirectory = layout.projectDirectory.dir("run-canvas")
}
