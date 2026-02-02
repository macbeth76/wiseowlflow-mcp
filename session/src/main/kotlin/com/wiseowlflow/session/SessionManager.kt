package com.wiseowlflow.session

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Serializable
data class Session(
    val id: String,
    val userId: String,
    val data: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long
)

class SessionManager(
    private val redis: RedisAsyncCommands<String, String>,
    private val sessionTtlSeconds: Long = 86400 // 24 hours
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val keyPrefix = "session:"

    suspend fun create(userId: String, data: Map<String, String> = emptyMap()): Session {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = Session(
            id = sessionId,
            userId = userId,
            data = data,
            createdAt = now,
            expiresAt = now + (sessionTtlSeconds * 1000)
        )

        val key = "$keyPrefix$sessionId"
        val value = json.encodeToString(session)

        redis.setex(key, sessionTtlSeconds, value).await()
        logger.debug { "Created session $sessionId for user $userId" }

        return session
    }

    suspend fun get(sessionId: String): Session? {
        val key = "$keyPrefix$sessionId"
        val value = redis.get(key).await() ?: return null

        return try {
            json.decodeFromString<Session>(value)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to deserialize session $sessionId" }
            null
        }
    }

    suspend fun update(sessionId: String, data: Map<String, String>): Session? {
        val session = get(sessionId) ?: return null
        val updated = session.copy(data = session.data + data)

        val key = "$keyPrefix$sessionId"
        val value = json.encodeToString(updated)
        val ttl = redis.ttl(key).await()

        if (ttl > 0) {
            redis.setex(key, ttl, value).await()
        }

        return updated
    }

    suspend fun refresh(sessionId: String): Boolean {
        val key = "$keyPrefix$sessionId"
        val exists = redis.exists(key).await() > 0

        if (exists) {
            redis.expire(key, sessionTtlSeconds).await()
            return true
        }

        return false
    }

    suspend fun delete(sessionId: String): Boolean {
        val key = "$keyPrefix$sessionId"
        val deleted = redis.del(key).await()
        return deleted > 0
    }

    suspend fun deleteAllForUser(userId: String) {
        // Note: This is a simplified implementation
        // In production, you'd want to maintain a set of session IDs per user
        logger.warn { "deleteAllForUser not fully implemented - requires session tracking per user" }
    }
}
