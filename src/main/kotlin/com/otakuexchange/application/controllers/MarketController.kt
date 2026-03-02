package com.otakuexchange.application.controllers

import com.otakuexchange.domain.repositories.IMarketRepository
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.http.HttpStatusCode

class MarketController(private val marketRepository: IMarketRepository) : IRouteController {
    override fun registerRoutes(route: Route) {
        route.get("/markets/{eventId}") {
            val eventId = call.parameters["eventId"]?.toIntOrNull()
            if (eventId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid eventId")
                return@get
            }
            val markets = marketRepository.getMarketsByEventId(eventId)
            call.respond(markets)
        }
    }
}