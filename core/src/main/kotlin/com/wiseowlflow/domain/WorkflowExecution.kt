package com.wiseowlflow.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class WorkflowExecution(
    val id: String,
    val workflowId: String,
    val userId: String,
    val status: ExecutionStatus,
    val triggerType: String,
    val input: JsonObject = JsonObject(emptyMap()),
    val output: JsonElement? = null,
    val error: ExecutionError? = null,
    val stateSnapshot: JsonObject = JsonObject(emptyMap()),
    val currentStepId: String? = null,
    val startedAt: Instant,
    val completedAt: Instant? = null
)

@Serializable
enum class ExecutionStatus {
    PENDING,
    RUNNING,
    PAUSED,
    WAITING,
    COMPLETED,
    FAILED,
    CANCELED
}

@Serializable
data class ExecutionError(
    val code: String,
    val message: String,
    val stepId: String? = null,
    val details: JsonObject? = null
)

@Serializable
data class WorkflowExecutionStep(
    val id: String,
    val executionId: String,
    val stepId: String,
    val stepName: String?,
    val stepType: StepType,
    val status: StepStatus,
    val input: JsonObject = JsonObject(emptyMap()),
    val output: JsonElement? = null,
    val error: ExecutionError? = null,
    val retryCount: Int = 0,
    val startedAt: Instant,
    val completedAt: Instant? = null
)

@Serializable
enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELED
}

@Serializable
data class ExecutionContext(
    val executionId: String,
    val workflowId: String,
    val userId: String,
    val inputs: JsonObject,
    val variables: MutableMap<String, JsonElement> = mutableMapOf(),
    val stepOutputs: MutableMap<String, JsonElement> = mutableMapOf()
) {
    fun setStepOutput(stepId: String, output: JsonElement) {
        stepOutputs[stepId] = output
    }

    fun getStepOutput(stepId: String): JsonElement? = stepOutputs[stepId]

    fun setVariable(name: String, value: JsonElement) {
        variables[name] = value
    }

    fun getVariable(name: String): JsonElement? = variables[name]
}
