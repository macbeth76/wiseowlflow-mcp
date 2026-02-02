package com.wiseowlflow.billing

import com.wiseowlflow.domain.SubscriptionLimits
import com.wiseowlflow.domain.SubscriptionTier
import com.wiseowlflow.ports.SubscriptionRepository
import com.wiseowlflow.ports.UsageRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val logger = KotlinLogging.logger {}

sealed class QuotaCheckResult {
    data object Allowed : QuotaCheckResult()
    data class Exceeded(val resource: String, val limit: Int, val current: Int) : QuotaCheckResult()
    data class Error(val message: String) : QuotaCheckResult()
}

class UsageEnforcer(
    private val subscriptionRepository: SubscriptionRepository,
    private val usageRepository: UsageRepository
) {
    suspend fun checkWorkflowCreation(userId: String): QuotaCheckResult {
        val subscription = subscriptionRepository.findByUserId(userId)
        val tier = subscription?.tier ?: SubscriptionTier.FREE
        val limits = SubscriptionLimits.forTier(tier)

        val currentCount = usageRepository.getWorkflowCount(userId)

        return if (currentCount >= limits.maxWorkflows) {
            QuotaCheckResult.Exceeded("workflows", limits.maxWorkflows, currentCount)
        } else {
            QuotaCheckResult.Allowed
        }
    }

    suspend fun checkMcpServerCreation(userId: String): QuotaCheckResult {
        val subscription = subscriptionRepository.findByUserId(userId)
        val tier = subscription?.tier ?: SubscriptionTier.FREE
        val limits = SubscriptionLimits.forTier(tier)

        val currentCount = usageRepository.getMcpServerCount(userId)

        return if (currentCount >= limits.maxMcpServers) {
            QuotaCheckResult.Exceeded("mcp_servers", limits.maxMcpServers, currentCount)
        } else {
            QuotaCheckResult.Allowed
        }
    }

    suspend fun checkWorkflowExecution(userId: String): QuotaCheckResult {
        val subscription = subscriptionRepository.findByUserId(userId)
        val tier = subscription?.tier ?: SubscriptionTier.FREE
        val limits = SubscriptionLimits.forTier(tier)

        val (periodStart, periodEnd) = getCurrentPeriod()
        val stats = usageRepository.getUsageStats(userId, periodStart, periodEnd)

        return if (stats.executionCount >= limits.maxExecutionsPerMonth) {
            QuotaCheckResult.Exceeded("executions", limits.maxExecutionsPerMonth, stats.executionCount)
        } else {
            QuotaCheckResult.Allowed
        }
    }

    suspend fun checkAiDecision(userId: String): QuotaCheckResult {
        val subscription = subscriptionRepository.findByUserId(userId)
        val tier = subscription?.tier ?: SubscriptionTier.FREE
        val limits = SubscriptionLimits.forTier(tier)

        val (periodStart, periodEnd) = getCurrentPeriod()
        val stats = usageRepository.getUsageStats(userId, periodStart, periodEnd)

        return if (stats.aiDecisionCount >= limits.maxAiDecisionsPerMonth) {
            QuotaCheckResult.Exceeded("ai_decisions", limits.maxAiDecisionsPerMonth, stats.aiDecisionCount)
        } else {
            QuotaCheckResult.Allowed
        }
    }

    suspend fun recordExecution(userId: String): Int {
        return usageRepository.incrementExecutionCount(userId)
    }

    suspend fun recordAiDecision(userId: String): Int {
        return usageRepository.incrementAiDecisionCount(userId)
    }

    suspend fun getUserLimits(userId: String): SubscriptionLimits {
        val subscription = subscriptionRepository.findByUserId(userId)
        val tier = subscription?.tier ?: SubscriptionTier.FREE
        return SubscriptionLimits.forTier(tier)
    }

    private fun getCurrentPeriod(): Pair<kotlinx.datetime.Instant, kotlinx.datetime.Instant> {
        val now = Clock.System.now()
        val tz = TimeZone.UTC
        val local = now.toLocalDateTime(tz)

        val startOfMonth = kotlinx.datetime.LocalDateTime(local.year, local.month, 1, 0, 0, 0)
            .toInstant(tz)

        val nextMonth = if (local.month == kotlinx.datetime.Month.DECEMBER) {
            kotlinx.datetime.LocalDateTime(local.year + 1, kotlinx.datetime.Month.JANUARY, 1, 0, 0, 0)
        } else {
            kotlinx.datetime.LocalDateTime(local.year, local.month.plus(1), 1, 0, 0, 0)
        }
        val endOfMonth = nextMonth.toInstant(tz)

        return startOfMonth to endOfMonth
    }
}

private fun kotlinx.datetime.LocalDateTime.toInstant(tz: TimeZone): kotlinx.datetime.Instant {
    return this.toInstant(tz)
}
