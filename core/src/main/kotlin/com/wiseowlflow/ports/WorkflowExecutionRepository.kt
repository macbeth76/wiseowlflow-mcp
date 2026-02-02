package com.wiseowlflow.ports

import com.wiseowlflow.domain.ExecutionStatus
import com.wiseowlflow.domain.WorkflowExecution
import com.wiseowlflow.domain.WorkflowExecutionStep
import kotlinx.datetime.Instant

interface WorkflowExecutionRepository {
    suspend fun findById(id: String): WorkflowExecution?
    suspend fun findByWorkflowId(workflowId: String, limit: Int = 100, offset: Int = 0): List<WorkflowExecution>
    suspend fun findByUserId(userId: String, limit: Int = 100, offset: Int = 0): List<WorkflowExecution>
    suspend fun findByStatus(status: ExecutionStatus): List<WorkflowExecution>
    suspend fun findPendingOrRunning(): List<WorkflowExecution>
    suspend fun create(execution: WorkflowExecution): WorkflowExecution
    suspend fun update(execution: WorkflowExecution): WorkflowExecution
    suspend fun countByUserIdAndPeriod(userId: String, start: Instant, end: Instant): Int
}

interface WorkflowExecutionStepRepository {
    suspend fun findById(id: String): WorkflowExecutionStep?
    suspend fun findByExecutionId(executionId: String): List<WorkflowExecutionStep>
    suspend fun create(step: WorkflowExecutionStep): WorkflowExecutionStep
    suspend fun update(step: WorkflowExecutionStep): WorkflowExecutionStep
}
