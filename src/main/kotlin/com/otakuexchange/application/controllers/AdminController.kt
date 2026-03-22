package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.MarketStatus
import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.services.MarketSeederService
import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.repositories.IPositionRepository
import com.otakuexchange.domain.repositories.IUserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SeedRequest(
    /**
     * Center YES price in cents (1-99). Defaults to 50.
     * Only used when useLastPrice = false.
     */
    val midpoint: Int = 50,

    /**
     * Number of price levels per side. Defaults to 10.
     * 5-7 = light top-up, 10 = standard seed, 12-15 = heavy seed
     */
    val levels: Int = 10,

    /**
     * Contracts per level from closest to furthest from midpoint.
     * Defaults to normal distribution [50,45,38,28,20,13,8,5,2,1]
     */
    val quantities: List<Int>? = null,

    /**
     * If true, centers around last traded YES price instead of midpoint.
     * Use for reseeds on active markets to avoid distorting price.
     */
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

    override fun registerRoutes(route: Route) { }

    override fun registerProtectedRoutes(route: Route) {

        // ── Seed a market ─────────────────────────────────────────────────────
        route.post("/admin/markets/{id}/seed") {
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

            // Calculate escrow from positions — sum of all locked amounts
            val escrowBalance = positions.sumOf { it.lockedAmount }
            val winners = positions.filter { it.side == winningSide }
            val totalWinningContracts = winners.sumOf { it.quantity }

            if (totalWinningContracts > 0) {
                // Each winning contract gets paid proportionally from escrow
                // escrow = 100¢ per contract pair, so each YES or NO contract
                // that wins gets 100¢ (their stake + loser's stake)
                for (position in winners) {
                    val payout = (escrowBalance * position.quantity) / totalWinningContracts
                    userRepository.addBalance(position.userId, payout)
                }
            }

            // Delete all positions for this market
            positions.forEach { position ->
                positionRepository.deletePosition(position.userId, marketId, position.side)
            }

            // Update market status
            val newStatus = if (resolution == "YES") MarketStatus.RESOLVED_YES else MarketStatus.RESOLVED_NO
            marketRepository.updateStatus(marketId, newStatus)

            call.respond(
                HttpStatusCode.OK,
                "Market $marketId resolved $resolution — paid out ${winners.size} winners from ${escrowBalance}¢ escrow"
            )
        }
    }
}