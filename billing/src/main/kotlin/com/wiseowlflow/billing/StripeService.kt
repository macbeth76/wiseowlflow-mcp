package com.wiseowlflow.billing

import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.Invoice
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.checkout.SessionCreateParams
import com.wiseowlflow.domain.Subscription
import com.wiseowlflow.domain.SubscriptionStatus
import com.wiseowlflow.domain.SubscriptionTier
import com.wiseowlflow.ports.SubscriptionRepository
import com.wiseowlflow.ports.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class StripeConfig(
    val secretKey: String,
    val webhookSecret: String,
    val proPriceId: String,
    val enterprisePriceId: String,
    val successUrl: String = "http://localhost:8080/billing/success",
    val cancelUrl: String = "http://localhost:8080/billing/cancel"
)

class StripeService(
    private val config: StripeConfig,
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository
) {
    init {
        Stripe.apiKey = config.secretKey
    }

    suspend fun createCheckoutSession(
        userId: String,
        tier: SubscriptionTier,
        customerEmail: String
    ): String {
        val priceId = when (tier) {
            SubscriptionTier.PRO -> config.proPriceId
            SubscriptionTier.ENTERPRISE -> config.enterprisePriceId
            SubscriptionTier.FREE -> throw IllegalArgumentException("Cannot checkout free tier")
        }

        // Get or create Stripe customer
        val subscription = subscriptionRepository.findByUserId(userId)
        val customerId = subscription?.stripeCustomerId ?: createCustomer(userId, customerEmail)

        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .setSuccessUrl("${config.successUrl}?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(config.cancelUrl)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1)
                    .build()
            )
            .putMetadata("user_id", userId)
            .putMetadata("tier", tier.name)
            .build()

        val session = Session.create(params)
        logger.info { "Created checkout session ${session.id} for user $userId" }

        return session.url
    }

    suspend fun createBillingPortalSession(userId: String): String {
        val subscription = subscriptionRepository.findByUserId(userId)
            ?: throw IllegalStateException("No subscription found for user")

        val customerId = subscription.stripeCustomerId
            ?: throw IllegalStateException("No Stripe customer ID found")

        val params = com.stripe.param.billingportal.SessionCreateParams.builder()
            .setCustomer(customerId)
            .setReturnUrl(config.successUrl)
            .build()

        val session = com.stripe.model.billingportal.Session.create(params)
        return session.url
    }

    suspend fun handleWebhook(payload: String, signature: String): WebhookResult {
        val event = try {
            Webhook.constructEvent(payload, signature, config.webhookSecret)
        } catch (e: Exception) {
            logger.error(e) { "Webhook signature verification failed" }
            return WebhookResult.InvalidSignature
        }

        logger.info { "Processing Stripe webhook: ${event.type}" }

        return when (event.type) {
            "checkout.session.completed" -> {
                val session = event.dataObjectDeserializer.`object`.orElse(null) as? Session
                if (session != null) {
                    handleCheckoutCompleted(session)
                }
                WebhookResult.Success
            }
            "customer.subscription.updated" -> {
                val stripeSub = event.dataObjectDeserializer.`object`.orElse(null) as? com.stripe.model.Subscription
                if (stripeSub != null) {
                    handleSubscriptionUpdated(stripeSub)
                }
                WebhookResult.Success
            }
            "customer.subscription.deleted" -> {
                val stripeSub = event.dataObjectDeserializer.`object`.orElse(null) as? com.stripe.model.Subscription
                if (stripeSub != null) {
                    handleSubscriptionDeleted(stripeSub)
                }
                WebhookResult.Success
            }
            "invoice.payment_failed" -> {
                val invoice = event.dataObjectDeserializer.`object`.orElse(null) as? Invoice
                if (invoice != null) {
                    handlePaymentFailed(invoice)
                }
                WebhookResult.Success
            }
            else -> {
                logger.debug { "Unhandled webhook event type: ${event.type}" }
                WebhookResult.Ignored
            }
        }
    }

    private suspend fun handleCheckoutCompleted(session: Session) {
        val userId = session.metadata["user_id"] ?: return
        val tierStr = session.metadata["tier"] ?: return
        val tier = SubscriptionTier.valueOf(tierStr)

        val stripeSubscriptionId = session.subscription
        val stripeSubscription = com.stripe.model.Subscription.retrieve(stripeSubscriptionId)

        val now = Clock.System.now()
        val existingSubscription = subscriptionRepository.findByUserId(userId)

        val subscription = if (existingSubscription != null) {
            existingSubscription.copy(
                tier = tier,
                status = mapStripeStatus(stripeSubscription.status),
                stripeCustomerId = session.customer,
                stripeSubscriptionId = stripeSubscriptionId,
                currentPeriodStart = Instant.fromEpochSeconds(stripeSubscription.currentPeriodStart),
                currentPeriodEnd = Instant.fromEpochSeconds(stripeSubscription.currentPeriodEnd),
                updatedAt = now
            )
        } else {
            Subscription(
                id = UUID.randomUUID().toString(),
                userId = userId,
                tier = tier,
                status = mapStripeStatus(stripeSubscription.status),
                stripeCustomerId = session.customer,
                stripeSubscriptionId = stripeSubscriptionId,
                currentPeriodStart = Instant.fromEpochSeconds(stripeSubscription.currentPeriodStart),
                currentPeriodEnd = Instant.fromEpochSeconds(stripeSubscription.currentPeriodEnd),
                createdAt = now,
                updatedAt = now
            )
        }

        if (existingSubscription != null) {
            subscriptionRepository.update(subscription)
        } else {
            subscriptionRepository.create(subscription)
        }

        logger.info { "Updated subscription for user $userId to $tier" }
    }

    private suspend fun handleSubscriptionUpdated(stripeSubscription: com.stripe.model.Subscription) {
        val subscription = subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.id)
            ?: return

        val updated = subscription.copy(
            status = mapStripeStatus(stripeSubscription.status),
            currentPeriodStart = Instant.fromEpochSeconds(stripeSubscription.currentPeriodStart),
            currentPeriodEnd = Instant.fromEpochSeconds(stripeSubscription.currentPeriodEnd),
            cancelAtPeriodEnd = stripeSubscription.cancelAtPeriodEnd,
            updatedAt = Clock.System.now()
        )

        subscriptionRepository.update(updated)
        logger.info { "Updated subscription ${subscription.id}" }
    }

    private suspend fun handleSubscriptionDeleted(stripeSubscription: com.stripe.model.Subscription) {
        val subscription = subscriptionRepository.findByStripeSubscriptionId(stripeSubscription.id)
            ?: return

        val updated = subscription.copy(
            tier = SubscriptionTier.FREE,
            status = SubscriptionStatus.CANCELED,
            updatedAt = Clock.System.now()
        )

        subscriptionRepository.update(updated)
        logger.info { "Canceled subscription ${subscription.id}" }
    }

    private suspend fun handlePaymentFailed(invoice: Invoice) {
        val customerId = invoice.customer
        val subscription = subscriptionRepository.findByStripeCustomerId(customerId)
            ?: return

        val updated = subscription.copy(
            status = SubscriptionStatus.PAST_DUE,
            updatedAt = Clock.System.now()
        )

        subscriptionRepository.update(updated)
        logger.warn { "Payment failed for subscription ${subscription.id}" }
    }

    private fun createCustomer(userId: String, email: String): String {
        val params = CustomerCreateParams.builder()
            .setEmail(email)
            .putMetadata("user_id", userId)
            .build()

        val customer = Customer.create(params)
        return customer.id
    }

    private fun mapStripeStatus(status: String): SubscriptionStatus = when (status) {
        "active" -> SubscriptionStatus.ACTIVE
        "past_due" -> SubscriptionStatus.PAST_DUE
        "canceled" -> SubscriptionStatus.CANCELED
        "incomplete" -> SubscriptionStatus.INCOMPLETE
        "trialing" -> SubscriptionStatus.TRIALING
        else -> SubscriptionStatus.ACTIVE
    }

    companion object {
        fun fromEnvironment(
            subscriptionRepository: SubscriptionRepository,
            userRepository: UserRepository
        ): StripeService? {
            val secretKey = System.getenv("STRIPE_SECRET_KEY") ?: return null
            val webhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET") ?: return null

            val config = StripeConfig(
                secretKey = secretKey,
                webhookSecret = webhookSecret,
                proPriceId = System.getenv("STRIPE_PRICE_PRO") ?: "price_pro",
                enterprisePriceId = System.getenv("STRIPE_PRICE_ENTERPRISE") ?: "price_enterprise"
            )

            return StripeService(config, subscriptionRepository, userRepository)
        }
    }
}

sealed class WebhookResult {
    data object Success : WebhookResult()
    data object InvalidSignature : WebhookResult()
    data object Ignored : WebhookResult()
}
