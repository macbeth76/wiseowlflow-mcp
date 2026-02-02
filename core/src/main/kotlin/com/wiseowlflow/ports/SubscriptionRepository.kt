package com.wiseowlflow.ports

import com.wiseowlflow.domain.Subscription
import com.wiseowlflow.domain.UsageStats
import kotlinx.datetime.Instant

interface SubscriptionRepository {
    suspend fun findById(id: String): Subscription?
    suspend fun findByUserId(userId: String): Subscription?
    suspend fun findByStripeCustomerId(customerId: String): Subscription?
    suspend fun findByStripeSubscriptionId(subscriptionId: String): Subscription?
    suspend fun create(subscription: Subscription): Subscription
    suspend fun update(subscription: Subscription): Subscription
}

interface UsageRepository {
    suspend fun getUsageStats(userId: String, periodStart: Instant, periodEnd: Instant): UsageStats
    suspend fun incrementExecutionCount(userId: String): Int
    suspend fun incrementAiDecisionCount(userId: String): Int
    suspend fun getWorkflowCount(userId: String): Int
    suspend fun getMcpServerCount(userId: String): Int
}
