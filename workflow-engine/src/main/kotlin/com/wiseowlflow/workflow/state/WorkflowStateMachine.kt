package com.wiseowlflow.workflow.state

import com.wiseowlflow.domain.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import ru.nsk.kstatemachine.*
import ru.nsk.kstatemachine.event.*
import ru.nsk.kstatemachine.state.*
import ru.nsk.kstatemachine.statemachine.*
import ru.nsk.kstatemachine.transition.*

private val logger = KotlinLogging.logger {}

// Events - using object-based events for KStateMachine
object StartEvent : Event
object PauseEvent : Event
object ResumeEvent : Event
object CancelEvent : Event
object CompleteEvent : Event
data class StepCompletedEvent(val stepId: String, val output: Any?) : Event
data class StepFailedEvent(val stepId: String, val error: String) : Event

// States as sealed class with DefaultState
sealed class WorkflowState(name: String? = null) : DefaultState(name) {
    object Idle : WorkflowState("idle")
    object Running : WorkflowState("running")
    object Paused : WorkflowState("paused")
    object Completed : WorkflowState("completed"), FinalState
    object Failed : WorkflowState("failed"), FinalState
    object Canceled : WorkflowState("canceled"), FinalState
}

class WorkflowStateMachineBuilder(
    private val workflowDefinition: WorkflowDefinition
) {
    suspend fun build(scope: CoroutineScope, onStateChange: suspend (WorkflowState) -> Unit): StateMachine {
        return createStateMachine(
            scope = scope,
            name = "Workflow-${workflowDefinition.name}"
        ) {
            addInitialState(WorkflowState.Idle) {
                transition<StartEvent>(targetState = WorkflowState.Running)
            }

            addState(WorkflowState.Running) {
                transition<StepCompletedEvent>(targetState = WorkflowState.Completed)
                transition<StepFailedEvent>(targetState = WorkflowState.Failed)
                transition<PauseEvent>(targetState = WorkflowState.Paused)
                transition<CancelEvent>(targetState = WorkflowState.Canceled)
                transition<CompleteEvent>(targetState = WorkflowState.Completed)
            }

            addState(WorkflowState.Paused) {
                transition<ResumeEvent>(targetState = WorkflowState.Running)
                transition<CancelEvent>(targetState = WorkflowState.Canceled)
            }

            addFinalState(WorkflowState.Completed)
            addFinalState(WorkflowState.Failed)
            addFinalState(WorkflowState.Canceled)

            onStateEntry { state, _ ->
                // Log using println to avoid scope conflicts with KStateMachine DSL
                println("Workflow entered state: ${state.name}")
                if (state is WorkflowState) {
                    onStateChange(state)
                }
            }
        }
    }
}

data class WorkflowStateSnapshot(
    val stateName: String,
    val currentStepId: String?,
    val completedSteps: Set<String>,
    val stepOutputs: Map<String, Any?>,
    val variables: Map<String, Any?>
)

class WorkflowStateManager {
    private var currentStepId: String? = null
    private var lastStepId: String? = null

    fun setCurrentStep(stepId: String?) {
        lastStepId = currentStepId
        currentStepId = stepId
    }

    fun createSnapshot(
        state: WorkflowState,
        context: ExecutionContext
    ): WorkflowStateSnapshot {
        return WorkflowStateSnapshot(
            stateName = state.name ?: "Unknown",
            currentStepId = currentStepId,
            completedSteps = context.stepOutputs.keys,
            stepOutputs = context.stepOutputs.mapValues { it.value.toString() },
            variables = context.variables.mapValues { it.value.toString() }
        )
    }
}
