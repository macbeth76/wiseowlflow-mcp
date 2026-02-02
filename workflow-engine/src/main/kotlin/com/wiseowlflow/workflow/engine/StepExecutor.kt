package com.wiseowlflow.workflow.engine

import com.wiseowlflow.domain.*
import com.wiseowlflow.mcp.routing.ToolRouter
import com.wiseowlflow.workflow.ai.AiDecisionMaker
import com.wiseowlflow.workflow.dsl.ExpressionEvaluator
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

sealed class StepResult {
    data class Success(val output: JsonElement) : StepResult()
    data class Failure(val error: String, val retryable: Boolean = true) : StepResult()
    data class Skip(val reason: String) : StepResult()
}

class StepExecutor(
    private val toolRouter: ToolRouter?,
    private val aiDecisionMaker: AiDecisionMaker?
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            requestTimeout = 30_000
        }
    }

    suspend fun execute(
        step: WorkflowStep,
        context: ExecutionContext
    ): StepResult {
        logger.debug { "Executing step: ${step.id} (${step.type})" }

        // Check condition if present
        val stepCondition = step.condition
        if (stepCondition != null) {
            val conditionResult = ExpressionEvaluator.evaluateCondition(stepCondition, context)
            if (!conditionResult) {
                return StepResult.Skip("Condition not met: $stepCondition")
            }
        }

        return try {
            withTimeout(step.timeout ?: 60_000) {
                when (step.type) {
                    StepType.MCP_TOOL -> executeMcpTool(step.config as StepConfig.McpTool, context)
                    StepType.AI_DECISION -> executeAiDecision(step.config as StepConfig.AiDecision, context)
                    StepType.CONDITION -> executeCondition(step.config as StepConfig.Condition, context)
                    StepType.PARALLEL -> executeParallel(step.config as StepConfig.Parallel, context)
                    StepType.WAIT -> executeWait(step.config as StepConfig.Wait, context)
                    StepType.TRANSFORM -> executeTransform(step.config as StepConfig.Transform, context)
                    StepType.HTTP_REQUEST -> executeHttpRequest(step.config as StepConfig.HttpRequest, context)
                }
            }
        } catch (e: TimeoutCancellationException) {
            StepResult.Failure("Step timed out after ${step.timeout ?: 60_000}ms", retryable = true)
        } catch (e: Exception) {
            logger.error(e) { "Step ${step.id} failed" }
            StepResult.Failure(e.message ?: "Unknown error", retryable = true)
        }
    }

    private suspend fun executeMcpTool(
        config: StepConfig.McpTool,
        context: ExecutionContext
    ): StepResult {
        if (toolRouter == null) {
            return StepResult.Failure("MCP tool router not configured", retryable = false)
        }

        // Evaluate arguments
        val evaluatedArgs = config.arguments.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> {
                    if (value.isString) {
                        val evaluated = ExpressionEvaluator.evaluate(value.content, context)
                        JsonPrimitive(evaluated)
                    } else {
                        value
                    }
                }
                else -> value
            }
        }

        val result = toolRouter.routeToolCallByServerAndName(
            serverName = config.server,
            toolName = config.tool,
            arguments = evaluatedArgs
        )

        return if (result.isError != true) {
            // Extract text content from result
            val textContent = result.content.filterIsInstance<TextContent>()
                .joinToString("\n") { it.text }
            StepResult.Success(JsonPrimitive(textContent))
        } else {
            val errorMessage = result.content.filterIsInstance<TextContent>()
                .firstOrNull()?.text ?: "Tool call failed"
            StepResult.Failure(errorMessage)
        }
    }

    private suspend fun executeAiDecision(
        config: StepConfig.AiDecision,
        context: ExecutionContext
    ): StepResult {
        if (aiDecisionMaker == null) {
            return StepResult.Failure("AI decision maker not configured", retryable = false)
        }

        val result = aiDecisionMaker.makeDecision(config, context)

        return if (result.success && result.output != null) {
            StepResult.Success(result.output)
        } else {
            StepResult.Failure(result.error ?: "AI decision failed")
        }
    }

    private suspend fun executeCondition(
        config: StepConfig.Condition,
        context: ExecutionContext
    ): StepResult {
        val conditionResult = ExpressionEvaluator.evaluateCondition(config.expression, context)

        return StepResult.Success(buildJsonObject {
            put("result", conditionResult)
            put("branch", if (conditionResult) "then" else "else")
            putJsonArray("next_steps") {
                val steps = if (conditionResult) config.thenSteps else config.elseSteps
                steps.forEach { add(it) }
            }
        })
    }

    private suspend fun executeParallel(
        config: StepConfig.Parallel,
        context: ExecutionContext
    ): StepResult {
        // Parallel execution would require step references - simplified for now
        return StepResult.Success(buildJsonObject {
            put("branches", config.branches.size)
            put("wait_for", config.waitFor.name)
        })
    }

    private suspend fun executeWait(
        config: StepConfig.Wait,
        context: ExecutionContext
    ): StepResult {
        val waitDuration = config.duration
        if (waitDuration != null) {
            delay(waitDuration)
        }

        return StepResult.Success(buildJsonObject {
            put("waited_ms", waitDuration ?: 0)
        })
    }

    private suspend fun executeTransform(
        config: StepConfig.Transform,
        context: ExecutionContext
    ): StepResult {
        val result = ExpressionEvaluator.evaluateToJsonElement(config.expression, context)
        context.setVariable(config.outputKey, result)

        return StepResult.Success(buildJsonObject {
            put(config.outputKey, result)
        })
    }

    private suspend fun executeHttpRequest(
        config: StepConfig.HttpRequest,
        context: ExecutionContext
    ): StepResult {
        val evaluatedUrl = ExpressionEvaluator.evaluate(config.url, context)

        val response = httpClient.request(evaluatedUrl) {
            method = HttpMethod.parse(config.method)
            config.headers.forEach { (key, value) ->
                header(key, ExpressionEvaluator.evaluate(value, context))
            }
            config.body?.let {
                contentType(ContentType.Application.Json)
                setBody(it)
            }
        }

        val responseBody = response.body<String>()

        return StepResult.Success(buildJsonObject {
            put("status", response.status.value)
            put("body", try {
                json.parseToJsonElement(responseBody)
            } catch (e: Exception) {
                JsonPrimitive(responseBody)
            })
        })
    }
}
