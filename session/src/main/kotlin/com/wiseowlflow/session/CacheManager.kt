package com.wiseowlflow.session

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

class CacheManager(
    private val redis: RedisAsyncCommands<String, String>,
    private val defaultTtlSeconds: Long = 3600
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(key: String): String? {
        return redis.get(key).await()
    }

    suspend fun set(key: String, value: String, ttlSeconds: Long? = null) {
        val ttl = ttlSeconds ?: defaultTtlSeconds
        redis.setex(key, ttl, value).await()
    }

    suspend fun delete(key: String): Boolean {
        return redis.del(key).await() > 0
    }

    suspend fun exists(key: String): Boolean {
        return redis.exists(key).await() > 0
    }

    suspend fun increment(key: String): Long {
        return redis.incr(key).await()
    }

    suspend fun incrementWithExpiry(key: String, ttlSeconds: Long): Long {
        val value = redis.incr(key).await()
        if (value == 1L) {
            redis.expire(key, ttlSeconds).await()
        }
        return value
    }

    suspend fun setNx(key: String, value: String, ttlSeconds: Long): Boolean {
        val result = redis.setnx(key, value).await()
        if (result) {
            redis.expire(key, ttlSeconds).await()
        }
        return result
    }

    // Rate limiting helper
    suspend fun checkRateLimit(key: String, limit: Int, windowSeconds: Long): RateLimitResult {
        val count = incrementWithExpiry("ratelimit:$key", windowSeconds)
        val remaining = (limit - count).coerceAtLeast(0).toInt()
        val ttl = redis.ttl("ratelimit:$key").await()

        return RateLimitResult(
            allowed = count <= limit,
            remaining = remaining,
            resetAt = System.currentTimeMillis() + (ttl * 1000)
        )
    }
}

data class RateLimitResult(
    val allowed: Boolean,
    val remaining: Int,
    val resetAt: Long
)
