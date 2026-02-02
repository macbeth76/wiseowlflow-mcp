plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

val ktorVersion: String by project
val exposedVersion: String by project

application {
    mainClass.set("com.wiseowlflow.ApplicationKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence"))
    implementation(project(":session"))
    implementation(project(":mcp-proxy"))
    implementation(project(":workflow-engine"))
    implementation(project(":auth"))
    implementation(project(":billing"))
    implementation(project(":api"))

    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Exposed (for Database type)
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")

    // Redis (for RedisAsyncCommands type)
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
}

tasks.shadowJar {
    archiveBaseName.set("app")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
}
