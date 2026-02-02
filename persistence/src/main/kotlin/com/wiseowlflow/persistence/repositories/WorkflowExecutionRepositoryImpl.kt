package com.wiseowlflow.persistence.repositories

import com.wiseowlflow.domain.*
import com.wiseowlflow.persistence.dbQuery
import com.wiseowlflow.persistence.tables.WorkflowExecutionStepTable
import com.wiseowlflow.persistence.tables.WorkflowExecutionTable
import com.wiseowlflow.ports.WorkflowExecutionRepository
import com.wiseowlflow.ports.WorkflowExecutionStepRepository
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class WorkflowExecutionRepositoryImpl : WorkflowExecutionRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun findById(id: String): WorkflowExecution? = dbQuery {
        WorkflowExecutionTable.selectAll()
            .where { WorkflowExecutionTable.id eq id }
            .map { it.toWorkflowExecution() }
            .singleOrNull()
    }

    override suspend fun findByWorkflowId(workflowId: String, limit: Int, offset: Int): List<WorkflowExecution> = dbQuery {
        WorkflowExecutionTable.selectAll()
            .where { WorkflowExecutionTable.workflowId eq workflowId }
            .orderBy(WorkflowExecutionTable.startedAt, SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toWorkflowExecution() }
    }

    override suspend fun findByUserId(userId: String, limit: Int, offset: Int): List<WorkflowExecution> = dbQuery {
        WorkflowExecutionTable.selectAll()
            .where { WorkflowExecutionTable.userId eq userId }
            .orderBy(WorkflowExecutionTable.startedAt, SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toWorkflowExecution() }
    }

    override suspend fun findByStatus(status: ExecutionStatus): List<WorkflowExecution> = dbQuery {
        WorkflowExecutionTable.selectAll()
            .where { WorkflowExecutionTable.status eq status }
            .orderBy(WorkflowExecutionTable.startedAt, SortOrder.DESC)
            .map { it.toWorkflowExecution() }
    }

    override suspend fun findPendingOrRunning(): List<WorkflowExecution> = dbQuery {
        WorkflowExecutionTable.selectAll()
            .where {
                (WorkflowExecutionTable.status eq ExecutionStatus.PENDING) or
                    (WorkflowExecutionTable.status eq ExecutionStatus.RUNNING)
            }
            .orderBy(WorkflowExecutionTable.startedAt, SortOrder.ASC)
            .map { it.toWorkflowExecution() }
    }

    override suspend fun create(execution: WorkflowExecution): WorkflowExecution = dbQuery {
        WorkflowExecutionTable.insert {
            it[id] = execution.id
            it[workflowId] = execution.workflowId
            it[userId] = execution.userId
            it[status] = execution.status
            it[triggerType] = execution.triggerType
            it[input] = execution.input
            it[output] = execution.output
            it[error] = execution.error?.let { err -> json.encodeToString(ExecutionError.serializer(), err) }
            it[stateSnapshot] = execution.stateSnapshot
            it[currentStepId] = execution.currentStepId
            it[startedAt] = execution.startedAt
            it[completedAt] = execution.completedAt
        }
        execution
    }

    override suspend fun update(execution: WorkflowExecution): WorkflowExecution = dbQuery {
        WorkflowExecutionTable.update({ WorkflowExecutionTable.id eq execution.id }) {
            it[status] = execution.status
            it[output] = execution.output
            it[error] = execution.error?.let { err -> json.encodeToString(ExecutionError.serializer(), err) }
            it[stateSnapshot] = execution.stateSnapshot
            it[currentStepId] = execution.currentStepId
            it[completedAt] = execution.completedAt
        }
        execution
    }

    override suspend fun countByUserIdAndPeriod(userId: String, start: Instant, end: Instant): Int = dbQuery {
        WorkflowExecutionTable.selectAll()
            .where {
                (WorkflowExecutionTable.userId eq userId) and
                    (WorkflowExecutionTable.startedAt greaterEq start) and
                    (WorkflowExecutionTable.startedAt less end)
            }
            .count().toInt()
    }

    private fun ResultRow.toWorkflowExecution(): WorkflowExecution {
        val errorStr = this[WorkflowExecutionTable.error]
        val error = errorStr?.let { json.decodeFromString(ExecutionError.serializer(), it) }

        return WorkflowExecution(
            id = this[WorkflowExecutionTable.id],
            workflowId = this[WorkflowExecutionTable.workflowId],
            userId = this[WorkflowExecutionTable.userId],
            status = this[WorkflowExecutionTable.status],
            triggerType = this[WorkflowExecutionTable.triggerType],
            input = this[WorkflowExecutionTable.input],
            output = this[WorkflowExecutionTable.output],
            error = error,
            stateSnapshot = this[WorkflowExecutionTable.stateSnapshot],
            currentStepId = this[WorkflowExecutionTable.currentStepId],
            startedAt = this[WorkflowExecutionTable.startedAt],
            completedAt = this[WorkflowExecutionTable.completedAt]
        )
    }
}

