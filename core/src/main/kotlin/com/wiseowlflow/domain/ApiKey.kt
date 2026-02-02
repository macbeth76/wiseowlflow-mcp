package com.wiseowlflow.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ApiKey(
    val id: String,
    val userId: String,
    val name: String,
    val keyHash: String,
    val keyPrefix: String,
    val scopes: Set<ApiKeyScope>,
    val lastUsedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val createdAt: Instant,
    val revokedAt: Instant? = null
) {
    val isRevoked: Boolean get() = revokedAt != null
    val isExpired: Boolean get() = expiresAt?.let { it < kotlinx.datetime.Clock.System.now() } ?: false
    val isValid: Boolean get() = !isRevoked && !isExpired
}

@Serializable
enum class ApiKeyScope {
    WORKFLOWS_READ,
    WORKFLOWS_WRITE,
    WORKFLOWS_EXECUTE,
    MCP_SERVERS_READ,
    MCP_SERVERS_WRITE,
    MCP_TOOLS_EXECUTE,
    USER_READ,
    USER_WRITE,
    BILLING_READ,
    ADMIN
}

object ApiKeyScopes {
    val READ_ONLY = setOf(
        ApiKeyScope.WORKFLOWS_READ,
        ApiKeyScope.MCP_SERVERS_READ,
        ApiKeyScope.USER_READ
    )

    val EXECUTE_ONLY = setOf(
        ApiKeyScope.WORKFLOWS_EXECUTE,
        ApiKeyScope.MCP_TOOLS_EXECUTE
    )

    val FULL_ACCESS = ApiKeyScope.entries.toSet() - ApiKeyScope.ADMIN
}
