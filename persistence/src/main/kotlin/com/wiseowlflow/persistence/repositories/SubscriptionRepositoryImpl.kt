package com.wiseowlflow.persistence.repositories

import com.wiseowlflow.domain.Subscription
import com.wiseowlflow.domain.SubscriptionStatus
import com.wiseowlflow.domain.SubscriptionTier
import com.wiseowlflow.domain.UsageStats
import com.wiseowlflow.persistence.dbQuery
import com.wiseowlflow.persistence.tables.*
import com.wiseowlflow.ports.SubscriptionRepository
import com.wiseowlflow.ports.UsageRepository
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class SubscriptionRepositoryImpl : SubscriptionRepository {

    override suspend fun findById(id: String): Subscription? = dbQuery {
        SubscriptionTable.selectAll()
            .where { SubscriptionTable.id eq id }
            .map { it.toSubscription() }
            .singleOrNull()
    }

    override suspend fun findByUserId(userId: String): Subscription? = dbQuery {
        SubscriptionTable.selectAll()
            .where { SubscriptionTable.userId eq userId }
            .map { it.toSubscription() }
            .singleOrNull()
    }

    override suspend fun findByStripeCustomerId(customerId: String): Subscription? = dbQuery {
        SubscriptionTable.selectAll()
            .where { SubscriptionTable.stripeCustomerId eq customerId }
            .map { it.toSubscription() }
            .singleOrNull()
    }

    override suspend fun findByStripeSubscriptionId(subscriptionId: String): Subscription? = dbQuery {
        SubscriptionTable.selectAll()
            .where { SubscriptionTable.stripeSubscriptionId eq subscriptionId }
            .map { it.toSubscription() }
            .singleOrNull()
    }

    override suspend fun create(subscription: Subscription): Subscription = dbQuery {
        SubscriptionTable.insert {
            it[id] = subscription.id
            it[userId] = subscription.userId
            it[tier] = subscription.tier
            it[status] = subscription.status
            it[stripeCustomerId] = subscription.stripeCustomerId
            it[stripeSubscriptionId] = subscription.stripeSubscriptionId
            it[currentPeriodStart] = subscription.currentPeriodStart
            it[currentPeriodEnd] = subscription.currentPeriodEnd
            it[cancelAtPeriodEnd] = subscription.cancelAtPeriodEnd
            it[createdAt] = subscription.createdAt
            it[updatedAt] = subscription.updatedAt
        }
        subscription
    }

    override suspend fun update(subscription: Subscription): Subscription = dbQuery {
        SubscriptionTable.update({ SubscriptionTable.id eq subscription.id }) {
            it[tier] = subscription.tier
            it[status] = subscription.status
            it[stripeCustomerId] = subscription.stripeCustomerId
            it[stripeSubscriptionId] = subscription.stripeSubscriptionId
            it[currentPeriodStart] = subscription.currentPeriodStart
            it[currentPeriodEnd] = subscription.currentPeriodEnd
            it[cancelAtPeriodEnd] = subscription.cancelAtPeriodEnd
            it[updatedAt] = subscription.updatedAt
        }
        subscription
    }

    private fun ResultRow.toSubscription() = Subscription(
        id = this[SubscriptionTable.id],
        userId = this[SubscriptionTable.userId],
        tier = this[SubscriptionTable.tier],
        status = this[SubscriptionTable.status],
        stripeCustomerId = this[SubscriptionTable.stripeCustomerId],
        stripeSubscriptionId = this[SubscriptionTable.stripeSubscriptionId],
        currentPeriodStart = this[SubscriptionTable.currentPeriodStart],
        currentPeriodEnd = this[SubscriptionTable.currentPeriodEnd],
        cancelAtPeriodEnd = this[SubscriptionTable.cancelAtPeriodEnd],
        createdAt = this[SubscriptionTable.createdAt],
        updatedAt = this[SubscriptionTable.updatedAt]
    )
}

class UsageRepositoryImpl : UsageRepository {