class WorkflowExecutionStepRepositoryImpl : WorkflowExecutionStepRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun findById(id: String): WorkflowExecutionStep? = dbQuery {
        WorkflowExecutionStepTable.selectAll()
            .where { WorkflowExecutionStepTable.id eq id }
            .map { it.toWorkflowExecutionStep() }
            .singleOrNull()
    }

    override suspend fun findByExecutionId(executionId: String): List<WorkflowExecutionStep> = dbQuery {
        WorkflowExecutionStepTable.selectAll()
            .where { WorkflowExecutionStepTable.executionId eq executionId }
            .orderBy(WorkflowExecutionStepTable.startedAt)
            .map { it.toWorkflowExecutionStep() }
    }

    override suspend fun create(step: WorkflowExecutionStep): WorkflowExecutionStep = dbQuery {
        WorkflowExecutionStepTable.insert {
            it[id] = step.id
            it[executionId] = step.executionId
            it[stepId] = step.stepId
            it[stepName] = step.stepName
            it[stepType] = step.stepType
            it[status] = step.status
            it[input] = step.input
            it[output] = step.output
            it[error] = step.error?.let { err -> json.encodeToString(ExecutionError.serializer(), err) }
            it[retryCount] = step.retryCount
            it[startedAt] = step.startedAt
            it[completedAt] = step.completedAt
        }
        step
    }

    override suspend fun update(step: WorkflowExecutionStep): WorkflowExecutionStep = dbQuery {
        WorkflowExecutionStepTable.update({ WorkflowExecutionStepTable.id eq step.id }) {
            it[status] = step.status
            it[output] = step.output
            it[error] = step.error?.let { err -> json.encodeToString(ExecutionError.serializer(), err) }
            it[retryCount] = step.retryCount
            it[completedAt] = step.completedAt
        }
        step
    }

    private fun ResultRow.toWorkflowExecutionStep(): WorkflowExecutionStep {
        val errorStr = this[WorkflowExecutionStepTable.error]
        val error = errorStr?.let { json.decodeFromString(ExecutionError.serializer(), it) }

        return WorkflowExecutionStep(
            id = this[WorkflowExecutionStepTable.id],
            executionId = this[WorkflowExecutionStepTable.executionId],
            stepId = this[WorkflowExecutionStepTable.stepId],
            stepName = this[WorkflowExecutionStepTable.stepName],
            stepType = this[WorkflowExecutionStepTable.stepType],
            status = this[WorkflowExecutionStepTable.status],
            input = this[WorkflowExecutionStepTable.input],
            output = this[WorkflowExecutionStepTable.output],
            error = error,
            retryCount = this[WorkflowExecutionStepTable.retryCount],
            startedAt = this[WorkflowExecutionStepTable.startedAt],
            completedAt = this[WorkflowExecutionStepTable.completedAt]
        )
    }
}
