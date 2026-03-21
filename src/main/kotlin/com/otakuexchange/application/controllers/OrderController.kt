package com.otakuexchange.application.controllers

import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.market.Market
import com.otakuexchange.domain.market.Order
import com.otakuexchange.domain.market.Topic
import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.repositories.IOrderRecordRepository
import com.otakuexchange.domain.repositories.ITopicRepository
import com.otakuexchange.domain.repositories.ITradeHistoryRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.services.OrderMatchingService
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid

class OrderController(
    private val orderRecordRepository: IOrderRecordRepository,
    private val tradeHistoryRepository: ITradeHistoryRepository,
    private val marketRepository: IMarketRepository,
    private val eventRepository: IEventRepository,
    private val topicRepository: ITopicRepository,
    private val userRepository: IUserRepository,
    private val orderMatchingService: OrderMatchingService
) : IRouteController {

    // Dev bypass — set DEV_USER_ID in .env to a real user UUID to skip auth
    private val devUserId: String? = runCatching {
        System.getenv("DEV_USER_ID") ?: dotenv()["DEV_USER_ID"]
    }.getOrNull()

    private suspend fun resolveUser(call: io.ktor.server.application.ApplicationCall): User? {
        // If dev bypass is set, use that user directly
        if (devUserId != null) {
            return userRepository.findById(Uuid.parse(devUserId))
        }
        val clerkId = call.principal<JWTPrincipal>()?.payload?.subject ?: return null
        return userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
    }

    override fun registerRoutes(route: Route) {

        route.get("/orders/me") {
            val user = resolveUser(call)
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")
            val orders = orderRecordRepository.findByUserId(user.id)
            call.respond(orders)
        }

        route.get("/orders/{id}") {
            val id = try { Uuid.parse(call.parameters["id"] ?: "") }
            catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, "Invalid id"); return@get }
            val order = orderRecordRepository.findById(id)
            if (order == null) call.respond(HttpStatusCode.NotFound, "Order not found")
            else call.respond(order)
        }

        route.get("/markets/{marketId}/orders") {
            val marketId = try { Uuid.parse(call.parameters["marketId"] ?: "") }
            catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, "Invalid marketId"); return@get }
            val orders = orderRecordRepository.findByMarketId(marketId)
            call.respond(orders)
        }

        route.get("/markets/{marketId}/trades") {
            val marketId = try { Uuid.parse(call.parameters["marketId"] ?: "") }
            catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, "Invalid marketId"); return@get }
            val since = call.request.queryParameters["since"]?.toLongOrNull()
            val trades = if (since != null)
                tradeHistoryRepository.findByMarketIdSince(marketId, since)
            else
                tradeHistoryRepository.findByMarketId(marketId)
            call.respond(trades)
        }
    }

    override fun registerProtectedRoutes(route: Route) {

        route.post("/orders") {
            val user = resolveUser(call)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")

            val order = call.receive<Order>()

            val marketWithEntity = marketRepository.getById(order.marketId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Market not found")
            val eventWithBookmark = eventRepository.getById(marketWithEntity.eventId, user.id)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Event not found")
            val topic = topicRepository.getById(eventWithBookmark.topicId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Topic not found")

            val market = Market(
                id = marketWithEntity.id,
                eventId = marketWithEntity.eventId,
                label = marketWithEntity.label,
                status = marketWithEntity.status
            )
            val event = Event(
                id = eventWithBookmark.id,
                topicId = eventWithBookmark.topicId,
                format = eventWithBookmark.format,
                name = eventWithBookmark.name,
                description = eventWithBookmark.description,
                closeTime = eventWithBookmark.closeTime,
                status = eventWithBookmark.status,
                resolutionRule = eventWithBookmark.resolutionRule
            )

            val userOrder = order.copy(userId = user.id)
            orderMatchingService.submitOrder(userOrder, market, event, topic)
            call.respond(HttpStatusCode.Accepted, userOrder)
        }

        route.delete("/orders/{id}") {
            val id = try { Uuid.parse(call.parameters["id"] ?: "") }
            catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, "Invalid id"); return@delete }
            val user = resolveUser(call)
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, "User not found")
            val order = orderRecordRepository.findById(id)
            when {
                order == null -> call.respond(HttpStatusCode.NotFound, "Order not found")
                order.userId != user.id -> call.respond(HttpStatusCode.Forbidden, "Not your order")
                else -> {
                    orderMatchingService.cancelOrder(id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}