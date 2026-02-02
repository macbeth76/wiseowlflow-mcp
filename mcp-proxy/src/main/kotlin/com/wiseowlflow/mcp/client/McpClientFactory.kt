package com.wiseowlflow.mcp.client

import com.wiseowlflow.domain.McpAuthConfig
import com.wiseowlflow.domain.McpServer
import com.wiseowlflow.domain.McpTransportType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

class McpClientFactory {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createClient(server: McpServer): McpClientConnection {
        logger.info { "Creating MCP client for server: ${server.name} (${server.transportType})" }

        val client = Client(
            clientInfo = Implementation(
                name = "wiseowlflow-mcp-proxy",
                version = "0.1.0"
            )
        )

        return McpClientConnection(
            serverId = server.id,
            serverName = server.name,
            client = client,
            server = server
        )
    }

    private fun createHttpClient(authConfig: McpAuthConfig?): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }

            when (authConfig) {
                is McpAuthConfig.Bearer -> {
                    install(Auth) {
                        bearer {
                            loadTokens {
                                BearerTokens(authConfig.token, "")
                            }
                        }
                    }
                }
                is McpAuthConfig.Basic -> {
                    install(Auth) {
                        basic {
                            credentials {
                                BasicAuthCredentials(authConfig.username, authConfig.password)
                            }
                        }
                    }
                }
                is McpAuthConfig.ApiKey -> {
                    // API key is handled via headers, configured per-request
                }
                is McpAuthConfig.None, null -> {
                    // No auth
                }
            }

            engine {
                requestTimeout = 60_000
            }
        }
    }
}

class McpClientConnection(
    val serverId: String,
    val serverName: String,
    val client: Client,
    private val server: McpServer
) {
    private var process: Process? = null
    var isConnected: Boolean = false
        private set

    suspend fun connect() {
        when (server.transportType) {
            McpTransportType.STDIO -> {
                val parts = server.endpoint.split(" ")
                val command = parts.first()
                val args = parts.drop(1)

                val proc = ProcessBuilder(listOf(command) + args)
                    .redirectErrorStream(false)
                    .start()
                process = proc

                val transport = StdioClientTransport(
                    input = proc.inputStream.asSource().buffered(),
                    output = proc.outputStream.asSink().buffered()
                )
                client.connect(transport)
            }
            McpTransportType.SSE, McpTransportType.STREAMABLE_HTTP -> {
                // SSE/HTTP transport would need different handling
                logger.warn { "SSE/HTTP transport not fully implemented for ${server.name}" }
            }
        }
        isConnected = true
        logger.info { "Connected to MCP server: $serverName" }
    }

    suspend fun disconnect() {
        try {
            client.close()
            process?.destroy()
            process = null
            isConnected = false
            logger.info { "Disconnected from MCP server: $serverName" }
        } catch (e: Exception) {
            logger.warn(e) { "Error disconnecting from MCP server: $serverName" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
