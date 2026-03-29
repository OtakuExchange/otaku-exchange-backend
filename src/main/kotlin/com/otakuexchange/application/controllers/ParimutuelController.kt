package com.otakuexchange.application.controllers

import com.otakuexchange.domain.parimutuel.MarketPool
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.repositories.parimutuel.IMarketPoolRepository
import com.otakuexchange.domain.repositories.parimutuel.IStakeRepository
import com.otakuexchange.domain.services.ParimutuelService
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class PlaceStakeRequest(val marketPoolId: Uuid, val amount: Int)

@Serializable
data class ResolveEventRequest(val winningPoolId: Uuid)

@Serializable
data class CreateMarketPoolRequest(
    val label: String,
    val entityId: Uuid? = null
)

class ParimutuelController(
    private val parimutuelService: ParimutuelService,
    private val marketPoolRepository: IMarketPoolRepository,
    private val stakeRepository: IStakeRepository,
    private val userRepository: IUserRepository
) : IRouteController {

    private val devUserId: String? = runCatching {
        System.getenv("DEV_USER_ID") ?: dotenv()["DEV_USER_ID"]
    }.getOrNull()

    private suspend fun resolveUser(call: io.ktor.server.application.ApplicationCall): User? {
        if (devUserId != null) return userRepository.findById(Uuid.parse(devUserId))
        val clerkId = call.principal<JWTPrincipal>()?.payload?.subject ?: return null
        return userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
    }

    override fun registerRoutes(route: Route) {

        // List all pools for an event (amounts update as users stake)
        route.get("/events/{eventId}/pools") {
            val eventId = parseUuid(call, "eventId") ?: return@get
            val pools = marketPoolRepository.getByEventIdWithEntity(eventId)
            call.respond(pools)
        }

        /**
         * GET /events/{eventId}/pools/{poolId}/preview?amount=100
         *
         * Hypothetical payout if caller staked [amount] cents into [poolId] right now
         * and that pool won. Defaults to 100¢ ($1.00) so the frontend can show
         * "stake $1 → win $X" for every pool without requiring a logged-in user.
         *
         * Hit this endpoint whenever the user changes the amount input.
         */
        route.get("/events/{eventId}/pools/{poolId}/preview") {
            val eventId = parseUuid(call, "eventId") ?: return@get
            val poolId  = parseUuid(call, "poolId")  ?: return@get
            val amount  = call.request.queryParameters["amount"]?.toIntOrNull() ?: 100

            if (amount <= 0) {
                call.respond(HttpStatusCode.BadRequest, "amount must be > 0")
                return@get
            }

            val payout = parimutuelService.getPayoutPreview(eventId, poolId, amount)
            call.respond(mapOf("hypotheticalStake" to amount, "projectedPayout" to payout))
        }
    }

    override fun registerProtectedRoutes(route: Route) {

        // GET /stakes/me — all stakes for the logged-in user
        route.get("/stakes/me") {
            val user = resolveUser(call)
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")
            val stakes = stakeRepository.getByUserId(user.id)
            call.respond(stakes)
        }

        /**
         * GET /stakes/me/pools/{poolId}/payout
         *
         * What the calling user would actually receive if [poolId] wins right now,
         * based on their real recorded stake.
         */
        route.get("/stakes/me/pools/{poolId}/payout") {
            val user = resolveUser(call)
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")
            val poolId = parseUuid(call, "poolId") ?: return@get
            val payout = parimutuelService.getCurrentPayout(user.id, poolId)
            call.respond(mapOf("payout" to payout))
        }

        // POST /stakes — place or add to a stake
        route.post("/stakes") {
            val user = resolveUser(call)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")

            val body = call.receive<PlaceStakeRequest>()
            if (body.amount <= 0) {
                call.respond(HttpStatusCode.BadRequest, "amount must be > 0")
                return@post
            }

            val stake = runCatching {
                parimutuelService.placeStake(user.id, body.marketPoolId, body.amount)
            }.getOrElse { e ->
                val msg    = e.message ?: "Failed to place stake"
                val status = if (msg == "Insufficient balance") HttpStatusCode.PaymentRequired
                             else HttpStatusCode.BadRequest
                return@post call.respond(status, msg)
            }

            call.respond(HttpStatusCode.Created, stake)
        }

        // POST /events/{eventId}/resolve — admin only
        route.post("/events/{eventId}/resolve") {
            val user = resolveUser(call)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")
            if (!user.isAdmin) {
                call.respond(HttpStatusCode.Forbidden, "Admin only")
                return@post
            }

            val eventId = parseUuid(call, "eventId") ?: return@post
            val body    = call.receive<ResolveEventRequest>()

            runCatching {
                parimutuelService.resolveEvent(eventId, body.winningPoolId)
            }.getOrElse { e ->
                return@post call.respond(HttpStatusCode.BadRequest, e.message ?: "Resolution failed")
            }

            call.respond(HttpStatusCode.OK, mapOf("resolved" to true))
        }

        // POST /events/{eventId}/pools — admin only
        route.post("/events/{eventId}/pools") {
            val user = resolveUser(call)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")
            if (!user.isAdmin) {
                call.respond(HttpStatusCode.Forbidden, "Admin only")
                return@post
            }
            val eventId = parseUuid(call, "eventId") ?: return@post
            val body = call.receive<CreateMarketPoolRequest>()
            val pool = marketPoolRepository.create(
                MarketPool(
                    eventId = eventId,
                    entityId = body.entityId,
                    label = body.label
                )
            )
            call.respond(HttpStatusCode.Created, pool)
        }
    }

    private suspend fun parseUuid(
        call: io.ktor.server.application.ApplicationCall,
        param: String
    ): Uuid? = try {
        Uuid.parse(call.parameters[param] ?: "")
    } catch (e: Exception) {
        call.respond(HttpStatusCode.BadRequest, "Invalid $param")
        null
    }
}