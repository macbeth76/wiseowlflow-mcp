package com.wiseowlflow.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.wiseowlflow.domain.AuthProvider
import com.wiseowlflow.domain.User
import com.wiseowlflow.ports.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import java.util.UUID

private val logger = KotlinLogging.logger {}

class PasswordService(
    private val userRepository: UserRepository,
    private val bcryptCost: Int = 12
) {
    fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(bcryptCost, password.toCharArray())
    }

    fun verifyPassword(password: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }

    suspend fun registerUser(
        email: String,
        password: String,
        name: String? = null
    ): Result<User> {
        // Check if user already exists
        val existing = userRepository.findByEmail(email)
        if (existing != null) {
            return Result.failure(UserAlreadyExistsException(email))
        }

        // Validate password
        val validationError = validatePassword(password)
        if (validationError != null) {
            return Result.failure(InvalidPasswordException(validationError))
        }

        val now = Clock.System.now()
        val user = User(
            id = UUID.randomUUID().toString(),
            email = email.lowercase(),
            passwordHash = hashPassword(password),
            authProvider = AuthProvider.EMAIL,
            name = name,
            emailVerified = false,
            createdAt = now,
            updatedAt = now
        )

        val created = userRepository.create(user)
        logger.info { "Registered new user: ${user.email}" }

        return Result.success(created)
    }

    suspend fun authenticateUser(email: String, password: String): Result<User> {
        val user = userRepository.findByEmail(email)
            ?: return Result.failure(InvalidCredentialsException())

        if (user.authProvider != AuthProvider.EMAIL) {
            return Result.failure(WrongAuthProviderException(user.authProvider))
        }

        val passwordHash = user.passwordHash
            ?: return Result.failure(InvalidCredentialsException())

        if (!verifyPassword(password, passwordHash)) {
            return Result.failure(InvalidCredentialsException())
        }

        return Result.success(user)
    }

    suspend fun changePassword(
        userId: String,
        currentPassword: String,
        newPassword: String
    ): Result<Unit> {
        val user = userRepository.findById(userId)
            ?: return Result.failure(UserNotFoundException(userId))

        val passwordHash = user.passwordHash
            ?: return Result.failure(InvalidCredentialsException())

        if (!verifyPassword(currentPassword, passwordHash)) {
            return Result.failure(InvalidCredentialsException())
        }

        val validationError = validatePassword(newPassword)
        if (validationError != null) {
            return Result.failure(InvalidPasswordException(validationError))
        }

        val updated = user.copy(
            passwordHash = hashPassword(newPassword),
            updatedAt = Clock.System.now()
        )
        userRepository.update(updated)

        logger.info { "Password changed for user: ${user.email}" }
        return Result.success(Unit)
    }

    private fun validatePassword(password: String): String? {
        if (password.length < 8) {
            return "Password must be at least 8 characters"
        }
        if (!password.any { it.isDigit() }) {
            return "Password must contain at least one digit"
        }
        if (!password.any { it.isLetter() }) {
            return "Password must contain at least one letter"
        }
        return null
    }
}

class UserAlreadyExistsException(email: String) : Exception("User already exists: $email")
class UserNotFoundException(userId: String) : Exception("User not found: $userId")
class InvalidCredentialsException : Exception("Invalid email or password")
class InvalidPasswordException(message: String) : Exception(message)
class WrongAuthProviderException(provider: AuthProvider) : Exception("User registered with $provider")
