package com.wiseowlflow.api.dto

import com.wiseowlflow.domain.McpAuthConfig
import com.wiseowlflow.domain.McpHealthStatus
import com.wiseowlflow.domain.McpTransportType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CreateMcpServerRequest(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val transportType: McpTransportType,
    val endpoint: String,
    val authConfig: McpAuthConfig? = null,
    val enabled: Boolean = true
)

@Serializable
data class UpdateMcpServerRequest(
    val name: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val transportType: McpTransportType? = null,
    val endpoint: String? = null,
    val authConfig: McpAuthConfig? = null,
    val enabled: Boolean? = null
)

@Serializable
data class McpServerResponse(
    val id: String,
    val name: String,
    val displayName: String?,
    val description: String?,
    val transportType: McpTransportType,
    val endpoint: String,
    val enabled: Boolean,
    val healthStatus: McpHealthStatus,
    val lastHealthCheck: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class McpToolResponse(
    val serverName: String,
    val name: String,
    val qualifiedName: String,
    val description: String?,
    val inputSchema: JsonObject
)

@Serializable
data class McpResourceResponse(
    val serverName: String,
    val uri: String,
    val name: String,
    val description: String?,
    val mimeType: String?
)

@Serializable
data class McpPromptResponse(
    val serverName: String,
    val name: String,
    val qualifiedName: String,
    val description: String?,
    val arguments: List<McpPromptArgumentResponse>
)

@Serializable
data class McpPromptArgumentResponse(
    val name: String,
    val description: String?,
    val required: Boolean
)

@Serializable
data class McpServerListResponse(
    val servers: List<McpServerResponse>,
    val total: Int
)

@Serializable
data class McpToolListResponse(
    val tools: List<McpToolResponse>,
    val total: Int
)
