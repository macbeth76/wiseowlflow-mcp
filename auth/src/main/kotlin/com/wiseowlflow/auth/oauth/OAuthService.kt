package com.wiseowlflow.auth.oauth

import com.wiseowlflow.domain.AuthProvider
import com.wiseowlflow.domain.User
import com.wiseowlflow.ports.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Serializable
data class OAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: List<String>
)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null
)

@Serializable
data class GoogleUserInfo(
    val id: String,
    val email: String,
    val name: String? = null,
    val picture: String? = null,
    @SerialName("verified_email") val verifiedEmail: Boolean = false
)

@Serializable
data class GitHubUserInfo(
    val id: Long,
    val login: String,
    val email: String? = null,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class GitHubEmail(
    val email: String,
    val primary: Boolean,
    val verified: Boolean
)

class OAuthService(
    private val userRepository: UserRepository,
    private val googleConfig: OAuthConfig?,
    private val githubConfig: OAuthConfig?
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    // Google OAuth
    fun getGoogleAuthorizationUrl(state: String): String? {
        val config = googleConfig ?: return null
        return URLBuilder("https://accounts.google.com/o/oauth2/v2/auth").apply {
            parameters.append("client_id", config.clientId)
            parameters.append("redirect_uri", config.redirectUri)
            parameters.append("response_type", "code")
            parameters.append("scope", config.scopes.joinToString(" "))
            parameters.append("state", state)
            parameters.append("access_type", "offline")
            parameters.append("prompt", "consent")
        }.buildString()
    }

    suspend fun handleGoogleCallback(code: String): User? {
        val config = googleConfig ?: return null

        // Exchange code for token
        val tokenResponse = httpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("code", code)
                append("grant_type", "authorization_code")
                append("redirect_uri", config.redirectUri)
            }
        )

        if (!tokenResponse.status.isSuccess()) {
            logger.error { "Failed to exchange Google code for token" }
            return null
        }

        val token = tokenResponse.body<OAuthTokenResponse>()

        // Get user info
        val userInfoResponse = httpClient.get("https://www.googleapis.com/oauth2/v2/userinfo") {
            header(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
        }

        if (!userInfoResponse.status.isSuccess()) {
            logger.error { "Failed to get Google user info" }
            return null
        }

        val userInfo = userInfoResponse.body<GoogleUserInfo>()

        return findOrCreateUser(
            provider = AuthProvider.GOOGLE,
            externalId = userInfo.id,
            email = userInfo.email,
            name = userInfo.name,
            avatarUrl = userInfo.picture,
            emailVerified = userInfo.verifiedEmail
        )
    }

    // GitHub OAuth
    fun getGitHubAuthorizationUrl(state: String): String? {
        val config = githubConfig ?: return null
        return URLBuilder("https://github.com/login/oauth/authorize").apply {
            parameters.append("client_id", config.clientId)
            parameters.append("redirect_uri", config.redirectUri)
            parameters.append("scope", config.scopes.joinToString(" "))
            parameters.append("state", state)
        }.buildString()
    }

    suspend fun handleGitHubCallback(code: String): User? {
        val config = githubConfig ?: return null

        // Exchange code for token
        val tokenResponse = httpClient.post("https://github.com/login/oauth/access_token") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(mapOf(
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "code" to code
            ))
        }

        if (!tokenResponse.status.isSuccess()) {
            logger.error { "Failed to exchange GitHub code for token" }
            return null
        }

        val token = tokenResponse.body<OAuthTokenResponse>()

        // Get user info
        val userInfoResponse = httpClient.get("https://api.github.com/user") {
            header(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }

        if (!userInfoResponse.status.isSuccess()) {
            logger.error { "Failed to get GitHub user info" }
            return null
        }

        val userInfo = userInfoResponse.body<GitHubUserInfo>()

        // Get email if not provided
        val email = userInfo.email ?: run {
            val emailsResponse = httpClient.get("https://api.github.com/user/emails") {
                header(HttpHeaders.Authorization, "Bearer ${token.accessToken}")
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }

            if (emailsResponse.status.isSuccess()) {
                val emails = emailsResponse.body<List<GitHubEmail>>()
                emails.find { it.primary && it.verified }?.email
                    ?: emails.find { it.verified }?.email
            } else {
                null
            }
        }

        if (email == null) {
            logger.error { "Could not get email from GitHub" }
            return null
        }

        return findOrCreateUser(
            provider = AuthProvider.GITHUB,
            externalId = userInfo.id.toString(),
            email = email,
            name = userInfo.name ?: userInfo.login,
            avatarUrl = userInfo.avatarUrl,
            emailVerified = true
        )
    }

    private suspend fun findOrCreateUser(
        provider: AuthProvider,
        externalId: String,
        email: String,
        name: String?,
        avatarUrl: String?,
        emailVerified: Boolean
    ): User {
        // Try to find existing user by provider + external ID
        val existingByExternal = userRepository.findByExternalId(provider, externalId)
        if (existingByExternal != null) {
            return existingByExternal
        }

        // Try to find by email and link account
        val existingByEmail = userRepository.findByEmail(email)
        if (existingByEmail != null) {
            // Update existing user with OAuth info
            val updated = existingByEmail.copy(
                authProvider = provider,
                externalId = externalId,
                name = name ?: existingByEmail.name,
                avatarUrl = avatarUrl ?: existingByEmail.avatarUrl,
                emailVerified = emailVerified || existingByEmail.emailVerified,
                updatedAt = Clock.System.now()
            )
            return userRepository.update(updated)
        }

        // Create new user
        val now = Clock.System.now()
        val newUser = User(
            id = UUID.randomUUID().toString(),
            email = email,
            authProvider = provider,
            externalId = externalId,
            name = name,
            avatarUrl = avatarUrl,
            emailVerified = emailVerified,
            createdAt = now,
            updatedAt = now
        )
        return userRepository.create(newUser)
    }

    companion object {
        fun fromEnvironment(userRepository: UserRepository): OAuthService {
            val googleConfig = System.getenv("GOOGLE_CLIENT_ID")?.let { clientId ->
                OAuthConfig(
                    clientId = clientId,
                    clientSecret = System.getenv("GOOGLE_CLIENT_SECRET") ?: "",
                    redirectUri = System.getenv("GOOGLE_REDIRECT_URI")
                        ?: "http://localhost:8080/auth/google/callback",
                    scopes = listOf("openid", "email", "profile")
                )
            }

            val githubConfig = System.getenv("GITHUB_CLIENT_ID")?.let { clientId ->
                OAuthConfig(
                    clientId = clientId,
                    clientSecret = System.getenv("GITHUB_CLIENT_SECRET") ?: "",
                    redirectUri = System.getenv("GITHUB_REDIRECT_URI")
                        ?: "http://localhost:8080/auth/github/callback",
                    scopes = listOf("user:email", "read:user")
                )
            }

            return OAuthService(userRepository, googleConfig, githubConfig)
        }
    }
}
