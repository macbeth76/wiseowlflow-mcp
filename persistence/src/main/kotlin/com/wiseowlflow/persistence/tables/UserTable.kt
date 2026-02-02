package com.wiseowlflow.persistence.tables

import com.wiseowlflow.domain.AuthProvider
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object UserTable : Table("users") {
    val id = varchar("id", 36)
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255).nullable()
    val authProvider = enumerationByName<AuthProvider>("auth_provider", 20)
    val externalId = varchar("external_id", 255).nullable()
    val name = varchar("name", 255).nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val emailVerified = bool("email_verified").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_users_provider_external", authProvider, externalId)
    }
}
