plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.19.4" apply false
    id("xyz.jpenilla.run-paper") version "3.1.0" apply false
}

allprojects {
    group = property("maven_group") as String
    // For example 0.1.0-alpha.1+26.3: SemVer build metadata names the Minecraft version.
    version = "${property("mod_version")}+${property("minecraft_version")}"
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Every jar we ship carries our license and the third-party notices.
    tasks.named<Jar>("jar") {
        from(rootProject.file("LICENSE")) {
            into("META-INF")
            rename { "LICENSE_shulker_stickers.txt" }
        }
        from(rootProject.file("THIRD_PARTY_NOTICES.md")) { into("META-INF") }
    }
}
