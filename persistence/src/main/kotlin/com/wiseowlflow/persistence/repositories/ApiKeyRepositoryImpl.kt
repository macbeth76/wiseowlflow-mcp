package com.wiseowlflow.persistence.repositories

import com.wiseowlflow.domain.ApiKey
import com.wiseowlflow.domain.ApiKeyScope
import com.wiseowlflow.persistence.dbQuery
import com.wiseowlflow.persistence.tables.ApiKeyTable
import com.wiseowlflow.ports.ApiKeyRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ApiKeyRepositoryImpl : ApiKeyRepository {

    override suspend fun findById(id: String): ApiKey? = dbQuery {
        ApiKeyTable.selectAll()
            .where { ApiKeyTable.id eq id }
            .map { it.toApiKey() }
            .singleOrNull()
    }

    override suspend fun findByKeyHash(keyHash: String): ApiKey? = dbQuery {
        ApiKeyTable.selectAll()
            .where { ApiKeyTable.keyHash eq keyHash }
            .map { it.toApiKey() }
            .singleOrNull()
    }

    override suspend fun findByUserId(userId: String): List<ApiKey> = dbQuery {
        ApiKeyTable.selectAll()
            .where { ApiKeyTable.userId eq userId }
            .orderBy(ApiKeyTable.createdAt, SortOrder.DESC)
            .map { it.toApiKey() }
    }

    override suspend fun findActiveByUserId(userId: String): List<ApiKey> = dbQuery {
        val now = Clock.System.now()
        ApiKeyTable.selectAll()
            .where {
                (ApiKeyTable.userId eq userId) and
                    (ApiKeyTable.revokedAt.isNull()) and
                    ((ApiKeyTable.expiresAt.isNull()) or (ApiKeyTable.expiresAt greater now))
            }
            .orderBy(ApiKeyTable.createdAt, SortOrder.DESC)
            .map { it.toApiKey() }
    }

    override suspend fun create(apiKey: ApiKey): ApiKey = dbQuery {
        ApiKeyTable.insert {
            it[id] = apiKey.id
            it[userId] = apiKey.userId
            it[name] = apiKey.name
            it[keyHash] = apiKey.keyHash
            it[keyPrefix] = apiKey.keyPrefix
            it[scopes] = apiKey.scopes.joinToString(",") { scope -> scope.name }
            it[lastUsedAt] = apiKey.lastUsedAt
            it[expiresAt] = apiKey.expiresAt
            it[createdAt] = apiKey.createdAt
            it[revokedAt] = apiKey.revokedAt
        }
        apiKey
    }

    override suspend fun updateLastUsed(id: String, usedAt: Instant): Boolean = dbQuery {
        ApiKeyTable.update({ ApiKeyTable.id eq id }) {
            it[lastUsedAt] = usedAt
        } > 0
    }

    override suspend fun revoke(id: String, revokedAt: Instant): Boolean = dbQuery {
        ApiKeyTable.update({ ApiKeyTable.id eq id }) {
            it[ApiKeyTable.revokedAt] = revokedAt
        } > 0
    }

    override suspend fun delete(id: String): Boolean = dbQuery {
        ApiKeyTable.deleteWhere { ApiKeyTable.id eq id } > 0
    }

    private fun ResultRow.toApiKey(): ApiKey {
        val scopesStr = this[ApiKeyTable.scopes]
        val scopes = if (scopesStr.isBlank()) {
            emptySet()
        } else {
            scopesStr.split(",").map { ApiKeyScope.valueOf(it.trim()) }.toSet()
        }

        return ApiKey(
            id = this[ApiKeyTable.id],
            userId = this[ApiKeyTable.userId],
            name = this[ApiKeyTable.name],
            keyHash = this[ApiKeyTable.keyHash],
            keyPrefix = this[ApiKeyTable.keyPrefix],
            scopes = scopes,
            lastUsedAt = this[ApiKeyTable.lastUsedAt],
            expiresAt = this[ApiKeyTable.expiresAt],
            createdAt = this[ApiKeyTable.createdAt],
            revokedAt = this[ApiKeyTable.revokedAt]
        )
    }
}
