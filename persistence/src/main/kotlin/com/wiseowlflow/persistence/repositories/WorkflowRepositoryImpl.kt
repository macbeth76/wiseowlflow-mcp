package com.wiseowlflow.persistence.repositories

import com.wiseowlflow.domain.Workflow
import com.wiseowlflow.domain.WorkflowTrigger
import com.wiseowlflow.persistence.dbQuery
import com.wiseowlflow.persistence.tables.WorkflowTable
import com.wiseowlflow.ports.WorkflowRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class WorkflowRepositoryImpl : WorkflowRepository {

    override suspend fun findById(id: String): Workflow? = dbQuery {
        WorkflowTable.selectAll()
            .where { WorkflowTable.id eq id }
            .map { it.toWorkflow() }
            .singleOrNull()
    }

    override suspend fun findByUserId(userId: String): List<Workflow> = dbQuery {
        WorkflowTable.selectAll()
            .where { WorkflowTable.userId eq userId }
            .orderBy(WorkflowTable.name)
            .map { it.toWorkflow() }
    }

    override suspend fun findByUserIdAndName(userId: String, name: String): Workflow? = dbQuery {
        WorkflowTable.selectAll()
            .where { (WorkflowTable.userId eq userId) and (WorkflowTable.name eq name) }
            .map { it.toWorkflow() }
            .singleOrNull()
    }

    override suspend fun findByWebhookPath(path: String): List<Workflow> = dbQuery {
        // This is a simplified implementation - in production you'd want a proper index
        WorkflowTable.selectAll()
            .where { WorkflowTable.enabled eq true }
            .map { it.toWorkflow() }
            .filter { workflow ->
                val trigger = workflow.definition.trigger
                trigger is WorkflowTrigger.Webhook && trigger.path == path
            }
    }

    override suspend fun findEnabledByUserId(userId: String): List<Workflow> = dbQuery {
        WorkflowTable.selectAll()
            .where { (WorkflowTable.userId eq userId) and (WorkflowTable.enabled eq true) }
            .orderBy(WorkflowTable.name)
            .map { it.toWorkflow() }
    }

    override suspend fun create(workflow: Workflow): Workflow = dbQuery {
        WorkflowTable.insert {
            it[id] = workflow.id
            it[userId] = workflow.userId
            it[name] = workflow.name
            it[description] = workflow.description
            it[definition] = workflow.definition
            it[enabled] = workflow.enabled
            it[createdAt] = workflow.createdAt
            it[updatedAt] = workflow.updatedAt
        }
        workflow
    }

    override suspend fun update(workflow: Workflow): Workflow = dbQuery {
        WorkflowTable.update({ WorkflowTable.id eq workflow.id }) {
            it[name] = workflow.name
            it[description] = workflow.description
            it[definition] = workflow.definition
            it[enabled] = workflow.enabled
            it[updatedAt] = workflow.updatedAt
        }
        workflow
    }

    override suspend fun delete(id: String): Boolean = dbQuery {
        WorkflowTable.deleteWhere { WorkflowTable.id eq id } > 0
    }

    override suspend fun countByUserId(userId: String): Int = dbQuery {
        WorkflowTable.selectAll()
            .where { WorkflowTable.userId eq userId }
            .count().toInt()
    }

    private fun ResultRow.toWorkflow() = Workflow(
        id = this[WorkflowTable.id],
        userId = this[WorkflowTable.userId],
        name = this[WorkflowTable.name],
        description = this[WorkflowTable.description],
        definition = this[WorkflowTable.definition],
        enabled = this[WorkflowTable.enabled],
        createdAt = this[WorkflowTable.createdAt],
        updatedAt = this[WorkflowTable.updatedAt]
    )
}
