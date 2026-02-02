package com.wiseowlflow.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object ApiKeyTable : Table("api_keys") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UserTable.id).index()
    val name = varchar("name", 255)
    val keyHash = varchar("key_hash", 255).uniqueIndex()
    val keyPrefix = varchar("key_prefix", 20)
    val scopes = text("scopes") // Comma-separated list
    val lastUsedAt = timestamp("last_used_at").nullable()
    val expiresAt = timestamp("expires_at").nullable()
    val createdAt = timestamp("created_at")
    val revokedAt = timestamp("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
