package com.wiseowlflow.workflow.ai

import com.wiseowlflow.domain.AiOutputFormat
import com.wiseowlflow.domain.ExecutionContext
import com.wiseowlflow.domain.StepConfig
import com.wiseowlflow.workflow.dsl.ExpressionEvaluator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

class AiDecisionMaker(
    private val ollamaClient: OllamaClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun makeDecision(
        config: StepConfig.AiDecision,
        context: ExecutionContext
    ): AiDecisionResult {
        // Evaluate the prompt template with context
        val evaluatedPrompt = ExpressionEvaluator.evaluate(config.prompt, context)

        logger.debug { "Making AI decision with prompt: $evaluatedPrompt" }

        val response = try {
            ollamaClient.generate(
                prompt = buildPrompt(evaluatedPrompt, config.outputFormat),
                model = config.model,
                temperature = config.temperature,
                maxTokens = config.maxTokens
            )
        } catch (e: Exception) {
            logger.error(e) { "AI decision failed" }
            return AiDecisionResult.failure("AI decision failed: ${e.message}")
        }

        // Parse the response based on output format
        val output = parseResponse(response, config.outputFormat)

        return AiDecisionResult.success(output, response)
    }

    private fun buildPrompt(userPrompt: String, outputFormat: AiOutputFormat): String {
        return when (outputFormat) {
            AiOutputFormat.TEXT -> userPrompt
            AiOutputFormat.JSON -> """
                |$userPrompt
                |
                |IMPORTANT: Respond with valid JSON only. No explanation or markdown, just the JSON object.
            """.trimMargin()
            AiOutputFormat.BOOLEAN -> """
                |$userPrompt
                |
                |IMPORTANT: Respond with only "true" or "false". No other text.
            """.trimMargin()
        }
    }

    private fun parseResponse(response: String, outputFormat: AiOutputFormat): JsonElement {
        val cleaned = response.trim()

        return when (outputFormat) {
            AiOutputFormat.TEXT -> JsonPrimitive(cleaned)
            AiOutputFormat.JSON -> {
                try {
                    // Try to extract JSON from response
                    val jsonStart = cleaned.indexOf('{')
                    val jsonEnd = cleaned.lastIndexOf('}')
                    if (jsonStart >= 0 && jsonEnd > jsonStart) {
                        json.parseToJsonElement(cleaned.substring(jsonStart, jsonEnd + 1))
                    } else {
                        // Try array
                        val arrayStart = cleaned.indexOf('[')
                        val arrayEnd = cleaned.lastIndexOf(']')
                        if (arrayStart >= 0 && arrayEnd > arrayStart) {
                            json.parseToJsonElement(cleaned.substring(arrayStart, arrayEnd + 1))
                        } else {
                            JsonPrimitive(cleaned)
                        }
                    }
                } catch (e: Exception) {
                    logger.warn { "Failed to parse AI response as JSON: ${e.message}" }
                    JsonPrimitive(cleaned)
                }
            }
            AiOutputFormat.BOOLEAN -> {
                val lower = cleaned.lowercase()
                val boolValue = when {
                    lower.contains("true") -> true
                    lower.contains("yes") -> true
                    lower.contains("false") -> false
                    lower.contains("no") -> false
                    else -> cleaned.isNotBlank()
                }
                JsonPrimitive(boolValue)
            }
        }
    }

    suspend fun isAvailable(): Boolean = ollamaClient.isAvailable()
}

data class AiDecisionResult(
    val success: Boolean,
    val output: JsonElement?,
    val rawResponse: String?,
    val error: String?
) {
    companion object {
        fun success(output: JsonElement, rawResponse: String) = AiDecisionResult(
            success = true,
            output = output,
            rawResponse = rawResponse,
            error = null
        )

        fun failure(error: String) = AiDecisionResult(
            success = false,
            output = null,
            rawResponse = null,
            error = error
        )
    }
}
