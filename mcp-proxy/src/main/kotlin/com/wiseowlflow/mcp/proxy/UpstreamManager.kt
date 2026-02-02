package com.wiseowlflow.mcp.proxy

import com.wiseowlflow.domain.*
import com.wiseowlflow.mcp.client.McpClientConnection
import com.wiseowlflow.mcp.client.McpClientFactory
import com.wiseowlflow.ports.McpServerRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class UpstreamManager(
    private val mcpServerRepository: McpServerRepository,
    private val clientFactory: McpClientFactory = McpClientFactory(),
    private val healthCheckIntervalMs: Long = 60_000
) {
    private val connections = ConcurrentHashMap<String, McpClientConnection>()
    private val toolsCache = ConcurrentHashMap<String, List<McpTool>>()
    private val resourcesCache = ConcurrentHashMap<String, List<McpResource>>()
    private val promptsCache = ConcurrentHashMap<String, List<McpPrompt>>()
    private val mutex = Mutex()
    private var healthCheckJob: Job? = null

    suspend fun start(scope: CoroutineScope) {
        logger.info { "Starting upstream manager..." }
        healthCheckJob = scope.launch {
            while (isActive) {
                try {
                    performHealthChecks()
                } catch (e: Exception) {
                    logger.error(e) { "Error during health check" }
                }
                delay(healthCheckIntervalMs)
            }
        }
    }

    suspend fun stop() {
        logger.info { "Stopping upstream manager..." }
        healthCheckJob?.cancel()
        connections.values.forEach { it.disconnect() }
        connections.clear()
        toolsCache.clear()
        resourcesCache.clear()
        promptsCache.clear()
    }

    suspend fun connectServer(server: McpServer): Boolean = mutex.withLock {
        if (connections.containsKey(server.id)) {
            logger.debug { "Server ${server.name} already connected" }
            return true
        }

        return try {
            val connection = clientFactory.createClient(server)
            connection.connect()
            connections[server.id] = connection

            // Fetch and cache tools, resources, prompts
            refreshServerCapabilities(server.id)

            mcpServerRepository.updateHealthStatus(
                server.id,
                McpHealthStatus.HEALTHY,
                Clock.System.now()
            )

            logger.info { "Successfully connected to server: ${server.name}" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to server: ${server.name}" }
            mcpServerRepository.updateHealthStatus(
                server.id,
                McpHealthStatus.UNHEALTHY,
                Clock.System.now()
            )
            false
        }
    }

    suspend fun disconnectServer(serverId: String) = mutex.withLock {
        connections.remove(serverId)?.disconnect()
        toolsCache.remove(serverId)
        resourcesCache.remove(serverId)
        promptsCache.remove(serverId)
    }

    fun getConnection(serverId: String): McpClientConnection? = connections[serverId]

    fun getConnectionByName(serverName: String): McpClientConnection? =
        connections.values.find { it.serverName == serverName }

    suspend fun getAllTools(userId: String? = null): List<McpTool> {
        return toolsCache.values.flatten()
    }

    suspend fun getToolsForServer(serverId: String): List<McpTool> {
        return toolsCache[serverId] ?: emptyList()
    }

    suspend fun getAllResources(userId: String? = null): List<McpResource> {
        return resourcesCache.values.flatten()
    }

    suspend fun getAllPrompts(userId: String? = null): List<McpPrompt> {
        return promptsCache.values.flatten()
    }

    suspend fun findToolByQualifiedName(qualifiedName: String): Pair<McpClientConnection, McpTool>? {
        val parts = qualifiedName.split(".", limit = 2)
        if (parts.size != 2) return null

        val (serverName, toolName) = parts
        val connection = getConnectionByName(serverName) ?: return null
        val tool = toolsCache[connection.serverId]?.find { it.name == toolName } ?: return null

        return connection to tool
    }

    private suspend fun refreshServerCapabilities(serverId: String) {
        val connection = connections[serverId] ?: return

        try {
            // Fetch tools
            val toolsResult = connection.client.listTools()
            val tools = toolsResult.tools.map { tool ->
                McpTool(
                    serverId = serverId,
                    serverName = connection.serverName,
                    name = tool.name,
                    description = tool.description,
                    inputSchema = buildJsonObject {
                        put("type", "object")
                        put("properties", tool.inputSchema.properties ?: JsonObject(emptyMap()))
                        putJsonArray("required") {
                            tool.inputSchema.required?.forEach { add(it) }
                        }
                    }
                )
            }
            toolsCache[serverId] = tools

            // Fetch resources
            val resourcesResult = connection.client.listResources()
            val resources = resourcesResult.resources.map { resource ->
                McpResource(
                    serverId = serverId,
                    serverName = connection.serverName,
                    uri = resource.uri,
                    name = resource.name,
                    description = resource.description,
                    mimeType = resource.mimeType
                )
            }
            resourcesCache[serverId] = resources

            // Fetch prompts
            val promptsResult = connection.client.listPrompts()
            val prompts = promptsResult.prompts.map { prompt ->
                McpPrompt(
                    serverId = serverId,
                    serverName = connection.serverName,
                    name = prompt.name,
                    description = prompt.description,
                    arguments = prompt.arguments?.map { arg ->
                        McpPromptArgument(
                            name = arg.name,
                            description = arg.description,
                            required = arg.required ?: false
                        )
                    } ?: emptyList()
                )
            }
            promptsCache[serverId] = prompts

            logger.debug {
                "Refreshed capabilities for ${connection.serverName}: " +
                    "${tools.size} tools, ${resources.size} resources, ${prompts.size} prompts"
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to refresh capabilities for server: ${connection.serverName}" }
        }
    }

    private suspend fun performHealthChecks() {
        logger.debug { "Performing health checks on ${connections.size} connections" }

        connections.forEach { (serverId, connection) ->
            try {
                // Try to list tools as a health check
                connection.client.listTools()
                mcpServerRepository.updateHealthStatus(
                    serverId,
                    McpHealthStatus.HEALTHY,
                    Clock.System.now()
                )
            } catch (e: Exception) {
                logger.warn { "Health check failed for ${connection.serverName}: ${e.message}" }
                mcpServerRepository.updateHealthStatus(
                    serverId,
                    McpHealthStatus.UNHEALTHY,
                    Clock.System.now()
                )
            }
        }
    }
}
