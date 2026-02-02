package com.wiseowlflow.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val passwordHash: String? = null,
    val authProvider: AuthProvider = AuthProvider.EMAIL,
    val externalId: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val emailVerified: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
enum class AuthProvider {
    EMAIL,
    GOOGLE,
    GITHUB
}
