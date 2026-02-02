package com.wiseowlflow.api.routes

import com.wiseowlflow.mcp.proxy.McpProxyServer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class McpEndpoint(
    private val mcpProxyServer: McpProxyServer
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val servers = ConcurrentHashMap<String, Server>()

    fun Route.mcpRoutes() {
        route("/mcp") {
            // Info endpoint
            get {
                val response = kotlinx.serialization.json.buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive("wiseowlflow-mcp"))
                    put("version", kotlinx.serialization.json.JsonPrimitive("0.1.0"))
                    put("capabilities", kotlinx.serialization.json.buildJsonObject {
                        put("tools", kotlinx.serialization.json.JsonPrimitive(true))
                        put("resources", kotlinx.serialization.json.JsonPrimitive(true))
                        put("prompts", kotlinx.serialization.json.JsonPrimitive(true))
                    })
                }
                call.respondText(
                    json.encodeToString(JsonObject.serializer(), response),
                    io.ktor.http.ContentType.Application.Json
                )
            }

            // SSE endpoint for MCP
            sse("/sse") {
                val sessionId = call.request.queryParameters["session_id"]
                    ?: java.util.UUID.randomUUID().toString()

                logger.info { "MCP SSE connection started: $sessionId" }

                val server = mcpProxyServer.createServer()
                servers[sessionId] = server

                try {
                    // Send initial connection event
                    send(
                        data = json.encodeToString(
                            JsonObject.serializer(),
                            kotlinx.serialization.json.buildJsonObject {
                                put("type", kotlinx.serialization.json.JsonPrimitive("connected"))
                                put("sessionId", kotlinx.serialization.json.JsonPrimitive(sessionId))
                            }
                        ),
                        event = "connection"
                    )

                    // Keep connection alive
                    while (true) {
                        kotlinx.coroutines.delay(30_000)
                        send(data = "", event = "heartbeat")
                    }
                } finally {
                    servers.remove(sessionId)
                    server.close()
                    logger.info { "MCP SSE connection closed: $sessionId" }
                }
            }

            // Message endpoint for client-to-server communication
            post("/message") {
                val sessionId = call.request.queryParameters["session_id"]

                val body = call.receiveText()
                logger.debug { "MCP message received: $body" }

                try {
                    val requestJson = json.parseToJsonElement(body).jsonObject

                    // For now, return a basic response
                    // Full MCP message handling would require more complex routing
                    val response = kotlinx.serialization.json.buildJsonObject {
                        put("jsonrpc", kotlinx.serialization.json.JsonPrimitive("2.0"))
                        put("id", requestJson["id"] ?: kotlinx.serialization.json.JsonPrimitive(1))
                        put("result", kotlinx.serialization.json.buildJsonObject {
                            put("status", kotlinx.serialization.json.JsonPrimitive("ok"))
                        })
                    }

                    call.respondText(
                        json.encodeToString(JsonObject.serializer(), response),
                        ContentType.Application.Json
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Error handling MCP message" }
                    val errorResponse = kotlinx.serialization.json.buildJsonObject {
                        put("jsonrpc", kotlinx.serialization.json.JsonPrimitive("2.0"))
                        put("id", kotlinx.serialization.json.JsonPrimitive(0))
                        put("error", kotlinx.serialization.json.buildJsonObject {
                            put("code", kotlinx.serialization.json.JsonPrimitive(-32603))
                            put("message", kotlinx.serialization.json.JsonPrimitive(e.message ?: "Internal error"))
                        })
                    }
                    call.respondText(
                        json.encodeToString(JsonObject.serializer(), errorResponse),
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError
                    )
                }
            }

            // Streamable HTTP POST endpoint
            post {
                val body = call.receiveText()
                logger.debug { "MCP POST message received: $body" }

                try {
                    val requestJson = json.parseToJsonElement(body).jsonObject

                    val response = kotlinx.serialization.json.buildJsonObject {
                        put("jsonrpc", kotlinx.serialization.json.JsonPrimitive("2.0"))
                        put("id", requestJson["id"] ?: kotlinx.serialization.json.JsonPrimitive(1))
                        put("result", kotlinx.serialization.json.buildJsonObject {
                            put("protocolVersion", kotlinx.serialization.json.JsonPrimitive("2024-11-05"))
                            put("capabilities", kotlinx.serialization.json.buildJsonObject {
                                put("tools", kotlinx.serialization.json.buildJsonObject {
                                    put("listChanged", kotlinx.serialization.json.JsonPrimitive(true))
                                })
                                put("resources", kotlinx.serialization.json.buildJsonObject {
                                    put("subscribe", kotlinx.serialization.json.JsonPrimitive(false))
                                    put("listChanged", kotlinx.serialization.json.JsonPrimitive(true))
                                })
                                put("prompts", kotlinx.serialization.json.buildJsonObject {
                                    put("listChanged", kotlinx.serialization.json.JsonPrimitive(true))
                                })
                            })
                            put("serverInfo", kotlinx.serialization.json.buildJsonObject {
                                put("name", kotlinx.serialization.json.JsonPrimitive("wiseowlflow-mcp"))
                                put("version", kotlinx.serialization.json.JsonPrimitive("0.1.0"))
                            })
                        })
                    }

                    call.respondText(
                        json.encodeToString(JsonObject.serializer(), response),
                        ContentType.Application.Json
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Error handling MCP POST message" }
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
                }
            }
        }
    }
}
