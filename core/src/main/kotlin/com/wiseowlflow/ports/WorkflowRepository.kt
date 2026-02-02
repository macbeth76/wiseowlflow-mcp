package com.wiseowlflow.ports

import com.wiseowlflow.domain.Workflow

interface WorkflowRepository {
    suspend fun findById(id: String): Workflow?
    suspend fun findByUserId(userId: String): List<Workflow>
    suspend fun findByUserIdAndName(userId: String, name: String): Workflow?
    suspend fun findByWebhookPath(path: String): List<Workflow>
    suspend fun findEnabledByUserId(userId: String): List<Workflow>
    suspend fun create(workflow: Workflow): Workflow
    suspend fun update(workflow: Workflow): Workflow
    suspend fun delete(id: String): Boolean
    suspend fun countByUserId(userId: String): Int
}
