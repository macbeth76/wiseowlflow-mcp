package com.wiseowlflow.session

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.StringCodec

private val logger = KotlinLogging.logger {}

class RedisConfig(
    private val redisUrl: String
) {
    private lateinit var client: RedisClient
    private lateinit var connection: StatefulRedisConnection<String, String>

    fun connect(): RedisAsyncCommands<String, String> {
        logger.info { "Connecting to Redis..." }

        val uri = RedisURI.create(redisUrl)
        client = RedisClient.create(uri)
        connection = client.connect(StringCodec.UTF8)

        logger.info { "Redis connected successfully" }
        return connection.async()
    }

    fun getConnection(): StatefulRedisConnection<String, String> = connection

    fun close() {
        if (::connection.isInitialized) {
            connection.close()
        }
        if (::client.isInitialized) {
            client.shutdown()
            logger.info { "Redis connection closed" }
        }
    }

    companion object {
        fun fromEnvironment(): RedisConfig {
            val redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379"
            return RedisConfig(redisUrl)
        }
    }
}
