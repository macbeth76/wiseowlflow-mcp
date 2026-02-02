plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val ktorVersion: String by project
val mcpSdkVersion: String by project

dependencies {
    implementation(project(":core"))

    // MCP SDK
    implementation("io.modelcontextprotocol:kotlin-sdk:$mcpSdkVersion")

    // kotlinx-io for stream handling
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.6.0")

    // Ktor client for HTTP transports
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
}
