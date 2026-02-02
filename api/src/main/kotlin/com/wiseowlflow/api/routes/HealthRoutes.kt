package com.wiseowlflow.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val services: Map<String, ServiceHealth>
)

@Serializable
data class ServiceHealth(
    val status: String,
    val message: String? = null
)

fun Route.healthRoutes(
    databaseHealthCheck: suspend () -> Boolean,
    redisHealthCheck: suspend () -> Boolean,
    ollamaHealthCheck: suspend () -> Boolean
) {
    get("/health") {
        val services = mutableMapOf<String, ServiceHealth>()
        var allHealthy = true

        // Check database
        try {
            if (databaseHealthCheck()) {
                services["database"] = ServiceHealth("healthy")
            } else {
                services["database"] = ServiceHealth("unhealthy", "Connection failed")
                allHealthy = false
            }
        } catch (e: Exception) {
            services["database"] = ServiceHealth("unhealthy", e.message)
            allHealthy = false
        }

        // Check Redis
        try {
            if (redisHealthCheck()) {
                services["redis"] = ServiceHealth("healthy")
            } else {
                services["redis"] = ServiceHealth("unhealthy", "Connection failed")
                allHealthy = false
            }
        } catch (e: Exception) {
            services["redis"] = ServiceHealth("unhealthy", e.message)
            allHealthy = false
        }

        // Check Ollama (optional)
        try {
            if (ollamaHealthCheck()) {
                services["ollama"] = ServiceHealth("healthy")
            } else {
                services["ollama"] = ServiceHealth("unavailable", "Not configured or not running")
                // Don't mark as unhealthy - Ollama is optional
            }
        } catch (e: Exception) {
            services["ollama"] = ServiceHealth("unavailable", e.message)
        }

        val response = HealthResponse(
            status = if (allHealthy) "healthy" else "degraded",
            version = "0.1.0",
            services = services
        )

        val statusCode = if (allHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(statusCode, response)
    }

    get("/ready") {
        // Lightweight readiness check
        call.respond(HttpStatusCode.OK, mapOf("status" to "ready"))
    }

    get("/live") {
        // Lightweight liveness check
        call.respond(HttpStatusCode.OK, mapOf("status" to "alive"))
    }
}
