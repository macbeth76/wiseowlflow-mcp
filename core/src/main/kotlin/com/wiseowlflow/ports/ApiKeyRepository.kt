package com.wiseowlflow.ports

import com.wiseowlflow.domain.ApiKey
import kotlinx.datetime.Instant

interface ApiKeyRepository {
    suspend fun findById(id: String): ApiKey?
    suspend fun findByKeyHash(keyHash: String): ApiKey?
    suspend fun findByUserId(userId: String): List<ApiKey>
    suspend fun findActiveByUserId(userId: String): List<ApiKey>
    suspend fun create(apiKey: ApiKey): ApiKey
    suspend fun updateLastUsed(id: String, usedAt: Instant): Boolean
    suspend fun revoke(id: String, revokedAt: Instant): Boolean
    suspend fun delete(id: String): Boolean
}
