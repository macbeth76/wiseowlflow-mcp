package com.wiseowlflow.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpServer(
    val id: String,
    val userId: String,
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val transportType: McpTransportType,
    val endpoint: String,
    val authConfig: McpAuthConfig? = null,
    val enabled: Boolean = true,
    val healthStatus: McpHealthStatus = McpHealthStatus.UNKNOWN,
    val lastHealthCheck: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
enum class McpTransportType {
    STDIO,
    SSE,
    STREAMABLE_HTTP
}

@Serializable
sealed class McpAuthConfig {
    @Serializable
    data class None(val placeholder: Unit = Unit) : McpAuthConfig()

    @Serializable
    data class Bearer(val token: String) : McpAuthConfig()

    @Serializable
    data class ApiKey(val key: String, val header: String = "X-API-Key") : McpAuthConfig()

    @Serializable
    data class Basic(val username: String, val password: String) : McpAuthConfig()
}

@Serializable
enum class McpHealthStatus {
    HEALTHY,
    UNHEALTHY,
    UNKNOWN
}

@Serializable
data class McpTool(
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject
) {
    val qualifiedName: String get() = "$serverName.$name"
}

@Serializable
data class McpResource(
    val serverId: String,
    val serverName: String,
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null
)

@Serializable
data class McpPrompt(
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String? = null,
    val arguments: List<McpPromptArgument> = emptyList()
) {
    val qualifiedName: String get() = "$serverName.$name"
}

@Serializable
data class McpPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = false
)
