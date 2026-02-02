package com.wiseowlflow.api.routes

import com.wiseowlflow.api.dto.*
import com.wiseowlflow.api.dto.ErrorResponse
import com.wiseowlflow.api.plugins.*
import com.wiseowlflow.billing.QuotaCheckResult
import com.wiseowlflow.billing.UsageEnforcer
import com.wiseowlflow.domain.Workflow
import com.wiseowlflow.ports.WorkflowExecutionRepository
import com.wiseowlflow.ports.WorkflowRepository
import com.wiseowlflow.workflow.engine.WorkflowEngine
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import java.util.UUID

fun Route.workflowRoutes(
    workflowRepository: WorkflowRepository,
    executionRepository: WorkflowExecutionRepository,
    workflowEngine: WorkflowEngine,
    usageEnforcer: UsageEnforcer
) {
    authenticate(AUTH_JWT, AUTH_API_KEY) {
        route("/workflows") {
            // List workflows
            get {
                val auth = call.requireUser()
                val enabledOnly = call.request.queryParameters["enabled"]?.toBoolean() ?: false

                val workflows = if (enabledOnly) {
                    workflowRepository.findEnabledByUserId(auth.userId)
                } else {
                    workflowRepository.findByUserId(auth.userId)
                }

                call.respond(WorkflowListResponse(
                    workflows = workflows.map { it.toResponse() },
                    total = workflows.size
                ))
            }

            // Create workflow
            post {
                val auth = call.requireUser()
                val request = call.receive<CreateWorkflowRequest>()

                // Check quota
                when (val check = usageEnforcer.checkWorkflowCreation(auth.userId)) {
                    is QuotaCheckResult.Exceeded -> throw QuotaExceededException(
                        "Workflow limit reached (${check.limit})",
                        "workflows"
                    )
                    is QuotaCheckResult.Error -> throw Exception(check.message)
                    QuotaCheckResult.Allowed -> {}
                }

                // Check for duplicate name
                val existing = workflowRepository.findByUserIdAndName(auth.userId, request.name)
                if (existing != null) {
                    throw ConflictException("Workflow with name '${request.name}' already exists")
                }

                val now = Clock.System.now()
                val workflow = Workflow(
                    id = UUID.randomUUID().toString(),
                    userId = auth.userId,
                    name = request.name,
                    description = request.description,
                    definition = request.definition,
                    enabled = request.enabled,
                    createdAt = now,
                    updatedAt = now
                )

                val created = workflowRepository.create(workflow)
                call.respond(HttpStatusCode.Created, created.toResponse())
            }

            // Get workflow by ID
            get("/{id}") {
                val auth = call.requireUser()
                val workflowId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing workflow ID")

                val workflow = workflowRepository.findById(workflowId)
                    ?: throw NotFoundException("Workflow not found")

                if (workflow.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                call.respond(workflow.toResponse())
            }

            // Update workflow
            put("/{id}") {
                val auth = call.requireUser()
                val workflowId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing workflow ID")
                val request = call.receive<UpdateWorkflowRequest>()

                val workflow = workflowRepository.findById(workflowId)
                    ?: throw NotFoundException("Workflow not found")

                if (workflow.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                // Check for name conflict if name is being changed
                if (request.name != null && request.name != workflow.name) {
                    val existing = workflowRepository.findByUserIdAndName(auth.userId, request.name)
                    if (existing != null) {
                        throw ConflictException("Workflow with name '${request.name}' already exists")
                    }
                }

                val updated = workflow.copy(
                    name = request.name ?: workflow.name,
                    description = request.description ?: workflow.description,
                    definition = request.definition ?: workflow.definition,
                    enabled = request.enabled ?: workflow.enabled,
                    updatedAt = Clock.System.now()
                )

                workflowRepository.update(updated)
                call.respond(updated.toResponse())
            }

            // Delete workflow
            delete("/{id}") {
                val auth = call.requireUser()
                val workflowId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing workflow ID")

                val workflow = workflowRepository.findById(workflowId)
                    ?: throw NotFoundException("Workflow not found")

                if (workflow.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                workflowRepository.delete(workflowId)
                call.respond(HttpStatusCode.NoContent)
            }

            // Execute workflow
            post("/{id}/execute") {
                val auth = call.requireUser()
                val workflowId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing workflow ID")
                val request = call.receive<ExecuteWorkflowRequest>()

                val workflow = workflowRepository.findById(workflowId)
                    ?: throw NotFoundException("Workflow not found")

                if (workflow.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                if (!workflow.enabled) {
                    throw IllegalArgumentException("Workflow is disabled")
                }

                // Check execution quota
                when (val check = usageEnforcer.checkWorkflowExecution(auth.userId)) {
                    is QuotaCheckResult.Exceeded -> throw QuotaExceededException(
                        "Execution limit reached (${check.limit}/month)",
                        "executions"
                    )
                    is QuotaCheckResult.Error -> throw Exception(check.message)
                    QuotaCheckResult.Allowed -> {}
                }

                // Record the execution
                usageEnforcer.recordExecution(auth.userId)

                val execution = workflowEngine.executeWorkflow(
                    workflow = workflow,
                    input = request.inputs,
                    triggerType = "api"
                )

                call.respond(HttpStatusCode.Accepted, workflowExecutionToResponse(execution))
            }

            // Get workflow executions
            get("/{id}/executions") {
                val auth = call.requireUser()
                val workflowId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing workflow ID")
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val workflow = workflowRepository.findById(workflowId)
                    ?: throw NotFoundException("Workflow not found")

                if (workflow.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                val executions = executionRepository.findByWorkflowId(workflowId, limit, offset)

                call.respond(ExecutionListResponse(
                    executions = executions.map { workflowExecutionToResponse(it) },
                    total = executions.size
                ))
            }
        }

        // Executions routes
        route("/executions") {
            // List user's executions
            get {
                val auth = call.requireUser()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val executions = executionRepository.findByUserId(auth.userId, limit, offset)

                call.respond(ExecutionListResponse(
                    executions = executions.map { workflowExecutionToResponse(it) },
                    total = executions.size
                ))
            }

            // Get execution by ID
            get("/{id}") {
                val auth = call.requireUser()
                val executionId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing execution ID")

                val execution = executionRepository.findById(executionId)
                    ?: throw NotFoundException("Execution not found")

                if (execution.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                call.respond(workflowExecutionToResponse(execution))
            }

            // Cancel execution
            post("/{id}/cancel") {
                val auth = call.requireUser()
                val executionId = call.parameters["id"]
                    ?: throw IllegalArgumentException("Missing execution ID")

                val execution = executionRepository.findById(executionId)
                    ?: throw NotFoundException("Execution not found")

                if (execution.userId != auth.userId) {
                    throw ForbiddenException("Access denied")
                }

                val canceled = workflowEngine.cancelExecution(executionId)
                if (!canceled) {
                    throw IllegalArgumentException("Execution cannot be canceled")
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "Execution canceled"))
            }
        }
    }
}

private fun Workflow.toResponse() = WorkflowResponse(
    id = id,
    name = name,
    description = description,
    definition = definition,
    enabled = enabled,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)

private fun workflowExecutionToResponse(execution: com.wiseowlflow.domain.WorkflowExecution): WorkflowExecutionResponse {
    val execError = execution.error
    return WorkflowExecutionResponse(
        id = execution.id,
        workflowId = execution.workflowId,
        status = execution.status,
        triggerType = execution.triggerType,
        input = execution.input,
        output = execution.output,
        error = execError?.let { err -> ErrorResponse(err.code, err.message, err.stepId) },
        currentStepId = execution.currentStepId,
        startedAt = execution.startedAt.toString(),
        completedAt = execution.completedAt?.toString()
    )
}
