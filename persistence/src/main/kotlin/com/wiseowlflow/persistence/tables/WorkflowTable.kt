package com.wiseowlflow.persistence.tables

import com.wiseowlflow.domain.WorkflowDefinition
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object WorkflowTable : Table("workflows") {
    private val json = Json { ignoreUnknownKeys = true }

    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UserTable.id).index()
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val definition = jsonb<WorkflowDefinition>("definition", json)
    val enabled = bool("enabled").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_workflows_user_name", userId, name)
    }
}
