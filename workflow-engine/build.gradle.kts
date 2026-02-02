plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val ktorVersion: String by project
val kstateMachineVersion: String by project
val mcpSdkVersion: String by project

dependencies {
    implementation(project(":core"))
    implementation(project(":mcp-proxy"))

    // MCP SDK (needed for CallToolResult type)
    implementation("io.modelcontextprotocol:kotlin-sdk:$mcpSdkVersion")

    // State Machine
    implementation("io.github.nsk90:kstatemachine:$kstateMachineVersion")
    implementation("io.github.nsk90:kstatemachine-coroutines:$kstateMachineVersion")

    // Ktor client (also used for custom Ollama client)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // YAML parsing
    implementation("com.charleskorn.kaml:kaml:0.67.0")
}
