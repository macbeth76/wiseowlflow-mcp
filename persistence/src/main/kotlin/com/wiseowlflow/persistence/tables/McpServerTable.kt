package com.wiseowlflow.persistence.tables

import com.wiseowlflow.domain.McpHealthStatus
import com.wiseowlflow.domain.McpTransportType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.serialization.json.Json

object McpServerTable : Table("mcp_servers") {
    private val json = Json { ignoreUnknownKeys = true }

    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UserTable.id).index()
    val name = varchar("name", 100)
    val displayName = varchar("display_name", 255).nullable()
    val description = text("description").nullable()
    val transportType = enumerationByName<McpTransportType>("transport_type", 20)
    val endpoint = varchar("endpoint", 500)
    val authConfig = jsonb<String>("auth_config", json).nullable()
    val enabled = bool("enabled").default(true)
    val healthStatus = enumerationByName<McpHealthStatus>("health_status", 20).default(McpHealthStatus.UNKNOWN)
    val lastHealthCheck = timestamp("last_health_check").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_mcp_servers_user_name", userId, name)
    }
}
