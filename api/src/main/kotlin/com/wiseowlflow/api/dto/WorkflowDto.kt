package com.wiseowlflow.api.dto

import com.wiseowlflow.domain.ExecutionStatus
import com.wiseowlflow.domain.WorkflowDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class CreateWorkflowRequest(
    val name: String,
    val description: String? = null,
    val definition: WorkflowDefinition,
    val enabled: Boolean = true
)

@Serializable
data class UpdateWorkflowRequest(
    val name: String? = null,
    val description: String? = null,
    val definition: WorkflowDefinition? = null,
    val enabled: Boolean? = null
)

@Serializable
data class WorkflowResponse(
    val id: String,
    val name: String,
    val description: String?,
    val definition: WorkflowDefinition,
    val enabled: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class ExecuteWorkflowRequest(
    val inputs: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class WorkflowExecutionResponse(
    val id: String,
    val workflowId: String,
    val status: ExecutionStatus,
    val triggerType: String,
    val input: JsonObject,
    val output: JsonElement?,
    val error: ErrorResponse?,
    val currentStepId: String?,
    val startedAt: String,
    val completedAt: String?
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val stepId: String? = null
)

@Serializable
data class WorkflowListResponse(
    val workflows: List<WorkflowResponse>,
    val total: Int
)

@Serializable
data class ExecutionListResponse(
    val executions: List<WorkflowExecutionResponse>,
    val total: Int
)
