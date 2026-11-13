plugins {
    `java-library`
}

dependencies {
    // Both Minecraft and Paper ship Gson, so core only compiles against it.
    compileOnly("com.google.code.gson:gson:${property("gson_version")}")
    // Both ship JOML too (Paper 1.10.8, Minecraft 1.10.9); core compiles against the older one.
    compileOnly("org.joml:joml:${property("joml_version")}")

    testImplementation("com.google.code.gson:gson:${property("gson_version")}")
    testImplementation("org.joml:joml:${property("joml_version")}")
    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
