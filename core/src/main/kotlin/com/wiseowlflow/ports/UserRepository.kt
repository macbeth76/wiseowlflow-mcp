package com.wiseowlflow.ports

import com.wiseowlflow.domain.AuthProvider
import com.wiseowlflow.domain.User

interface UserRepository {
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findByExternalId(provider: AuthProvider, externalId: String): User?
    suspend fun create(user: User): User
    suspend fun update(user: User): User
    suspend fun delete(id: String): Boolean
}
