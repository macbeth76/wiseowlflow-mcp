package com.wiseowlflow.api.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.wiseowlflow.auth.apikey.ApiKeyService
import com.wiseowlflow.auth.jwt.JwtConfig
import com.wiseowlflow.domain.User
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

data class AuthenticatedUser(
    val userId: String,
    val email: String,
    val scopes: Set<String>
)

const val AUTH_JWT = "auth-jwt"
const val AUTH_API_KEY = "auth-api-key"

fun Application.configureSecurity(jwtConfig: JwtConfig, apiKeyService: ApiKeyService?) {
    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = "wiseowlflow"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                    .withIssuer(jwtConfig.issuer)
                    .withAudience(jwtConfig.audience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
            }
        }

        // API key authentication
        if (apiKeyService != null) {
            bearer(AUTH_API_KEY) {
                authenticate { tokenCredential ->
                    val apiKey = apiKeyService.validateApiKey(tokenCredential.token)
                    if (apiKey != null) {
                        UserIdPrincipal(apiKey.userId)
                    } else {
                        null
                    }
                }
            }
        }
    }
}

fun ApplicationCall.authenticatedUser(): AuthenticatedUser? {
    val jwtPrincipal = principal<JWTPrincipal>()
    if (jwtPrincipal != null) {
        return AuthenticatedUser(
            userId = jwtPrincipal.payload.subject,
            email = jwtPrincipal.payload.getClaim("email").asString() ?: "",
            scopes = jwtPrincipal.payload.getClaim("scopes").asList(String::class.java)?.toSet() ?: emptySet()
        )
    }

    val userIdPrincipal = principal<UserIdPrincipal>()
    if (userIdPrincipal != null) {
        return AuthenticatedUser(
            userId = userIdPrincipal.name,
            email = "",
            scopes = emptySet()
        )
    }

    return null
}

fun ApplicationCall.requireUser(): AuthenticatedUser {
    return authenticatedUser() ?: throw UnauthorizedException("Authentication required")
}

class UnauthorizedException(message: String) : Exception(message)
class ForbiddenException(message: String) : Exception(message)
