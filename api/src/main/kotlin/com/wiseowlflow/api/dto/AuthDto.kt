package com.wiseowlflow.api.dto

import com.wiseowlflow.domain.ApiKeyScope
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String? = null
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class CreateApiKeyRequest(
    val name: String,
    val scopes: Set<ApiKeyScope>,
    val expiresInDays: Int? = null
)

@Serializable
data class ApiKeyResponse(
    val id: String,
    val name: String,
    val keyPrefix: String,
    val scopes: Set<ApiKeyScope>,
    val createdAt: String,
    val expiresAt: String?,
    val lastUsedAt: String?,
    val rawKey: String? = null // Only included on creation
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val name: String?,
    val avatarUrl: String?,
    val authProvider: String,
    val emailVerified: Boolean,
    val createdAt: String
)
