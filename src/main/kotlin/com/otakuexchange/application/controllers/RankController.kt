package com.otakuexchange.application.controllers

import com.otakuexchange.domain.repositories.IRankRepository
import io.ktor.server.response.*
import io.ktor.server.routing.*

class RankController(
    private val rankRepository: IRankRepository
) : IRouteController {

    override fun registerRoutes(route: Route) {
        // GET /rank/wallet?limit=100
        route.get("/rank/wallet") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
            val leaderboard = rankRepository.getWalletLeaderboard(limit)
            call.respond(leaderboard)
        }
    }
}