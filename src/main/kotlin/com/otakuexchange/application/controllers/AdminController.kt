package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.MarketStatus
import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.services.MarketSeederService
import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.repositories.IPositionRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SeedRequest(
    val midpoint: Int = 50,
    val levels: Int = 10,
    val quantities: List<Int>? = null,
    val useLastPrice: Boolean = false
)

@Serializable
data class ResolveRequest(
    val resolution: String  // "YES" or "NO"
)

class AdminController(
    private val marketSeederService: MarketSeederService,
    private val marketRepository: IMarketRepository,
    private val positionRepository: IPositionRepository,
    private val userRepository: IUserRepository
) : IRouteController {

    private val devUserId: String? = runCatching {
        System.getenv("DEV_USER_ID") ?: dotenv()["DEV_USER_ID"]
    }.getOrNull()

    private suspend fun resolveAdmin(call: io.ktor.server.application.ApplicationCall): User? {
        val user = if (devUserId != null) {
            userRepository.findById(Uuid.parse(devUserId))
        } else {
            val clerkId = call.principal<JWTPrincipal>()?.payload?.subject ?: return null
            userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
        }
        return if (user?.isAdmin == true) user else null
    }

    override fun registerRoutes(route: Route) { }

    override fun registerProtectedRoutes(route: Route) {

        // ── Seed a market ─────────────────────────────────────────────────────
        route.post("/admin/markets/{id}/seed") {
            resolveAdmin(call) ?: return@post call.respond(HttpStatusCode.Forbidden, "Admin access required")

            val marketId = try { Uuid.parse(call.parameters["id"] ?: "") }
            catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, "Invalid market id"); return@post }

            val req = call.receive<SeedRequest>()

            if (req.midpoint !in 1..99) {
                call.respond(HttpStatusCode.BadRequest, "midpoint must be between 1 and 99")
                return@post
            }
            if (req.levels !in 1..20) {
                call.respond(HttpStatusCode.BadRequest, "levels must be between 1 and 20")
                return@post
            }

            val quantities = req.quantities?.toIntArray() ?: MarketSeederService.DEFAULT_QUANTITIES
            marketSeederService.seed(
                marketId = marketId,
                midpoint = req.midpoint,
                levels = req.levels,
                quantities = quantities,
                useLastPrice = req.useLastPrice
            )
            call.respond(HttpStatusCode.OK, "Market $marketId seeded successfully")
        }

        // ── Resolve a market ──────────────────────────────────────────────────
        route.post("/admin/markets/{id}/resolve") {
            resolveAdmin(call) ?: return@post call.respond(HttpStatusCode.Forbidden, "Admin access required")

            val marketId = try { Uuid.parse(call.parameters["id"] ?: "") }
            catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, "Invalid market id"); return@post }

            val req = call.receive<ResolveRequest>()
            val resolution = req.resolution.uppercase()

            if (resolution != "YES" && resolution != "NO") {
                call.respond(HttpStatusCode.BadRequest, "resolution must be YES or NO")
                return@post
            }

            val market = marketRepository.getById(marketId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Market not found")

            if (market.status != "OPEN" && market.status != "CLOSED") {
                call.respond(HttpStatusCode.Conflict, "Market is already resolved")
                return@post
            }

            val winningSide = if (resolution == "YES") OrderSide.YES else OrderSide.NO
            val positions = positionRepository.getPositionsByMarket(marketId)
            val escrowBalance = positions.sumOf { it.lockedAmount }
            val winners = positions.filter { it.side == winningSide }
            val totalWinningContracts = winners.sumOf { it.quantity }

            if (totalWinningContracts > 0) {
                for (position in winners) {
                    val payout = (escrowBalance * position.quantity) / totalWinningContracts
                    userRepository.addBalance(position.userId, payout)
                }
            }

            positions.forEach { position ->
                positionRepository.deletePosition(position.userId, marketId, position.side)
            }

            val newStatus = if (resolution == "YES") MarketStatus.RESOLVED_YES else MarketStatus.RESOLVED_NO
            marketRepository.updateStatus(marketId, newStatus)

            call.respond(
                HttpStatusCode.OK,
                "Market $marketId resolved $resolution — paid out ${winners.size} winners from ${escrowBalance}¢ escrow"
            )
        }
    }
}