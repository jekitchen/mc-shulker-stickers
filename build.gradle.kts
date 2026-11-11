plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.19.4" apply false
    id("xyz.jpenilla.run-paper") version "3.1.0" apply false
}

allprojects {
    group = property("maven_group") as String
    version = property("mod_version") as String
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
}
