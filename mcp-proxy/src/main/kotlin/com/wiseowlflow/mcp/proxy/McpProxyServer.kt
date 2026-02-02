package com.wiseowlflow.mcp.proxy

import com.wiseowlflow.domain.McpPrompt
import com.wiseowlflow.domain.McpResource
import com.wiseowlflow.domain.McpTool
import com.wiseowlflow.mcp.routing.ToolRouter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

class McpProxyServer(
    private val upstreamManager: UpstreamManager,
    private val toolRouter: ToolRouter = ToolRouter(upstreamManager)
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun createServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "wiseowlflow-mcp",
                version = "0.1.0"
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    resources = ServerCapabilities.Resources(subscribe = false, listChanged = true),
                    prompts = ServerCapabilities.Prompts(listChanged = true)
                )
            )
        )

        // Register WiseOwlFlow tools
        registerWiseOwlFlowTools(server)

        return server
    }

    private fun registerWiseOwlFlowTools(server: Server) {
        server.addTool(
            name = "wiseowlflow.execute_workflow",
            description = "Execute a saved workflow by name or ID"
        ) { arguments ->
            CallToolResult(
                content = listOf(TextContent("Workflow execution not yet implemented")),
                isError = false
            )
        }

        server.addTool(
            name = "wiseowlflow.list_workflows",
            description = "List all available workflows"
        ) { arguments ->
            CallToolResult(
                content = listOf(TextContent("Workflow listing not yet implemented")),
                isError = false
            )
        }

        server.addTool(
            name = "wiseowlflow.get_execution_status",
            description = "Get the status of a workflow execution"
        ) { arguments ->
            CallToolResult(
                content = listOf(TextContent("Execution status not yet implemented")),
                isError = false
            )
        }

        server.addTool(
            name = "wiseowlflow.save_workflow",
            description = "Create or update a workflow from a definition"
        ) { arguments ->
            CallToolResult(
                content = listOf(TextContent("Workflow saving not yet implemented")),
                isError = false
            )
        }
    }

    private fun McpTool.toSdkTool() = Tool(
        name = qualifiedName,
        description = description,
        inputSchema = Tool.Input(
            properties = inputSchema["properties"] as? JsonObject ?: JsonObject(emptyMap()),
            required = (inputSchema["required"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
        )
    )

    private fun McpResource.toSdkResource() = Resource(
        uri = uri,
        name = "$serverName/$name",
        description = description,
        mimeType = mimeType
    )

    private fun McpPrompt.toSdkPrompt() = Prompt(
        name = qualifiedName,
        description = description,
        arguments = arguments.map { arg ->
            PromptArgument(
                name = arg.name,
                description = arg.description,
                required = arg.required
            )
        }
    )
}