    override suspend fun getUsageStats(userId: String, periodStart: Instant, periodEnd: Instant): UsageStats = dbQuery {
        val counter = UsageCounterTable.selectAll()
            .where {
                (UsageCounterTable.userId eq userId) and
                    (UsageCounterTable.periodStart eq periodStart)
            }
            .singleOrNull()

        val workflowCount = WorkflowTable.selectAll()
            .where { WorkflowTable.userId eq userId }
            .count().toInt()

        val mcpServerCount = McpServerTable.selectAll()
            .where { McpServerTable.userId eq userId }
            .count().toInt()

        UsageStats(
            userId = userId,
            periodStart = periodStart,
            periodEnd = periodEnd,
            workflowCount = workflowCount,
            executionCount = counter?.get(UsageCounterTable.executionCount) ?: 0,
            mcpServerCount = mcpServerCount,
            aiDecisionCount = counter?.get(UsageCounterTable.aiDecisionCount) ?: 0
        )
    }

    override suspend fun incrementExecutionCount(userId: String): Int = dbQuery {
        val period = getCurrentPeriod()
        ensureCounterExists(userId, period.first, period.second)

        UsageCounterTable.update({
            (UsageCounterTable.userId eq userId) and
                (UsageCounterTable.periodStart eq period.first)
        }) {
            with(SqlExpressionBuilder) {
                it[executionCount] = executionCount + 1
            }
        }

        UsageCounterTable.selectAll()
            .where {
                (UsageCounterTable.userId eq userId) and
                    (UsageCounterTable.periodStart eq period.first)
            }
            .single()[UsageCounterTable.executionCount]
    }

    override suspend fun incrementAiDecisionCount(userId: String): Int = dbQuery {
        val period = getCurrentPeriod()
        ensureCounterExists(userId, period.first, period.second)

        UsageCounterTable.update({
            (UsageCounterTable.userId eq userId) and
                (UsageCounterTable.periodStart eq period.first)
        }) {
            with(SqlExpressionBuilder) {
                it[aiDecisionCount] = aiDecisionCount + 1
            }
        }

        UsageCounterTable.selectAll()
            .where {
                (UsageCounterTable.userId eq userId) and
                    (UsageCounterTable.periodStart eq period.first)
            }
            .single()[UsageCounterTable.aiDecisionCount]
    }

    override suspend fun getWorkflowCount(userId: String): Int = dbQuery {
        WorkflowTable.selectAll()
            .where { WorkflowTable.userId eq userId }
            .count().toInt()
    }

    override suspend fun getMcpServerCount(userId: String): Int = dbQuery {
        McpServerTable.selectAll()
            .where { McpServerTable.userId eq userId }
            .count().toInt()
    }

    private fun ensureCounterExists(userId: String, periodStart: Instant, periodEnd: Instant) {
        val exists = UsageCounterTable.selectAll()
            .where {
                (UsageCounterTable.userId eq userId) and
                    (UsageCounterTable.periodStart eq periodStart)
            }
            .count() > 0

        if (!exists) {
            UsageCounterTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[UsageCounterTable.userId] = userId
                it[UsageCounterTable.periodStart] = periodStart
                it[UsageCounterTable.periodEnd] = periodEnd
                it[executionCount] = 0
                it[aiDecisionCount] = 0
                it[createdAt] = Clock.System.now()
            }
        }
    }

    private fun getCurrentPeriod(): Pair<Instant, Instant> {
        val now = Clock.System.now()
        val tz = TimeZone.UTC
        val local = now.toLocalDateTime(tz)

        val startOfMonth = LocalDateTime(local.year, local.month, 1, 0, 0, 0, 0)
            .toInstant(tz)

        val nextMonth = if (local.monthNumber == 12) {
            LocalDateTime(local.year + 1, 1, 1, 0, 0, 0, 0)
        } else {
            LocalDateTime(local.year, local.monthNumber + 1, 1, 0, 0, 0, 0)
        }
        val endOfMonth = nextMonth.toInstant(tz)

        return startOfMonth to endOfMonth
    }
}
