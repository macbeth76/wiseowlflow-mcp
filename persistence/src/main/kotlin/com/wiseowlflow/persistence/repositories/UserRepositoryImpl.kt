package com.wiseowlflow.persistence.repositories

import com.wiseowlflow.domain.AuthProvider
import com.wiseowlflow.domain.User
import com.wiseowlflow.persistence.dbQuery
import com.wiseowlflow.persistence.tables.UserTable
import com.wiseowlflow.ports.UserRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class UserRepositoryImpl : UserRepository {

    override suspend fun findById(id: String): User? = dbQuery {
        UserTable.selectAll()
            .where { UserTable.id eq id }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun findByEmail(email: String): User? = dbQuery {
        UserTable.selectAll()
            .where { UserTable.email eq email.lowercase() }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun findByExternalId(provider: AuthProvider, externalId: String): User? = dbQuery {
        UserTable.selectAll()
            .where { (UserTable.authProvider eq provider) and (UserTable.externalId eq externalId) }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun create(user: User): User = dbQuery {
        UserTable.insert {
            it[id] = user.id
            it[email] = user.email.lowercase()
            it[passwordHash] = user.passwordHash
            it[authProvider] = user.authProvider
            it[externalId] = user.externalId
            it[name] = user.name
            it[avatarUrl] = user.avatarUrl
            it[emailVerified] = user.emailVerified
            it[createdAt] = user.createdAt
            it[updatedAt] = user.updatedAt
        }
        user
    }

    override suspend fun update(user: User): User = dbQuery {
        UserTable.update({ UserTable.id eq user.id }) {
            it[email] = user.email.lowercase()
            it[passwordHash] = user.passwordHash
            it[name] = user.name
            it[avatarUrl] = user.avatarUrl
            it[emailVerified] = user.emailVerified
            it[updatedAt] = user.updatedAt
        }
        user
    }

    override suspend fun delete(id: String): Boolean = dbQuery {
        UserTable.deleteWhere { UserTable.id eq id } > 0
    }

    private fun ResultRow.toUser() = User(
        id = this[UserTable.id],
        email = this[UserTable.email],
        passwordHash = this[UserTable.passwordHash],
        authProvider = this[UserTable.authProvider],
        externalId = this[UserTable.externalId],
        name = this[UserTable.name],
        avatarUrl = this[UserTable.avatarUrl],
        emailVerified = this[UserTable.emailVerified],
        createdAt = this[UserTable.createdAt],
        updatedAt = this[UserTable.updatedAt]
    )
}
