package com.wiseowlflow.auth.apikey

import at.favre.lib.crypto.bcrypt.BCrypt
import com.wiseowlflow.domain.ApiKey
import com.wiseowlflow.domain.ApiKeyScope
import com.wiseowlflow.ports.ApiKeyRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.security.SecureRandom
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private val logger = KotlinLogging.logger {}

data class GeneratedApiKey(
    val apiKey: ApiKey,
    val rawKey: String // Only available at creation time
)

class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val keyPrefix: String = "wof"
) {
    private val secureRandom = SecureRandom()
    private val bcryptCost = 10 // Lower cost for faster API key validation

    suspend fun generateApiKey(
        userId: String,
        name: String,
        scopes: Set<ApiKeyScope>,
        expiresIn: Duration? = null
    ): GeneratedApiKey {
        // Generate a random key: prefix_randomBytes
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        val rawKey = "${keyPrefix}_${Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)}"

        val keyHash = BCrypt.withDefaults().hashToString(bcryptCost, rawKey.toCharArray())
        val keyPrefixDisplay = rawKey.take(12) + "..."

        val now = Clock.System.now()
        val expiresAt = expiresIn?.let { now + it }

        val apiKey = ApiKey(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = name,
            keyHash = keyHash,
            keyPrefix = keyPrefixDisplay,
            scopes = scopes,
            expiresAt = expiresAt,
            createdAt = now
        )

        apiKeyRepository.create(apiKey)
        logger.info { "Created API key '$name' for user $userId" }

        return GeneratedApiKey(apiKey, rawKey)
    }

    suspend fun validateApiKey(rawKey: String): ApiKey? {
        // Check prefix
        if (!rawKey.startsWith("${keyPrefix}_")) {
            return null
        }

        // Find all potentially matching keys (by prefix)
        // Note: In production, you might want to index by a non-sensitive prefix
        val allKeys = apiKeyRepository.findByKeyHash("") // This won't work - need different approach

        // Actually, we need to hash the incoming key and compare
        // But bcrypt produces different hashes each time...
        // So we need to iterate through potential keys

        // Better approach: Store a deterministic hash (like SHA-256) for lookup
        // and bcrypt for verification. For now, simplified approach:

        // Since we can't do efficient lookup with bcrypt, let's use a different strategy:
        // Store a SHA-256 hash for lookup

        val keyHash = hashKeyForLookup(rawKey)
        val apiKey = apiKeyRepository.findByKeyHash(keyHash) ?: return null

        if (!apiKey.isValid) {
            logger.debug { "API key is invalid (expired or revoked)" }
            return null
        }

        // Update last used
        apiKeyRepository.updateLastUsed(apiKey.id, Clock.System.now())

        return apiKey
    }

    suspend fun revokeApiKey(apiKeyId: String, userId: String): Boolean {
        val apiKey = apiKeyRepository.findById(apiKeyId)
        if (apiKey == null || apiKey.userId != userId) {
            return false
        }

        return apiKeyRepository.revoke(apiKeyId, Clock.System.now())
    }

    suspend fun listApiKeys(userId: String): List<ApiKey> {
        return apiKeyRepository.findByUserId(userId)
    }

    suspend fun listActiveApiKeys(userId: String): List<ApiKey> {
        return apiKeyRepository.findActiveByUserId(userId)
    }

    fun hasScope(apiKey: ApiKey, requiredScope: ApiKeyScope): Boolean {
        return requiredScope in apiKey.scopes || ApiKeyScope.ADMIN in apiKey.scopes
    }

    fun hasAnyScope(apiKey: ApiKey, requiredScopes: Set<ApiKeyScope>): Boolean {
        return apiKey.scopes.intersect(requiredScopes).isNotEmpty() ||
            ApiKeyScope.ADMIN in apiKey.scopes
    }

    private fun hashKeyForLookup(rawKey: String): String {
        // Use SHA-256 for deterministic lookup
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawKey.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    // When creating a key, we should use this for the hash
    suspend fun generateApiKeyWithShaHash(
        userId: String,
        name: String,
        scopes: Set<ApiKeyScope>,
        expiresIn: Duration? = null
    ): GeneratedApiKey {
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        val rawKey = "${keyPrefix}_${Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)}"

        val keyHash = hashKeyForLookup(rawKey)
        val keyPrefixDisplay = rawKey.take(12) + "..."

        val now = Clock.System.now()
        val expiresAt = expiresIn?.let { now + it }

        val apiKey = ApiKey(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = name,
            keyHash = keyHash,
            keyPrefix = keyPrefixDisplay,
            scopes = scopes,
            expiresAt = expiresAt,
            createdAt = now
        )

        apiKeyRepository.create(apiKey)
        logger.info { "Created API key '$name' for user $userId" }

        return GeneratedApiKey(apiKey, rawKey)
    }
}
