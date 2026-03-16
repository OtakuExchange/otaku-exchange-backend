package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.Market
import com.otakuexchange.domain.repositories.IMarketRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid

class MarketController(private val marketRepository: IMarketRepository) : IRouteController {

    override fun registerRoutes(route: Route) {

        route.get("/events/{eventId}/markets") {
            val eventId = try {
                Uuid.parse(call.parameters["eventId"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid eventId")
                return@get
            }
            val markets = marketRepository.getMarketsByEventId(eventId)
            call.respond(markets)
        }

        route.get("/markets/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@get
            }
            val market = marketRepository.getById(id)
            if (market == null) call.respond(HttpStatusCode.NotFound, "Market not found")
            else call.respond(market)
        }

    }

    override fun registerProtectedRoutes(route: Route) {

        route.post("/markets") {
            val market = call.receive<Market>()
            val saved = marketRepository.save(market)
            call.respond(HttpStatusCode.Created, saved)
        }

        route.put("/markets/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@put
            }
            val market = call.receive<Market>()
            if (market.id != id) {
                call.respond(HttpStatusCode.BadRequest, "ID mismatch")
                return@put
            }
            val updated = marketRepository.update(market)
            call.respond(updated)
        }

        route.delete("/markets/{id}") {
            val id = try {
                Uuid.parse(call.parameters["id"] ?: "")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@delete
            }
            val deleted = marketRepository.delete(id)
            if (deleted) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound, "Market not found")
        }
    }
}