package com.otakuexchange.application.controllers

import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.market.Market
import com.otakuexchange.domain.market.MarketStatus
import com.otakuexchange.domain.market.Order
import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.market.OrderStatus
import com.otakuexchange.domain.market.OrderType
import com.otakuexchange.domain.market.Topic
import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.repositories.IOrderRecordRepository
import com.otakuexchange.domain.repositories.IPositionRepository
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
    private val positionRepository: IPositionRepository,
    private val orderMatchingService: OrderMatchingService
) : IRouteController {

    private val devUserId: String? = runCatching {
        System.getenv("DEV_USER_ID") ?: dotenv()["DEV_USER_ID"]
    }.getOrNull()

    private val seederUserId: Uuid = Uuid.parse(
        System.getenv("SEEDER_USER_ID") ?: "00000000-0000-0000-0000-000000000001"
    )

    private suspend fun resolveUser(call: io.ktor.server.application.ApplicationCall): User? {
        if (devUserId != null) return userRepository.findById(Uuid.parse(devUserId))
        val clerkId = call.principal<JWTPrincipal>()?.payload?.subject ?: return null
        return userRepository.findByProviderUserId(clerkId, AuthProvider.CLERK)
    }

    override fun registerRoutes(route: Route) {

        route.get("/orders/me") {
            val user = resolveUser(call)
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")
            val status = call.request.queryParameters["status"]?.let {
                runCatching { OrderStatus.valueOf(it.uppercase()) }.getOrNull()
            }
            val orderType = call.request.queryParameters["orderType"]?.let {
                runCatching { OrderType.valueOf(it.uppercase()) }.getOrNull()
            }
            val orders = orderRecordRepository.findByUserId(user.id, status, orderType)
            call.respond(orders)
        }

        route.get("/positions/me") {
            val user = resolveUser(call)
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")
            val positions = positionRepository.getPositionsByUser(user.id)
            call.respond(positions)
        }

        route.get("/positions/me/total") {
            val user = resolveUser(call)
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "User not found")
            val positions = positionRepository.getPositionsByUser(user.id)
            val total = positions.sumOf { it.avgPrice.toLong() * it.quantity.toLong() }
            call.respond(mapOf("total" to total))
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

            // Validate based on order type
            when (order.orderType) {
                OrderType.LIMIT -> {
                    if (order.price !in 1..99)
                        return@post call.respond(HttpStatusCode.BadRequest, "Price must be between 1 and 99 cents")
                    if (order.quantity <= 0)
                        return@post call.respond(HttpStatusCode.BadRequest, "Quantity must be greater than 0")
                }
                OrderType.MARKET -> {
                    if (order.quantity <= 0)
                        return@post call.respond(HttpStatusCode.BadRequest, "Quantity must be greater than 0")
                }
                OrderType.NOTIONAL -> {
                    if (order.notionalAmount == null || order.notionalAmount <= 0)
                        return@post call.respond(HttpStatusCode.BadRequest, "notionalAmount must be provided and greater than 0")
                    if (order.price !in 1..99)
                        return@post call.respond(HttpStatusCode.BadRequest, "price cap must be between 1 and 99 cents (use 99 for no cap)")
                }
            }

            // Calculate amount to lock:
            // LIMIT:    lock price × quantity
            // MARKET:   lock price × quantity (worst case, refunded if fills cheaper)
            // NOTIONAL: lock the full notional budget
            val cost: Long = when (order.orderType) {
                OrderType.LIMIT -> when (order.side) {
                    OrderSide.YES -> order.price.toLong() * order.quantity.toLong()
                    OrderSide.NO -> (100L - order.price.toLong()) * order.quantity.toLong()
                }
                OrderType.MARKET -> when (order.side) {
                    OrderSide.YES -> 99L * order.quantity.toLong() // worst case YES = 99¢
                    OrderSide.NO -> 99L * order.quantity.toLong()  // worst case NO = 99¢
                }
                OrderType.NOTIONAL -> order.notionalAmount!!
            }

            if (user.id != seederUserId) {
                val locked = userRepository.lockBalance(user.id, cost)
                if (!locked) {
                    return@post call.respond(
                        HttpStatusCode.PaymentRequired,
                        "Insufficient balance. Need ${cost}¢, available ${user.availableBalance}¢"
                    )
                }
            }

            val marketWithEntity = marketRepository.getById(order.marketId)
                ?: run {
                    if (user.id != seederUserId) userRepository.unlockBalance(user.id, cost)
                    return@post call.respond(HttpStatusCode.NotFound, "Market not found")
                }

            if (marketWithEntity.status != MarketStatus.OPEN.name) {
                if (user.id != seederUserId) userRepository.unlockBalance(user.id, cost)
                return@post call.respond(HttpStatusCode.Conflict, "Market is not open for trading")
            }

            val eventWithBookmark = eventRepository.getById(marketWithEntity.eventId, user.id)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Event not found")
            val topicWithSubtopics = topicRepository.getById(eventWithBookmark.topicId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Topic not found")

            val market = Market(
                id = marketWithEntity.id,
                eventId = marketWithEntity.eventId,
                label = marketWithEntity.label,
                status = MarketStatus.valueOf(marketWithEntity.status)
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
            val topic = Topic(
                id = topicWithSubtopics.id,
                topic = topicWithSubtopics.topic,
                description = topicWithSubtopics.description
            )

            val userOrder = order.copy(userId = user.id, lockedAmount = cost)
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