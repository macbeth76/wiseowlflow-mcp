package com.wiseowlflow.api.routes

import com.wiseowlflow.api.dto.*
import com.wiseowlflow.api.plugins.*
import com.wiseowlflow.auth.PasswordService
import com.wiseowlflow.auth.apikey.ApiKeyService
import com.wiseowlflow.auth.jwt.JwtService
import com.wiseowlflow.auth.oauth.OAuthService
import com.wiseowlflow.ports.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.util.UUID
import kotlin.time.Duration.Companion.days

fun Route.authRoutes(
    jwtService: JwtService,
    passwordService: PasswordService,
    oauthService: OAuthService,
    apiKeyService: ApiKeyService?,
    userRepository: UserRepository
) {
    route("/auth") {
        // Email/password login
        post("/login") {
            val request = call.receive<LoginRequest>()
            val result = passwordService.authenticateUser(request.email, request.password)

            result.fold(
                onSuccess = { user ->
                    val tokens = jwtService.generateTokenPair(user)
                    call.respond(TokenResponse(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresIn = tokens.expiresIn
                    ))
                },
                onFailure = { error ->
                    throw UnauthorizedException(error.message ?: "Invalid credentials")
                }
            )
        }

        // Email/password registration
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val result = passwordService.registerUser(
                email = request.email,
                password = request.password,
                name = request.name
            )

            result.fold(
                onSuccess = { user ->
                    val tokens = jwtService.generateTokenPair(user)
                    call.respond(HttpStatusCode.Created, TokenResponse(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresIn = tokens.expiresIn
                    ))
                },
                onFailure = { error ->
                    throw ConflictException(error.message ?: "Registration failed")
                }
            )
        }

        // Refresh token
        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()

            if (!jwtService.isRefreshToken(request.refreshToken)) {
                throw UnauthorizedException("Invalid refresh token")
            }

            val claims = jwtService.extractClaims(request.refreshToken)
                ?: throw UnauthorizedException("Invalid refresh token")

            val user = userRepository.findById(claims.userId)
                ?: throw UnauthorizedException("User not found")

            val tokens = jwtService.generateTokenPair(user)
            call.respond(TokenResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresIn = tokens.expiresIn
            ))
        }

        // Google OAuth
        get("/google") {
            val state = UUID.randomUUID().toString()
            call.sessions.set("oauth_state", state)

            val authUrl = oauthService.getGoogleAuthorizationUrl(state)
                ?: throw NotFoundException("Google OAuth not configured")

            call.respondRedirect(authUrl)
        }

        get("/google/callback") {
            val code = call.parameters["code"]
                ?: throw IllegalArgumentException("Missing authorization code")

            val user = oauthService.handleGoogleCallback(code)
                ?: throw UnauthorizedException("Google authentication failed")

            val tokens = jwtService.generateTokenPair(user)
            // In production, redirect to frontend with tokens
            call.respond(TokenResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresIn = tokens.expiresIn
            ))
        }

        // GitHub OAuth
        get("/github") {
            val state = UUID.randomUUID().toString()
            call.sessions.set("oauth_state", state)

            val authUrl = oauthService.getGitHubAuthorizationUrl(state)
                ?: throw NotFoundException("GitHub OAuth not configured")

            call.respondRedirect(authUrl)
        }

        get("/github/callback") {
            val code = call.parameters["code"]
                ?: throw IllegalArgumentException("Missing authorization code")

            val user = oauthService.handleGitHubCallback(code)
                ?: throw UnauthorizedException("GitHub authentication failed")

            val tokens = jwtService.generateTokenPair(user)
            call.respond(TokenResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresIn = tokens.expiresIn
            ))
        }

        // Authenticated routes
        authenticate(AUTH_JWT) {
            // Get current user
            get("/me") {
                val auth = call.requireUser()
                val user = userRepository.findById(auth.userId)
                    ?: throw NotFoundException("User not found")

                call.respond(UserResponse(
                    id = user.id,
                    email = user.email,
                    name = user.name,
                    avatarUrl = user.avatarUrl,
                    authProvider = user.authProvider.name,
                    emailVerified = user.emailVerified,
                    createdAt = user.createdAt.toString()
                ))
            }

            // Change password
            post("/change-password") {
                val auth = call.requireUser()
                val request = call.receive<ChangePasswordRequest>()

                val result = passwordService.changePassword(
                    userId = auth.userId,
                    currentPassword = request.currentPassword,
                    newPassword = request.newPassword
                )

                result.fold(
                    onSuccess = { call.respond(HttpStatusCode.OK, mapOf("message" to "Password changed")) },
                    onFailure = { throw UnauthorizedException(it.message ?: "Password change failed") }
                )
            }

            // API Key management
            if (apiKeyService != null) {
                route("/api-keys") {
                    get {
                        val auth = call.requireUser()
                        val keys = apiKeyService.listApiKeys(auth.userId)

                        call.respond(keys.map { key ->
                            ApiKeyResponse(
                                id = key.id,
                                name = key.name,
                                keyPrefix = key.keyPrefix,
                                scopes = key.scopes,
                                createdAt = key.createdAt.toString(),
                                expiresAt = key.expiresAt?.toString(),
                                lastUsedAt = key.lastUsedAt?.toString()
                            )
                        })
                    }

                    post {
                        val auth = call.requireUser()
                        val request = call.receive<CreateApiKeyRequest>()

                        val expiresIn = request.expiresInDays?.days

                        val generated = apiKeyService.generateApiKeyWithShaHash(
                            userId = auth.userId,
                            name = request.name,
                            scopes = request.scopes,
                            expiresIn = expiresIn
                        )

                        call.respond(HttpStatusCode.Created, ApiKeyResponse(
                            id = generated.apiKey.id,
                            name = generated.apiKey.name,
                            keyPrefix = generated.apiKey.keyPrefix,
                            scopes = generated.apiKey.scopes,
                            createdAt = generated.apiKey.createdAt.toString(),
                            expiresAt = generated.apiKey.expiresAt?.toString(),
                            lastUsedAt = null,
                            rawKey = generated.rawKey
                        ))
                    }

                    delete("/{id}") {
                        val auth = call.requireUser()
                        val keyId = call.parameters["id"]
                            ?: throw IllegalArgumentException("Missing API key ID")

                        val revoked = apiKeyService.revokeApiKey(keyId, auth.userId)
                        if (!revoked) {
                            throw NotFoundException("API key not found")
                        }

                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}
