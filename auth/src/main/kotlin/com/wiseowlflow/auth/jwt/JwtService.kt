package com.wiseowlflow.auth.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.wiseowlflow.domain.User
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val accessTokenExpiration: Duration = 24.hours,
    val refreshTokenExpiration: Duration = (24 * 30).hours // 30 days
)

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

data class JwtClaims(
    val userId: String,
    val email: String,
    val scopes: Set<String> = emptySet()
)

class JwtService(private val config: JwtConfig) {
    private val algorithm = Algorithm.HMAC256(config.secret)
    private val verifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    fun generateTokenPair(user: User, scopes: Set<String> = emptySet()): TokenPair {
        val now = Clock.System.now()
        val accessExpiry = now + config.accessTokenExpiration
        val refreshExpiry = now + config.refreshTokenExpiration

        val accessToken = JWT.create()
            .withSubject(user.id)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withClaim("email", user.email)
            .withClaim("scopes", scopes.toList())
            .withClaim("type", "access")
            .withIssuedAt(Date.from(now.toJavaInstant()))
            .withExpiresAt(Date.from(accessExpiry.toJavaInstant()))
            .sign(algorithm)

        val refreshToken = JWT.create()
            .withSubject(user.id)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withClaim("type", "refresh")
            .withIssuedAt(Date.from(now.toJavaInstant()))
            .withExpiresAt(Date.from(refreshExpiry.toJavaInstant()))
            .sign(algorithm)

        return TokenPair(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = config.accessTokenExpiration.inWholeSeconds
        )
    }

    fun generateAccessToken(user: User, scopes: Set<String> = emptySet()): String {
        val now = Clock.System.now()
        val expiry = now + config.accessTokenExpiration

        return JWT.create()
            .withSubject(user.id)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withClaim("email", user.email)
            .withClaim("scopes", scopes.toList())
            .withClaim("type", "access")
            .withIssuedAt(Date.from(now.toJavaInstant()))
            .withExpiresAt(Date.from(expiry.toJavaInstant()))
            .sign(algorithm)
    }

    fun verifyToken(token: String): Result<DecodedJWT> {
        return try {
            val decoded = verifier.verify(token)
            Result.success(decoded)
        } catch (e: JWTVerificationException) {
            logger.debug { "Token verification failed: ${e.message}" }
            Result.failure(e)
        }
    }

    fun extractClaims(token: String): JwtClaims? {
        return verifyToken(token).getOrNull()?.let { decoded ->
            JwtClaims(
                userId = decoded.subject,
                email = decoded.getClaim("email").asString() ?: "",
                scopes = decoded.getClaim("scopes").asList(String::class.java)?.toSet() ?: emptySet()
            )
        }
    }

    fun isAccessToken(token: String): Boolean {
        return verifyToken(token).getOrNull()?.getClaim("type")?.asString() == "access"
    }

    fun isRefreshToken(token: String): Boolean {
        return verifyToken(token).getOrNull()?.getClaim("type")?.asString() == "refresh"
    }

    companion object {
        fun fromEnvironment(): JwtService {
            val config = JwtConfig(
                secret = System.getenv("JWT_SECRET")
                    ?: throw IllegalStateException("JWT_SECRET not set"),
                issuer = System.getenv("JWT_ISSUER") ?: "wiseowlflow.com",
                audience = System.getenv("JWT_AUDIENCE") ?: "wiseowlflow-api",
                accessTokenExpiration = System.getenv("JWT_EXPIRATION_HOURS")
                    ?.toLongOrNull()?.hours ?: 24.hours
            )
            return JwtService(config)
        }
    }
}
