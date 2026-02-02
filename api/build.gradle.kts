plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val ktorVersion: String by project

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence"))
    implementation(project(":session"))
    implementation(project(":mcp-proxy"))
    implementation(project(":workflow-engine"))
    implementation(project(":auth"))
    implementation(project(":billing"))

    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // MCP SDK for server
    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.3")
}
