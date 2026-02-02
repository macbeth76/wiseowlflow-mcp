package com.wiseowlflow.mcp.routing

import com.wiseowlflow.domain.McpTool
import com.wiseowlflow.mcp.proxy.UpstreamManager
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

class ToolRouter(
    private val upstreamManager: UpstreamManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun routeToolCall(
        qualifiedName: String,
        arguments: Map<String, JsonElement>
    ): CallToolResult {
        logger.debug { "Routing tool call: $qualifiedName with arguments: $arguments" }

        val (connection, tool) = upstreamManager.findToolByQualifiedName(qualifiedName)
            ?: return CallToolResult(
                content = listOf(TextContent("Tool not found: $qualifiedName")),
                isError = true
            )

        if (!connection.isConnected) {
            return CallToolResult(
                content = listOf(TextContent("Server not connected: ${connection.serverName}")),
                isError = true
            )
        }

        return try {
            val result = connection.client.callTool(
                name = tool.name,
                arguments = arguments
            )

            result ?: CallToolResult(
                content = listOf(TextContent("Tool call returned null result")),
                isError = true
            )
        } catch (e: Exception) {
            logger.error(e) { "Tool call failed: $qualifiedName" }
            CallToolResult(
                content = listOf(TextContent("Tool call failed: ${e.message}")),
                isError = true
            )
        }
    }

    suspend fun routeToolCallByServerAndName(
        serverName: String,
        toolName: String,
        arguments: Map<String, JsonElement>
    ): CallToolResult {
        return routeToolCall("$serverName.$toolName", arguments)
    }
}
