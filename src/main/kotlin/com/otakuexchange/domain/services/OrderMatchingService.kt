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
import com.otakuexchange.domain.repositories.ITradeHistoryRepository
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
    private val openOrderRepository: IOrderBookRepository,
    private val orderRecordRepository: IOrderRecordRepository,
    private val tradeHistoryRepository: ITradeHistoryRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val marketChannels = ConcurrentHashMap<Uuid, Channel<Order>>()

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
                openOrderRepository.insertOrder(order)
            }
            saveRecord.await()
            pushToRedis.await()
        }
        getOrCreateChannel(order.marketId).send(order)
    }

    suspend fun cancelOrder(orderId: Uuid) {
        val order = openOrderRepository.getOrder(orderId) ?: return
        val record = orderRecordRepository.findById(orderId) ?: return
        coroutineScope {
            launch(Dispatchers.IO) { openOrderRepository.removeOrder(order) }
            launch(Dispatchers.IO) {
                orderRecordRepository.update(record.copy(
                    status = OrderStatus.CANCELLED,
                    updatedAt = Clock.System.now()
                ))
            }
        }
    }

    private suspend fun processOrder(incoming: Order) {
        when (incoming.orderType) {
            OrderType.LIMIT -> matchLimitOrder(incoming)
            OrderType.MARKET -> matchMarketOrder(incoming)
        }
    }

    private suspend fun matchLimitOrder(incoming: Order) {
        val oppositeSide = if (incoming.side == OrderSide.BUY) OrderSide.SELL else OrderSide.BUY
        var remaining = incoming.remaining

        val candidates = openOrderRepository.getBestOrders(incoming.marketId, oppositeSide)

        for (candidate in candidates) {
            if (remaining <= 0) break

            val pricesCross = when (incoming.side) {
                OrderSide.BUY -> incoming.price >= candidate.price
                OrderSide.SELL -> incoming.price <= candidate.price
            }
            if (!pricesCross) break

            val fillQty = minOf(remaining, candidate.remaining)
            remaining -= fillQty
            val now = Clock.System.now()
            val candidateFilled = candidate.remaining - fillQty <= 0
            val updatedCandidate = candidate.copy(remaining = candidate.remaining - fillQty)

            val trade = TradeHistory(
                marketId = incoming.marketId,
                buyOrderId = if (incoming.side == OrderSide.BUY) incoming.id else candidate.id,
                sellOrderId = if (incoming.side == OrderSide.SELL) incoming.id else candidate.id,
                price = candidate.price,
                quantity = fillQty,
                executedAt = now
            )

            coroutineScope {
                launch(Dispatchers.IO) { tradeHistoryRepository.save(trade) }
                launch(Dispatchers.IO) {
                    if (candidateFilled) openOrderRepository.removeOrder(candidate)
                    else openOrderRepository.updateRemaining(updatedCandidate)
                }
                launch(Dispatchers.IO) {
                    updateRecord(
                        candidate.id,
                        if (candidateFilled) 0 else updatedCandidate.remaining,
                        if (candidateFilled) OrderStatus.FULFILLED else OrderStatus.PARTIALLY_FILLED,
                        now
                    )
                }
            }
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
                if (incomingFulfilled) openOrderRepository.removeOrder(incoming.copy(remaining = 0))
                else openOrderRepository.updateRemaining(incoming.copy(remaining = remaining))
            }
            launch(Dispatchers.IO) {
                updateRecord(incoming.id, remaining, incomingStatus, now)
            }
        }
    }

    private suspend fun matchMarketOrder(incoming: Order) {
        val oppositeSide = if (incoming.side == OrderSide.BUY) OrderSide.SELL else OrderSide.BUY
        var remaining = incoming.remaining

        val candidates = openOrderRepository.getBestOrders(incoming.marketId, oppositeSide, limit = 50)

        for (candidate in candidates) {
            if (remaining <= 0) break

            val fillQty = minOf(remaining, candidate.remaining)
            remaining -= fillQty
            val now = Clock.System.now()
            val candidateFilled = candidate.remaining - fillQty <= 0
            val updatedCandidate = candidate.copy(remaining = candidate.remaining - fillQty)

            val trade = TradeHistory(
                marketId = incoming.marketId,
                buyOrderId = if (incoming.side == OrderSide.BUY) incoming.id else candidate.id,
                sellOrderId = if (incoming.side == OrderSide.SELL) incoming.id else candidate.id,
                price = candidate.price,
                quantity = fillQty,
                executedAt = now
            )

            coroutineScope {
                launch(Dispatchers.IO) { tradeHistoryRepository.save(trade) }
                launch(Dispatchers.IO) {
                    if (candidateFilled) openOrderRepository.removeOrder(candidate)
                    else openOrderRepository.updateRemaining(updatedCandidate)
                }
                launch(Dispatchers.IO) {
                    updateRecord(
                        candidate.id,
                        if (candidateFilled) 0 else updatedCandidate.remaining,
                        if (candidateFilled) OrderStatus.FULFILLED else OrderStatus.PARTIALLY_FILLED,
                        now
                    )
                }
            }
        }

        val now = Clock.System.now()
        val finalStatus = if (remaining <= 0) OrderStatus.FULFILLED else OrderStatus.CANCELLED
        coroutineScope {
            launch(Dispatchers.IO) {
                openOrderRepository.removeOrder(incoming.copy(remaining = remaining))
            }
            launch(Dispatchers.IO) {
                updateRecord(incoming.id, remaining, finalStatus, now)
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
        status = status,
        orderType = orderType,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}