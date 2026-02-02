package com.wiseowlflow.workflow.engine

import com.wiseowlflow.domain.*
import com.wiseowlflow.mcp.routing.ToolRouter
import com.wiseowlflow.ports.WorkflowExecutionRepository
import com.wiseowlflow.ports.WorkflowExecutionStepRepository
import com.wiseowlflow.workflow.ai.AiDecisionMaker
import com.wiseowlflow.workflow.state.WorkflowState
import com.wiseowlflow.workflow.state.WorkflowStateMachineBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import java.util.UUID

private val logger = KotlinLogging.logger {}

class WorkflowEngine(
    private val executionRepository: WorkflowExecutionRepository,
    private val stepRepository: WorkflowExecutionStepRepository,
    private val toolRouter: ToolRouter?,
    private val aiDecisionMaker: AiDecisionMaker?
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val stepExecutor = StepExecutor(toolRouter, aiDecisionMaker)
    private val activeExecutions = mutableMapOf<String, Job>()

    suspend fun executeWorkflow(
        workflow: Workflow,
        input: JsonObject = JsonObject(emptyMap()),
        triggerType: String = "manual"
    ): WorkflowExecution {
        val executionId = UUID.randomUUID().toString()
        val now = Clock.System.now()

        val execution = WorkflowExecution(
            id = executionId,
            workflowId = workflow.id,
            userId = workflow.userId,
            status = ExecutionStatus.PENDING,
            triggerType = triggerType,
            input = input,
            stateSnapshot = JsonObject(emptyMap()),
            startedAt = now
        )

        executionRepository.create(execution)
        logger.info { "Created workflow execution: $executionId for workflow: ${workflow.name}" }

        // Start execution in coroutine
        val job = CoroutineScope(Dispatchers.Default).launch {
            runExecution(execution, workflow)
        }
        activeExecutions[executionId] = job

        return execution
    }

    private suspend fun runExecution(execution: WorkflowExecution, workflow: Workflow) {
        val context = ExecutionContext(
            executionId = execution.id,
            workflowId = workflow.id,
            userId = workflow.userId,
            inputs = execution.input
        )

        // Initialize variables from workflow definition
        workflow.definition.variables.forEach { (key, value) ->
            context.setVariable(key, value)
        }

        var currentExecution = execution.copy(status = ExecutionStatus.RUNNING)
        executionRepository.update(currentExecution)

        val steps = workflow.definition.steps
        var currentStepIndex = 0

        try {
            while (currentStepIndex < steps.size) {
                val step = steps[currentStepIndex]

                // Update current step
                currentExecution = currentExecution.copy(currentStepId = step.id)
                executionRepository.update(currentExecution)

                // Create step execution record
                val stepExecution = WorkflowExecutionStep(
                    id = UUID.randomUUID().toString(),
                    executionId = execution.id,
                    stepId = step.id,
                    stepName = step.name,
                    stepType = step.type,
                    status = StepStatus.RUNNING,
                    input = buildJsonObject {
                        step.config.let { config ->
                            put("config", json.encodeToJsonElement(StepConfig.serializer(), config))
                        }
                    },
                    startedAt = Clock.System.now()
                )
                stepRepository.create(stepExecution)

                // Execute step with retry
                val result = executeStepWithRetry(step, context, stepExecution)

                when (result) {
                    is StepResult.Success -> {
                        context.setStepOutput(step.id, result.output)
                        stepRepository.update(
                            stepExecution.copy(
                                status = StepStatus.COMPLETED,
                                output = result.output,
                                completedAt = Clock.System.now()
                            )
                        )

                        // Handle conditional branching
                        if (step.type == StepType.CONDITION) {
                            val conditionOutput = result.output as? JsonObject
                            val nextSteps = conditionOutput?.get("next_steps") as? JsonArray
                            // For now, continue linearly - full branching would be more complex
                        }

                        currentStepIndex++
                    }
                    is StepResult.Skip -> {
                        stepRepository.update(
                            stepExecution.copy(
                                status = StepStatus.SKIPPED,
                                output = JsonPrimitive(result.reason),
                                completedAt = Clock.System.now()
                            )
                        )
                        currentStepIndex++
                    }
                    is StepResult.Failure -> {
                        stepRepository.update(
                            stepExecution.copy(
                                status = StepStatus.FAILED,
                                error = ExecutionError(
                                    code = "STEP_FAILED",
                                    message = result.error,
                                    stepId = step.id
                                ),
                                completedAt = Clock.System.now()
                            )
                        )
                        throw WorkflowExecutionException(step.id, result.error)
                    }
                }
            }

            // Workflow completed successfully
            val finalOutput = context.stepOutputs[steps.lastOrNull()?.id]
            currentExecution = currentExecution.copy(
                status = ExecutionStatus.COMPLETED,
                output = finalOutput,
                stateSnapshot = buildStateSnapshot(context),
                completedAt = Clock.System.now()
            )
            executionRepository.update(currentExecution)
            logger.info { "Workflow execution completed: ${execution.id}" }

        } catch (e: WorkflowExecutionException) {
            currentExecution = currentExecution.copy(
                status = ExecutionStatus.FAILED,
                error = ExecutionError(
                    code = "WORKFLOW_FAILED",
                    message = e.message ?: "Unknown error",
                    stepId = e.stepId
                ),
                stateSnapshot = buildStateSnapshot(context),
                completedAt = Clock.System.now()
            )
            executionRepository.update(currentExecution)
            logger.error { "Workflow execution failed: ${execution.id} - ${e.message}" }

        } catch (e: CancellationException) {
            currentExecution = currentExecution.copy(
                status = ExecutionStatus.CANCELED,
                stateSnapshot = buildStateSnapshot(context),
                completedAt = Clock.System.now()
            )
            executionRepository.update(currentExecution)
            logger.info { "Workflow execution canceled: ${execution.id}" }

        } catch (e: Exception) {
            currentExecution = currentExecution.copy(
                status = ExecutionStatus.FAILED,
                error = ExecutionError(
                    code = "UNEXPECTED_ERROR",
                    message = e.message ?: "Unknown error"
                ),
                stateSnapshot = buildStateSnapshot(context),
                completedAt = Clock.System.now()
            )
            executionRepository.update(currentExecution)
            logger.error(e) { "Workflow execution failed unexpectedly: ${execution.id}" }

        } finally {
            activeExecutions.remove(execution.id)
        }
    }

    private suspend fun executeStepWithRetry(
        step: WorkflowStep,
        context: ExecutionContext,
        stepExecution: WorkflowExecutionStep
    ): StepResult {
        val retryPolicy = step.retryPolicy ?: RetryPolicy()
        var lastResult: StepResult = StepResult.Failure("Not executed")
        var currentDelay = retryPolicy.initialDelay

        repeat(retryPolicy.maxRetries + 1) { attempt ->
            if (attempt > 0) {
                logger.debug { "Retrying step ${step.id}, attempt ${attempt + 1}" }
                delay(currentDelay)
                currentDelay = (currentDelay * retryPolicy.backoffMultiplier)
                    .toLong()
                    .coerceAtMost(retryPolicy.maxDelay)

                stepRepository.update(
                    stepExecution.copy(retryCount = attempt)
                )
            }

            lastResult = stepExecutor.execute(step, context)

            when (lastResult) {
                is StepResult.Success, is StepResult.Skip -> return lastResult
                is StepResult.Failure -> {
                    if (!lastResult.retryable) return lastResult
                }
            }
        }

        return lastResult
    }

    private fun buildStateSnapshot(context: ExecutionContext): JsonObject {
        return buildJsonObject {
            putJsonObject("variables") {
                context.variables.forEach { (key, value) ->
                    put(key, value)
                }
            }
            putJsonObject("step_outputs") {
                context.stepOutputs.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
    }

    suspend fun cancelExecution(executionId: String): Boolean {
        val job = activeExecutions[executionId]
        if (job != null && job.isActive) {
            job.cancel()
            return true
        }
        return false
    }

    suspend fun getExecutionStatus(executionId: String): WorkflowExecution? {
        return executionRepository.findById(executionId)
    }
}

class WorkflowExecutionException(
    val stepId: String,
    message: String
) : Exception("Step $stepId failed: $message")
