package com.wiseowlflow.api.plugins

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger {}

@Serializable
data class ErrorResponse(
    val error: String,
    val code: String,
    val details: String? = null
)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<UnauthorizedException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(
                    error = cause.message ?: "Unauthorized",
                    code = "UNAUTHORIZED"
                )
            )
        }

        exception<ForbiddenException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(
                    error = cause.message ?: "Forbidden",
                    code = "FORBIDDEN"
                )
            )
        }

        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = cause.message ?: "Bad request",
                    code = "BAD_REQUEST"
                )
            )
        }

        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = cause.message ?: "Not found",
                    code = "NOT_FOUND"
                )
            )
        }

        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    error = cause.message ?: "Conflict",
                    code = "CONFLICT"
                )
            )
        }

        exception<QuotaExceededException> { call, cause ->
            call.respond(
                HttpStatusCode.PaymentRequired,
                ErrorResponse(
                    error = cause.message ?: "Quota exceeded",
                    code = "QUOTA_EXCEEDED",
                    details = cause.resource
                )
            )
        }

        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception" }
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = "Internal server error",
                    code = "INTERNAL_ERROR"
                )
            )
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = "Resource not found",
                    code = "NOT_FOUND"
                )
            )
        }
    }
}

class NotFoundException(message: String) : Exception(message)
class ConflictException(message: String) : Exception(message)
class QuotaExceededException(message: String, val resource: String) : Exception(message)
