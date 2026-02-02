package com.wiseowlflow

import com.wiseowlflow.api.plugins.*
import com.wiseowlflow.api.routes.*
import com.wiseowlflow.auth.PasswordService
import com.wiseowlflow.auth.apikey.ApiKeyService
import com.wiseowlflow.auth.jwt.JwtConfig
import com.wiseowlflow.auth.jwt.JwtService
import com.wiseowlflow.auth.oauth.OAuthService
import com.wiseowlflow.billing.StripeService
import com.wiseowlflow.billing.UsageEnforcer
import com.wiseowlflow.mcp.proxy.McpProxyServer
import com.wiseowlflow.mcp.proxy.UpstreamManager
import com.wiseowlflow.mcp.routing.ToolRouter
import com.wiseowlflow.persistence.DatabaseConfig
import com.wiseowlflow.persistence.repositories.*
import com.wiseowlflow.session.RedisConfig
import com.wiseowlflow.session.SessionManager
import com.wiseowlflow.workflow.ai.AiDecisionMaker
import com.wiseowlflow.workflow.ai.OllamaClient
import com.wiseowlflow.workflow.engine.WorkflowEngine
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.path
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.sse.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

private val logger = KotlinLogging.logger {}

fun main() {
    val port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("SERVER_HOST") ?: "0.0.0.0"

    logger.info { "Starting WiseOwlFlow MCP Server on $host:$port" }

    embeddedServer(Netty, port = port, host = host, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Initialize database
    val databaseConfig = DatabaseConfig.fromEnvironment()
    val database = databaseConfig.connect()
    databaseConfig.runMigrations()

    // Initialize Redis
    val redisConfig = RedisConfig.fromEnvironment()
    val redis = redisConfig.connect()
    val sessionManager = SessionManager(redis)

    // Initialize repositories
    val userRepository = UserRepositoryImpl()
    val subscriptionRepository = SubscriptionRepositoryImpl()
    val usageRepository = UsageRepositoryImpl()
    val mcpServerRepository = McpServerRepositoryImpl()
    val workflowRepository = WorkflowRepositoryImpl()
    val executionRepository = WorkflowExecutionRepositoryImpl()
    val stepRepository = WorkflowExecutionStepRepositoryImpl()
    val apiKeyRepository = ApiKeyRepositoryImpl()

    // Initialize services
    val jwtConfig = JwtConfig(
        secret = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production",
        issuer = System.getenv("JWT_ISSUER") ?: "wiseowlflow.com",
        audience = System.getenv("JWT_AUDIENCE") ?: "wiseowlflow-api"
    )
    val jwtService = JwtService(jwtConfig)
    val passwordService = PasswordService(userRepository)
    val oauthService = OAuthService.fromEnvironment(userRepository)
    val apiKeyService = ApiKeyService(apiKeyRepository)

    // Initialize billing
    val stripeService = StripeService.fromEnvironment(subscriptionRepository, userRepository)
    val usageEnforcer = UsageEnforcer(subscriptionRepository, usageRepository)

    // Initialize MCP proxy
    val upstreamManager = UpstreamManager(mcpServerRepository)
    val toolRouter = ToolRouter(upstreamManager)
    val mcpProxyServer = McpProxyServer(upstreamManager)

    // Initialize AI (optional)
    val ollamaClient = try {
        OllamaClient.fromEnvironment()
    } catch (e: Exception) {
        logger.warn { "Ollama not configured: ${e.message}" }
        null
    }
    val aiDecisionMaker = ollamaClient?.let { AiDecisionMaker(it) }

    // Initialize workflow engine
    val workflowEngine = WorkflowEngine(
        executionRepository = executionRepository,
        stepRepository = stepRepository,
        toolRouter = toolRouter,
        aiDecisionMaker = aiDecisionMaker
    )

    // Start upstream manager
    val scope = CoroutineScope(Dispatchers.Default)
    scope.launch {
        upstreamManager.start(this)

        // Connect to enabled MCP servers
        mcpServerRepository.findAllEnabled().forEach { server ->
            try {
                upstreamManager.connectServer(server)
            } catch (e: Exception) {
                logger.warn { "Failed to connect to MCP server ${server.name}: ${e.message}" }
            }
        }
    }

    // Configure Ktor
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
    }

    install(DefaultHeaders) {
        header("X-Engine", "WiseOwlFlow MCP Server")
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(Sessions) {
        cookie<String>("oauth_state") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 300
        }
    }

    install(SSE)

    // Configure security
    configureSecurity(jwtConfig, apiKeyService)

    // Configure error handling
    configureStatusPages()

    // Configure routes
    routing {
        healthRoutes(
            databaseHealthCheck = {
                try {
                    userRepository.findById("health-check") // Simple query
                    true
                } catch (e: Exception) {
                    false
                }
            },
            redisHealthCheck = {
                try {
                    redis.ping().get() == "PONG"
                } catch (e: Exception) {
                    false
                }
            },
            ollamaHealthCheck = {
                ollamaClient?.isAvailable() ?: false
            }
        )

        authRoutes(
            jwtService = jwtService,
            passwordService = passwordService,
            oauthService = oauthService,
            apiKeyService = apiKeyService,
            userRepository = userRepository
        )

        workflowRoutes(
            workflowRepository = workflowRepository,
            executionRepository = executionRepository,
            workflowEngine = workflowEngine,
            usageEnforcer = usageEnforcer
        )

        mcpServerRoutes(
            mcpServerRepository = mcpServerRepository,
            upstreamManager = upstreamManager,
            usageEnforcer = usageEnforcer
        )

        billingRoutes(
            stripeService = stripeService,
            subscriptionRepository = subscriptionRepository,
            userRepository = userRepository
        )

        // MCP endpoint
        val mcpEndpoint = McpEndpoint(mcpProxyServer)
        with(mcpEndpoint) {
            mcpRoutes()
        }
    }

    // Shutdown hooks
    environment.monitor.subscribe(ApplicationStopped) {
        logger.info { "Shutting down WiseOwlFlow MCP Server..." }
        scope.launch {
            upstreamManager.stop()
        }
        ollamaClient?.close()
        redisConfig.close()
        databaseConfig.close()
    }

    logger.info { "WiseOwlFlow MCP Server started successfully" }
}
