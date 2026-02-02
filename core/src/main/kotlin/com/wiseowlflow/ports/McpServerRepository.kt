package com.wiseowlflow.ports

import com.wiseowlflow.domain.McpHealthStatus
import com.wiseowlflow.domain.McpServer
import kotlinx.datetime.Instant

interface McpServerRepository {
    suspend fun findById(id: String): McpServer?
    suspend fun findByUserId(userId: String): List<McpServer>
    suspend fun findByUserIdAndName(userId: String, name: String): McpServer?
    suspend fun findAllEnabled(): List<McpServer>
    suspend fun create(server: McpServer): McpServer
    suspend fun update(server: McpServer): McpServer
    suspend fun updateHealthStatus(id: String, status: McpHealthStatus, checkedAt: Instant): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun countByUserId(userId: String): Int
}
