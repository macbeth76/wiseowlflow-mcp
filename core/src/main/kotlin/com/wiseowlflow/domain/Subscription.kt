package com.wiseowlflow.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String,
    val userId: String,
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    val stripeCustomerId: String? = null,
    val stripeSubscriptionId: String? = null,
    val currentPeriodStart: Instant? = null,
    val currentPeriodEnd: Instant? = null,
    val cancelAtPeriodEnd: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
enum class SubscriptionTier {
    FREE,
    PRO,
    ENTERPRISE
}

@Serializable
enum class SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    CANCELED,
    INCOMPLETE,
    TRIALING
}

@Serializable
data class SubscriptionLimits(
    val maxWorkflows: Int,
    val maxExecutionsPerMonth: Int,
    val maxMcpServers: Int,
    val maxAiDecisionsPerMonth: Int
) {
    companion object {
        fun forTier(tier: SubscriptionTier): SubscriptionLimits = when (tier) {
            SubscriptionTier.FREE -> SubscriptionLimits(
                maxWorkflows = 3,
                maxExecutionsPerMonth = 100,
                maxMcpServers = 3,
                maxAiDecisionsPerMonth = 50
            )
            SubscriptionTier.PRO -> SubscriptionLimits(
                maxWorkflows = 25,
                maxExecutionsPerMonth = 5000,
                maxMcpServers = 10,
                maxAiDecisionsPerMonth = 1000
            )
            SubscriptionTier.ENTERPRISE -> SubscriptionLimits(
                maxWorkflows = Int.MAX_VALUE,
                maxExecutionsPerMonth = Int.MAX_VALUE,
                maxMcpServers = Int.MAX_VALUE,
                maxAiDecisionsPerMonth = 10000
            )
        }
    }
}

@Serializable
data class UsageStats(
    val userId: String,
    val periodStart: Instant,
    val periodEnd: Instant,
    val workflowCount: Int,
    val executionCount: Int,
    val mcpServerCount: Int,
    val aiDecisionCount: Int
)
