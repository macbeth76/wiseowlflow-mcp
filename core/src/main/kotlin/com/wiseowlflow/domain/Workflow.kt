package com.wiseowlflow.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class Workflow(
    val id: String,
    val userId: String,
    val name: String,
    val description: String? = null,
    val definition: WorkflowDefinition,
    val enabled: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class WorkflowDefinition(
    val name: String,
    val description: String? = null,
    val trigger: WorkflowTrigger,
    val steps: List<WorkflowStep>,
    val variables: Map<String, JsonElement> = emptyMap()
)

@Serializable
sealed class WorkflowTrigger {
    abstract val type: String

    @Serializable
    data class Webhook(
        val path: String,
        val method: String = "POST",
        val headers: Map<String, String> = emptyMap()
    ) : WorkflowTrigger() {
        override val type: String = "webhook"
    }

    @Serializable
    data class Schedule(
        val cron: String,
        val timezone: String = "UTC"
    ) : WorkflowTrigger() {
        override val type: String = "schedule"
    }

    @Serializable
    data class Manual(
        val inputSchema: JsonObject? = null
    ) : WorkflowTrigger() {
        override val type: String = "manual"
    }

    @Serializable
    data class McpEvent(
        val serverId: String,
        val eventType: String
    ) : WorkflowTrigger() {
        override val type: String = "mcp_event"
    }
}

@Serializable
data class WorkflowStep(
    val id: String,
    val name: String? = null,
    val type: StepType,
    val config: StepConfig,
    val condition: String? = null,
    val retryPolicy: RetryPolicy? = null,
    val timeout: Long? = null
)

@Serializable
enum class StepType {
    MCP_TOOL,
    AI_DECISION,
    CONDITION,
    PARALLEL,
    WAIT,
    TRANSFORM,
    HTTP_REQUEST
}

@Serializable
sealed class StepConfig {
    @Serializable
    data class McpTool(
        val server: String,
        val tool: String,
        val arguments: Map<String, JsonElement> = emptyMap()
    ) : StepConfig()

    @Serializable
    data class AiDecision(
        val prompt: String,
        val model: String? = null,
        val temperature: Double = 0.7,
        val maxTokens: Int = 1000,
        val outputFormat: AiOutputFormat = AiOutputFormat.TEXT
    ) : StepConfig()

    @Serializable
    data class Condition(
        val expression: String,
        val thenSteps: List<String>,
        val elseSteps: List<String> = emptyList()
    ) : StepConfig()

    @Serializable
    data class Parallel(
        val branches: List<List<String>>,
        val waitFor: ParallelWaitStrategy = ParallelWaitStrategy.ALL
    ) : StepConfig()

    @Serializable
    data class Wait(
        val duration: Long? = null,
        val until: String? = null
    ) : StepConfig()

    @Serializable
    data class Transform(
        val expression: String,
        val outputKey: String
    ) : StepConfig()

    @Serializable
    data class HttpRequest(
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: JsonElement? = null
    ) : StepConfig()
}

@Serializable
enum class AiOutputFormat {
    TEXT,
    JSON,
    BOOLEAN
}

@Serializable
enum class ParallelWaitStrategy {
    ALL,
    ANY,
    NONE
}

@Serializable
data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelay: Long = 1000,
    val maxDelay: Long = 30000,
    val backoffMultiplier: Double = 2.0
)
