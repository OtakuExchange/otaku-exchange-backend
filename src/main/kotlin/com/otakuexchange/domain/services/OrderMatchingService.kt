package com.otakuexchange.domain.services

import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.market.Market
import com.otakuexchange.domain.market.Order
import com.otakuexchange.domain.market.OrderRecord
import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.market.OrderStatus
import com.otakuexchange.domain.market.OrderType
import com.otakuexchange.domain.market.Topic
import com.otakuexchange.domain.market.TradeHistory
import com.otakuexchange.domain.repositories.IOrderBookRepository
import com.otakuexchange.domain.repositories.IOrderRecordRepository
import com.otakuexchange.domain.repositories.IPositionRepository
import com.otakuexchange.domain.repositories.ITradeHistoryRepository
import com.otakuexchange.domain.repositories.IUserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import java.util.concurrent.ConcurrentHashMap

class OrderMatchingService(
    private val orderBookRepository: IOrderBookRepository,
    private val orderRecordRepository: IOrderRecordRepository,
    private val tradeHistoryRepository: ITradeHistoryRepository,
    private val userRepository: IUserRepository,
    private val positionRepository: IPositionRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val marketChannels = ConcurrentHashMap<Uuid, Channel<Order>>()

    private val seederUserId: Uuid = Uuid.parse(
        System.getenv("SEEDER_USER_ID") ?: "00000000-0000-0000-0000-000000000001"
    )

    private fun getOrCreateChannel(marketId: Uuid): Channel<Order> {
        return marketChannels.getOrPut(marketId) {
            Channel<Order>(Channel.UNLIMITED).also { channel ->
                scope.launch {
                    for (order in channel) {
                        processOrder(order)
                    }
                }
            }
        }
    }

    suspend fun submitOrder(order: Order, market: Market, event: Event, topic: Topic) {
        coroutineScope {
            val saveRecord = async(Dispatchers.IO) {
                orderRecordRepository.save(order.toRecord(OrderStatus.OPEN, market, event, topic))
            }
            val pushToRedis = async(Dispatchers.IO) {
                // NOTIONAL orders don't rest in the book — they fill immediately or not at all
                if (order.orderType != OrderType.NOTIONAL) {
                    orderBookRepository.insertOrder(order)
                }
            }
            saveRecord.await()
            pushToRedis.await()
        }
        getOrCreateChannel(order.marketId).send(order)
    }

    suspend fun cancelOrder(orderId: Uuid) {
        val order = orderBookRepository.getOrder(orderId) ?: return
        val record = orderRecordRepository.findById(orderId) ?: return
        coroutineScope {
            launch(Dispatchers.IO) { orderBookRepository.removeOrder(order) }
            launch(Dispatchers.IO) {
                orderRecordRepository.update(record.copy(
                    status = OrderStatus.CANCELLED,
                    updatedAt = Clock.System.now()
                ))
            }
            if (order.userId != seederUserId) {
                launch(Dispatchers.IO) {
                    val refund = order.lockedAmount * order.remaining / order.quantity
                    userRepository.unlockBalance(order.userId, refund)
                }
            }
        }
    }

    private suspend fun processOrder(incoming: Order) {
        when (incoming.orderType) {
            OrderType.LIMIT -> matchLimitOrder(incoming)
            OrderType.MARKET -> matchMarketOrder(incoming)
            OrderType.NOTIONAL -> matchNotionalOrder(incoming)
        }
    }

    private suspend fun matchLimitOrder(incoming: Order) {
        val oppositeSide = if (incoming.side == OrderSide.YES) OrderSide.NO else OrderSide.YES
        var remaining = incoming.remaining

        var offset = 0L
        val pageSize = 50

        while (remaining > 0) {
            val candidates = orderBookRepository.getBestOrdersPaged(
                incoming.marketId,
                oppositeSide,
                offset,
                pageSize
            )

            if (candidates.isEmpty()) break

            for (candidate in candidates) {
                if (remaining <= 0) break

                // 🚫 self-trade prevention
                if (candidate.userId == incoming.userId) continue

                val pricesCross = incoming.price + candidate.price >= 100
                if (!pricesCross) {
                    // nothing deeper will match
                    remaining = incoming.remaining - (incoming.remaining - remaining)
                    break
                }

                val fillQty = minOf(remaining, candidate.remaining)
                remaining -= fillQty

                executeFill(incoming, candidate, fillQty, isIncomingYes = incoming.side == OrderSide.YES)
            }

            // move to next page
            offset += pageSize
        }

        val now = Clock.System.now()
        val incomingFulfilled = remaining <= 0
        val incomingStatus = when {
            incomingFulfilled -> OrderStatus.FULFILLED
            remaining < incoming.quantity -> OrderStatus.PARTIALLY_FILLED
            else -> OrderStatus.OPEN
        }

        coroutineScope {
            launch(Dispatchers.IO) {
                if (incomingFulfilled) orderBookRepository.removeOrder(incoming.copy(remaining = 0))
                else orderBookRepository.updateRemaining(incoming.copy(remaining = remaining))
            }
            launch(Dispatchers.IO) { updateRecord(incoming.id, remaining, incomingStatus, now) }
        }
    }


    private suspend fun matchMarketOrder(incoming: Order) {
        val oppositeSide = if (incoming.side == OrderSide.YES) OrderSide.NO else OrderSide.YES
        var remaining = incoming.remaining

        var offset = 0L
        val pageSize = 50

        while (remaining > 0) {
            val candidates = orderBookRepository.getBestOrdersPaged(
                incoming.marketId,
                oppositeSide,
                offset,
                pageSize
            )

            if (candidates.isEmpty()) break

            for (candidate in candidates) {
                if (remaining <= 0) break

                if (candidate.userId == incoming.userId) continue

                val fillQty = minOf(remaining, candidate.remaining)
                remaining -= fillQty

                executeFill(incoming, candidate, fillQty, isIncomingYes = incoming.side == OrderSide.YES)
            }

            offset += pageSize
        }

        val now = Clock.System.now()
        val finalStatus = if (remaining <= 0) OrderStatus.FULFILLED else OrderStatus.CANCELLED

        coroutineScope {
            launch(Dispatchers.IO) { orderBookRepository.removeOrder(incoming.copy(remaining = remaining)) }
            launch(Dispatchers.IO) { updateRecord(incoming.id, remaining, finalStatus, now) }

            if (remaining > 0 && incoming.userId != seederUserId) {
                launch(Dispatchers.IO) {
                    val refund = 99L * remaining.toLong()
                    userRepository.unlockBalance(incoming.userId, refund)
                }
            }
        }
    }

    private suspend fun matchNotionalOrder(incoming: Order) {
        val oppositeSide = if (incoming.side == OrderSide.YES) OrderSide.NO else OrderSide.YES
        var budgetRemaining = incoming.notionalAmount ?: return
        var totalFilled = 0
        val maxPriceCap = incoming.price

        var offset = 0L
        val pageSize = 50

        while (budgetRemaining > 0) {
            val candidates = orderBookRepository.getBestOrdersPaged(
                incoming.marketId,
                oppositeSide,
                offset,
                pageSize
            )

            if (candidates.isEmpty()) break

            for (candidate in candidates) {
                if (budgetRemaining <= 0) break

                if (candidate.userId == incoming.userId) continue

                val incomingExecPrice = 100 - candidate.price

                if (incomingExecPrice > maxPriceCap) {
                    budgetRemaining = 0
                    break
                }

                val maxAffordable = (budgetRemaining / incomingExecPrice.toLong()).toInt()
                if (maxAffordable <= 0) break

                val fillQty = minOf(maxAffordable, candidate.remaining)
                val fillCost = fillQty.toLong() * incomingExecPrice.toLong()

                budgetRemaining -= fillCost
                totalFilled += fillQty

                executeFill(incoming, candidate, fillQty, isIncomingYes = incoming.side == OrderSide.YES)
            }

            offset += pageSize
        }

        val now = Clock.System.now()
        val finalStatus = if (totalFilled > 0) OrderStatus.FULFILLED else OrderStatus.CANCELLED

        if (budgetRemaining > 0 && incoming.userId != seederUserId) {
            userRepository.unlockBalance(incoming.userId, budgetRemaining)
        }

        updateRecord(incoming.id, 0, finalStatus, now)
    }

    private suspend fun executeFill(incoming: Order, candidate: Order, fillQty: Int, isIncomingYes: Boolean) {
        val now = Clock.System.now()
        val candidateFilled = candidate.remaining - fillQty <= 0
        val updatedCandidate = candidate.copy(remaining = candidate.remaining - fillQty)

        val yesOrder = if (isIncomingYes) incoming else candidate
        val noOrder = if (!isIncomingYes) incoming else candidate

        // Execution price = resting candidate's price
        val yesExecPrice = if (candidate.side == OrderSide.YES) candidate.price else (100 - candidate.price)
        val noExecPrice = 100 - yesExecPrice  // always sums to 100¢

        val trade = TradeHistory(
            marketId = incoming.marketId,
            yesOrderId = yesOrder.id,
            noOrderId = noOrder.id,
            yesPrice = yesExecPrice,
            noPrice = noExecPrice,
            quantity = fillQty,
            executedAt = now
        )

        coroutineScope {
            launch(Dispatchers.IO) { tradeHistoryRepository.save(trade) }
            launch(Dispatchers.IO) {
                if (candidateFilled) orderBookRepository.removeOrder(candidate)
                else orderBookRepository.updateRemaining(updatedCandidate)
            }
            launch(Dispatchers.IO) {
                updateRecord(
                    candidate.id,
                    if (candidateFilled) 0 else updatedCandidate.remaining,
                    if (candidateFilled) OrderStatus.FULFILLED else OrderStatus.PARTIALLY_FILLED,
                    now
                )
            }
            // YES side settlement with surplus refund
            if (yesOrder.userId != seederUserId) {
                launch(Dispatchers.IO) {
                    val yesActualCost = yesExecPrice.toLong() * fillQty.toLong()
                    val yesLockedPerContract = if (yesOrder.id == incoming.id) incoming.price.toLong()
                                               else candidate.price.toLong()
                    val yesSurplus = (yesLockedPerContract * fillQty) - yesActualCost
                    userRepository.consumeLockedBalance(yesOrder.userId, yesActualCost)
                    if (yesSurplus > 0) userRepository.unlockBalance(yesOrder.userId, yesSurplus)
                    positionRepository.upsertPosition(yesOrder.userId, incoming.marketId, OrderSide.YES, fillQty, yesExecPrice, yesActualCost)
                }
            }
            // NO side settlement with surplus refund
            if (noOrder.userId != seederUserId) {
                launch(Dispatchers.IO) {
                    val noActualCost = noExecPrice.toLong() * fillQty.toLong()
                    val noLockedPerContract = if (noOrder.id == incoming.id) incoming.price.toLong()
                                              else candidate.price.toLong()
                    val noSurplus = (noLockedPerContract * fillQty) - noActualCost
                    userRepository.consumeLockedBalance(noOrder.userId, noActualCost)
                    if (noSurplus > 0) userRepository.unlockBalance(noOrder.userId, noSurplus)
                    positionRepository.upsertPosition(noOrder.userId, incoming.marketId, OrderSide.NO, fillQty, noExecPrice, noActualCost)
                }
            }
        }
    }

    private suspend fun updateRecord(id: Uuid, remaining: Int, status: OrderStatus, now: Instant) {
        val record = orderRecordRepository.findById(id) ?: return
        orderRecordRepository.update(record.copy(
            remaining = remaining,
            status = status,
            updatedAt = now
        ))
    }

    private fun Order.toRecord(
        status: OrderStatus,
        market: Market,
        event: Event,
        topic: Topic
    ) = OrderRecord(
        id = id,
        userId = userId,
        marketId = marketId,
        marketLabel = market.label,
        eventId = event.id,
        eventName = event.name,
        topicId = topic.id,
        topicName = topic.topic,
        side = side,
        price = price,
        quantity = quantity,
        remaining = remaining,
        lockedAmount = lockedAmount,
        notionalAmount = notionalAmount,
        status = status,
        orderType = orderType,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}