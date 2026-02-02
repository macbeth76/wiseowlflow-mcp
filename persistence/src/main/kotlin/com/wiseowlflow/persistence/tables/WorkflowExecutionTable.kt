package com.wiseowlflow.persistence.tables

import com.wiseowlflow.domain.ExecutionStatus
import com.wiseowlflow.domain.StepStatus
import com.wiseowlflow.domain.StepType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object WorkflowExecutionTable : Table("workflow_executions") {
    private val json = Json { ignoreUnknownKeys = true }

    val id = varchar("id", 36)
    val workflowId = varchar("workflow_id", 36).references(WorkflowTable.id).index()
    val userId = varchar("user_id", 36).references(UserTable.id).index()
    val status = enumerationByName<ExecutionStatus>("status", 20).index()
    val triggerType = varchar("trigger_type", 50)
    val input = jsonb<JsonObject>("input", json)
    val output = jsonb<JsonElement>("output", json).nullable()
    val error = jsonb<String>("error", json).nullable()
    val stateSnapshot = jsonb<JsonObject>("state_snapshot", json)
    val currentStepId = varchar("current_step_id", 100).nullable()
    val startedAt = timestamp("started_at").index()
    val completedAt = timestamp("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object WorkflowExecutionStepTable : Table("workflow_execution_steps") {
    private val json = Json { ignoreUnknownKeys = true }

    val id = varchar("id", 36)
    val executionId = varchar("execution_id", 36).references(WorkflowExecutionTable.id).index()
    val stepId = varchar("step_id", 100)
    val stepName = varchar("step_name", 255).nullable()
    val stepType = enumerationByName<StepType>("step_type", 20)
    val status = enumerationByName<StepStatus>("status", 20)
    val input = jsonb<JsonObject>("input", json)
    val output = jsonb<JsonElement>("output", json).nullable()
    val error = jsonb<String>("error", json).nullable()
    val retryCount = integer("retry_count").default(0)
    val startedAt = timestamp("started_at")
    val completedAt = timestamp("completed_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_exec_steps_execution_step", executionId, stepId)
    }
}
