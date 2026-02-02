package com.wiseowlflow.api.routes

import com.wiseowlflow.api.dto.*
import com.wiseowlflow.api.plugins.*
import com.wiseowlflow.billing.QuotaCheckResult
import com.wiseowlflow.billing.UsageEnforcer
import com.wiseowlflow.domain.McpServer
import com.wiseowlflow.mcp.proxy.UpstreamManager
import com.wiseowlflow.ports.McpServerRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import java.util.UUID

fun Route.mcpServerRoutes(
    mcpServerRepository: McpServerRepository,
    upstreamManager: UpstreamManager,
    usageEnforcer: UsageEnforcer
) {
    authenticate(AUTH_JWT, AUTH_API_KEY) {
        route("/mcp-servers") {
            // List MCP servers
            get {
                val auth = call.requireUser()
                val servers = mcpServerRepository.findByUserId(auth.userId)

                call.respond(McpServerListResponse(
                    servers = servers.map { it.toResponse() },
                    total = servers.size
                ))
            }

            // Create MCP server
            post {
                val auth = call.requireUser()
                val request = call.receive<CreateMcpServerRequest>()

                // Check quota
                when (val check = usageEnforcer.checkMcpServerCreation(auth.userId)) {
                    is QuotaCheckResult.Exceeded -> throw QuotaExceededException(
                        "MCP server limit reached (${check.limit})",
                        "mcp_servers"
                    )
                    is QuotaCheckResult.Error -> throw Exception(check.message)
                    QuotaCheckResult.Allowed -> {}
                }

                // Check for duplicate name
                val existing = mcpServerRepository.findByUserIdAndName(auth.userId, request.name)
                if (existing != null) {
                    throw ConflictException("MCP server with name '${request.name}' already exists")
                }

                val now = Clock.System.now()
                val server = McpServer(
                    id = UUID.randomUUID().toString(),
                    userId = auth.userId,
                    name = request.name,
                    displayName = request.displayName,
                    description = request.description,
                    transportType = request.transportType,
                    endpoint = request.endpoint,
                    authConfig = request.authConfig,
                    enabled = request.enabled,
                    createdAt = now,
                    updatedAt = now
                )

                val created = mcpServerRepository.create(server)

                // Auto-connect if enabled
                if (created.enabled) {
                    upstreamManager.connectServer(created)
                }

                call.respond(HttpStatusCode.Created, created.toResponse())
            }

            // Get MCP server by ID
            get("/{id}") {
                val auth = call.requireUser()
                val serverId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing server ID")

                val server = mcpServerRepository.findById(serverId)
                    ?: throw NotFoundException("MCP server not found")

                if (server.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                call.respond(server.toResponse())
            }

            // Update MCP server
            put("/{id}") {
                val auth = call.requireUser()
                val serverId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing server ID")
                val request = call.receive<UpdateMcpServerRequest>()

                val server = mcpServerRepository.findById(serverId)
                    ?: throw NotFoundException("MCP server not found")

                if (server.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                // Check for name conflict if name is being changed
                if (request.name != null && request.name != server.name) {
                    val existing = mcpServerRepository.findByUserIdAndName(auth.userId, request.name)
                    if (existing != null) {
                        throw ConflictException("MCP server with name '${request.name}' already exists")
                    }
                }

                val updated = server.copy(
                    name = request.name ?: server.name,
                    displayName = request.displayName ?: server.displayName,
                    description = request.description ?: server.description,
                    transportType = request.transportType ?: server.transportType,
                    endpoint = request.endpoint ?: server.endpoint,
                    authConfig = request.authConfig ?: server.authConfig,
                    enabled = request.enabled ?: server.enabled,
                    updatedAt = Clock.System.now()
                )

                mcpServerRepository.update(updated)

                // Reconnect if configuration changed
                upstreamManager.disconnectServer(serverId)
                if (updated.enabled) {
                    upstreamManager.connectServer(updated)
                }

                call.respond(updated.toResponse())
            }

            // Delete MCP server
            delete("/{id}") {
                val auth = call.requireUser()
                val serverId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing server ID")

                val server = mcpServerRepository.findById(serverId)
                    ?: throw NotFoundException("MCP server not found")

                if (server.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                upstreamManager.disconnectServer(serverId)
                mcpServerRepository.delete(serverId)

                call.respond(HttpStatusCode.NoContent)
            }

            // Connect to MCP server
            post("/{id}/connect") {
                val auth = call.requireUser()
                val serverId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing server ID")

                val server = mcpServerRepository.findById(serverId)
                    ?: throw NotFoundException("MCP server not found")

                if (server.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                val connected = upstreamManager.connectServer(server)
                if (!connected) {
                    throw IllegalArgumentException("Failed to connect to server")
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "Connected"))
            }

            // Disconnect from MCP server
            post("/{id}/disconnect") {
                val auth = call.requireUser()
                val serverId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing server ID")

                val server = mcpServerRepository.findById(serverId)
                    ?: throw NotFoundException("MCP server not found")

                if (server.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                upstreamManager.disconnectServer(serverId)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Disconnected"))
            }

            // Get tools for a specific server
            get("/{id}/tools") {
                val auth = call.requireUser()
                val serverId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing server ID")

                val server = mcpServerRepository.findById(serverId)
                    ?: throw NotFoundException("MCP server not found")

                if (server.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                val tools = upstreamManager.getToolsForServer(serverId)

                call.respond(McpToolListResponse(
                    tools = tools.map {
                        McpToolResponse(
                            serverName = it.serverName,
                            name = it.name,
                            qualifiedName = it.qualifiedName,
                            description = it.description,
                            inputSchema = it.inputSchema
                        )
                    },
                    total = tools.size
                ))
            }
        }

        // Aggregated tools endpoint
        route("/mcp/tools") {
            get {
                val tools = upstreamManager.getAllTools()

                call.respond(McpToolListResponse(
                    tools = tools.map {
                        McpToolResponse(
                            serverName = it.serverName,
                            name = it.name,
                            qualifiedName = it.qualifiedName,
                            description = it.description,
                            inputSchema = it.inputSchema
                        )
                    },
                    total = tools.size
                ))
            }
        }
    }
}

private fun McpServer.toResponse() = McpServerResponse(
    id = id,
    name = name,
    displayName = displayName,
    description = description,
    transportType = transportType,
    endpoint = endpoint,
    enabled = enabled,
    healthStatus = healthStatus,
    lastHealthCheck = lastHealthCheck?.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)
