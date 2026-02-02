package com.wiseowlflow.workflow.dsl

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.wiseowlflow.domain.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

class WorkflowParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false
        )
    )

    fun parseJson(jsonString: String): WorkflowDefinition {
        return json.decodeFromString(WorkflowDefinition.serializer(), jsonString)
    }

    fun parseYaml(yamlString: String): WorkflowDefinition {
        return yaml.decodeFromString(WorkflowDefinition.serializer(), yamlString)
    }

    fun parse(content: String): WorkflowDefinition {
        // Try to detect if it's JSON or YAML
        val trimmed = content.trim()
        return if (trimmed.startsWith("{")) {
            parseJson(content)
        } else {
            parseYaml(content)
        }
    }

    fun toJson(definition: WorkflowDefinition): String {
        return json.encodeToString(WorkflowDefinition.serializer(), definition)
    }

    fun toYaml(definition: WorkflowDefinition): String {
        return yaml.encodeToString(WorkflowDefinition.serializer(), definition)
    }
}

object ExpressionEvaluator {
    private val variablePattern = Regex("""\$\{([^}]+)\}""")

    fun evaluate(expression: String, context: ExecutionContext): String {
        return variablePattern.replace(expression) { match ->
            val path = match.groupValues[1]
            resolveValue(path, context)?.toString() ?: ""
        }
    }

    fun evaluateToJsonElement(expression: String, context: ExecutionContext): JsonElement {
        val resolved = evaluate(expression, context)
        return try {
            Json.parseToJsonElement(resolved)
        } catch (e: Exception) {
            JsonPrimitive(resolved)
        }
    }

    fun evaluateCondition(expression: String, context: ExecutionContext): Boolean {
        val evaluated = evaluate(expression, context)
        return when (evaluated.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no", "" -> false
            else -> evaluated.isNotBlank()
        }
    }

    private fun resolveValue(path: String, context: ExecutionContext): Any? {
        val parts = path.split(".")

        return when (parts[0]) {
            "inputs" -> {
                var current: JsonElement = context.inputs
                for (i in 1 until parts.size) {
                    current = (current as? JsonObject)?.get(parts[i]) ?: return null
                }
                jsonElementToValue(current)
            }
            "steps" -> {
                if (parts.size < 2) return null
                val stepId = parts[1]
                var current: JsonElement = context.getStepOutput(stepId) ?: return null

                for (i in 2 until parts.size) {
                    current = when (current) {
                        is JsonObject -> current[parts[i]] ?: return null
                        is JsonArray -> current.getOrNull(parts[i].toIntOrNull() ?: return null) ?: return null
                        else -> return null
                    }
                }
                jsonElementToValue(current)
            }
            "variables" -> {
                if (parts.size < 2) return null
                val varName = parts[1]
                var current: JsonElement = context.getVariable(varName) ?: return null

                for (i in 2 until parts.size) {
                    current = (current as? JsonObject)?.get(parts[i]) ?: return null
                }
                jsonElementToValue(current)
            }
            else -> null
        }
    }

    private fun jsonElementToValue(element: JsonElement): Any? = when (element) {
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.intOrNull != null -> element.int
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
        is JsonObject -> element.toString()
        is JsonArray -> element.toString()
        JsonNull -> null
    }
}
