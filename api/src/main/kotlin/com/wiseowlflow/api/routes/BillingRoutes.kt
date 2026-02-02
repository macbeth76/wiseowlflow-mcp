package com.wiseowlflow.api.routes

import com.wiseowlflow.api.plugins.*
import com.wiseowlflow.billing.StripeService
import com.wiseowlflow.billing.WebhookResult
import com.wiseowlflow.domain.SubscriptionLimits
import com.wiseowlflow.domain.SubscriptionTier
import com.wiseowlflow.ports.SubscriptionRepository
import com.wiseowlflow.ports.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateCheckoutRequest(
    val tier: SubscriptionTier
)

@Serializable
data class SubscriptionResponse(
    val id: String,
    val tier: SubscriptionTier,
    val status: String,
    val currentPeriodStart: String?,
    val currentPeriodEnd: String?,
    val cancelAtPeriodEnd: Boolean,
    val limits: SubscriptionLimitsResponse
)

@Serializable
data class SubscriptionLimitsResponse(
    val maxWorkflows: Int,
    val maxExecutionsPerMonth: Int,
    val maxMcpServers: Int,
    val maxAiDecisionsPerMonth: Int
)

fun Route.billingRoutes(
    stripeService: StripeService?,
    subscriptionRepository: SubscriptionRepository,
    userRepository: UserRepository
) {
    route("/billing") {
        // Webhook endpoint (no auth)
        post("/webhook") {
            if (stripeService == null) {
                call.respond(HttpStatusCode.NotImplemented, mapOf("error" to "Billing not configured"))
                return@post
            }

            val payload = call.receiveText()
            val signature = call.request.header("Stripe-Signature")
                ?: throw IllegalArgumentException("Missing Stripe signature")

            when (stripeService.handleWebhook(payload, signature)) {
                WebhookResult.Success -> call.respond(HttpStatusCode.OK)
                WebhookResult.InvalidSignature -> call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid signature")
                )
                WebhookResult.Ignored -> call.respond(HttpStatusCode.OK)
            }
        }

        // Success/cancel pages
        get("/success") {
            call.respondText("Payment successful! You can close this window.", ContentType.Text.Html)
        }

        get("/cancel") {
            call.respondText("Payment canceled. You can close this window.", ContentType.Text.Html)
        }

        // Authenticated routes
        authenticate(AUTH_JWT) {
            // Get current subscription
            get("/subscription") {
                val auth = call.requireUser()
                val subscription = subscriptionRepository.findByUserId(auth.userId)

                if (subscription == null) {
                    // Return free tier info
                    val freeLimits = SubscriptionLimits.forTier(SubscriptionTier.FREE)
                    call.respond(SubscriptionResponse(
                        id = "",
                        tier = SubscriptionTier.FREE,
                        status = "ACTIVE",
                        currentPeriodStart = null,
                        currentPeriodEnd = null,
                        cancelAtPeriodEnd = false,
                        limits = freeLimits.toResponse()
                    ))
                    return@get
                }

                val limits = SubscriptionLimits.forTier(subscription.tier)
                call.respond(SubscriptionResponse(
                    id = subscription.id,
                    tier = subscription.tier,
                    status = subscription.status.name,
                    currentPeriodStart = subscription.currentPeriodStart?.toString(),
                    currentPeriodEnd = subscription.currentPeriodEnd?.toString(),
                    cancelAtPeriodEnd = subscription.cancelAtPeriodEnd,
                    limits = limits.toResponse()
                ))
            }

            // Create checkout session
            post("/checkout") {
                if (stripeService == null) {
                    call.respond(HttpStatusCode.NotImplemented, mapOf("error" to "Billing not configured"))
                    return@post
                }

                val auth = call.requireUser()
                val request = call.receive<CreateCheckoutRequest>()

                if (request.tier == SubscriptionTier.FREE) {
                    throw IllegalArgumentException("Cannot checkout free tier")
                }

                val user = userRepository.findById(auth.userId)
                    ?: throw NotFoundException("User not found")

                val checkoutUrl = stripeService.createCheckoutSession(
                    userId = auth.userId,
                    tier = request.tier,
                    customerEmail = user.email
                )

                call.respond(mapOf("url" to checkoutUrl))
            }

            // Get billing portal URL
            get("/portal") {
                if (stripeService == null) {
                    call.respond(HttpStatusCode.NotImplemented, mapOf("error" to "Billing not configured"))
                    return@get
                }

                val auth = call.requireUser()

                try {
                    val portalUrl = stripeService.createBillingPortalSession(auth.userId)
                    call.respond(mapOf("url" to portalUrl))
                } catch (e: IllegalStateException) {
                    throw NotFoundException("No active subscription")
                }
            }
        }
    }
}

private fun SubscriptionLimits.toResponse() = SubscriptionLimitsResponse(
    maxWorkflows = maxWorkflows,
    maxExecutionsPerMonth = maxExecutionsPerMonth,
    maxMcpServers = maxMcpServers,
    maxAiDecisionsPerMonth = maxAiDecisionsPerMonth
)
