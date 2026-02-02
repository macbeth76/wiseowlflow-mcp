package com.wiseowlflow.persistence.repositories

import com.wiseowlflow.domain.*
import com.wiseowlflow.persistence.dbQuery
import com.wiseowlflow.persistence.tables.McpServerTable
import com.wiseowlflow.ports.McpServerRepository
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class McpServerRepositoryImpl : McpServerRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun findById(id: String): McpServer? = dbQuery {
        McpServerTable.selectAll()
            .where { McpServerTable.id eq id }
            .map { it.toMcpServer() }
            .singleOrNull()
    }

    override suspend fun findByUserId(userId: String): List<McpServer> = dbQuery {
        McpServerTable.selectAll()
            .where { McpServerTable.userId eq userId }
            .orderBy(McpServerTable.name)
            .map { it.toMcpServer() }
    }

    override suspend fun findByUserIdAndName(userId: String, name: String): McpServer? = dbQuery {
        McpServerTable.selectAll()
            .where { (McpServerTable.userId eq userId) and (McpServerTable.name eq name) }
            .map { it.toMcpServer() }
            .singleOrNull()
    }

    override suspend fun findAllEnabled(): List<McpServer> = dbQuery {
        McpServerTable.selectAll()
            .where { McpServerTable.enabled eq true }
            .map { it.toMcpServer() }
    }

    override suspend fun create(server: McpServer): McpServer = dbQuery {
        McpServerTable.insert {
            it[id] = server.id
            it[userId] = server.userId
            it[name] = server.name
            it[displayName] = server.displayName
            it[description] = server.description
            it[transportType] = server.transportType
            it[endpoint] = server.endpoint
            it[authConfig] = server.authConfig?.let { auth -> json.encodeToString(McpAuthConfig.serializer(), auth) }
            it[enabled] = server.enabled
            it[healthStatus] = server.healthStatus
            it[lastHealthCheck] = server.lastHealthCheck
            it[createdAt] = server.createdAt
            it[updatedAt] = server.updatedAt
        }
        server
    }

    override suspend fun update(server: McpServer): McpServer = dbQuery {
        McpServerTable.update({ McpServerTable.id eq server.id }) {
            it[name] = server.name
            it[displayName] = server.displayName
            it[description] = server.description
            it[transportType] = server.transportType
            it[endpoint] = server.endpoint
            it[authConfig] = server.authConfig?.let { auth -> json.encodeToString(McpAuthConfig.serializer(), auth) }
            it[enabled] = server.enabled
            it[updatedAt] = server.updatedAt
        }
        server
    }

    override suspend fun updateHealthStatus(id: String, status: McpHealthStatus, checkedAt: Instant): Boolean = dbQuery {
        McpServerTable.update({ McpServerTable.id eq id }) {
            it[healthStatus] = status
            it[lastHealthCheck] = checkedAt
        } > 0
    }

    override suspend fun delete(id: String): Boolean = dbQuery {
        McpServerTable.deleteWhere { McpServerTable.id eq id } > 0
    }

    override suspend fun countByUserId(userId: String): Int = dbQuery {
        McpServerTable.selectAll()
            .where { McpServerTable.userId eq userId }
            .count().toInt()
    }

    private fun ResultRow.toMcpServer(): McpServer {
        val authConfigStr = this[McpServerTable.authConfig]
        val authConfig = authConfigStr?.let {
            json.decodeFromString(McpAuthConfig.serializer(), it)
        }

        return McpServer(
            id = this[McpServerTable.id],
            userId = this[McpServerTable.userId],
            name = this[McpServerTable.name],
            displayName = this[McpServerTable.displayName],
            description = this[McpServerTable.description],
            transportType = this[McpServerTable.transportType],
            endpoint = this[McpServerTable.endpoint],
            authConfig = authConfig,
            enabled = this[McpServerTable.enabled],
            healthStatus = this[McpServerTable.healthStatus],
            lastHealthCheck = this[McpServerTable.lastHealthCheck],
            createdAt = this[McpServerTable.createdAt],
            updatedAt = this[McpServerTable.updatedAt]
        )
    }
}
