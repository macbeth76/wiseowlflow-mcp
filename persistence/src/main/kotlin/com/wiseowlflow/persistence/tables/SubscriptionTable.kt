package com.wiseowlflow.persistence.tables

import com.wiseowlflow.domain.SubscriptionStatus
import com.wiseowlflow.domain.SubscriptionTier
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object SubscriptionTable : Table("subscriptions") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UserTable.id).uniqueIndex()
    val tier = enumerationByName<SubscriptionTier>("tier", 20)
    val status = enumerationByName<SubscriptionStatus>("status", 20)
    val stripeCustomerId = varchar("stripe_customer_id", 255).nullable().index()
    val stripeSubscriptionId = varchar("stripe_subscription_id", 255).nullable().index()
    val currentPeriodStart = timestamp("current_period_start").nullable()
    val currentPeriodEnd = timestamp("current_period_end").nullable()
    val cancelAtPeriodEnd = bool("cancel_at_period_end").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object UsageCounterTable : Table("usage_counters") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UserTable.id)
    val periodStart = timestamp("period_start")
    val periodEnd = timestamp("period_end")
    val executionCount = integer("execution_count").default(0)
    val aiDecisionCount = integer("ai_decision_count").default(0)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_usage_user_period", userId, periodStart)
    }
}
