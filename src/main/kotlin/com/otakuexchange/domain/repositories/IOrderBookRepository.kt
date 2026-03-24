package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Order
import com.otakuexchange.domain.market.OrderSide
import kotlin.uuid.Uuid

interface IOrderBookRepository {

    suspend fun insertOrder(order: Order): Order

    /**
     * Used by matching engine (core logic)
     * Fetches orders in price-time priority with pagination
     */
    suspend fun getBestOrdersPaged(
        marketId: Uuid,
        side: OrderSide,
        offset: Long,
        limit: Int
    ): List<Order>

    /**
     * Used for UI / simple reads (top of book)
     */
    suspend fun getBestOrders(
        marketId: Uuid,
        side: OrderSide,
        limit: Int = 10
    ): List<Order>

    suspend fun removeOrder(order: Order)

    suspend fun updateRemaining(order: Order)

    suspend fun getOrder(orderId: Uuid): Order?

    /**
     * Returns the lowest-priority (worst-priced) resting order for a side.
     * Used as a conservative fallback forecast when only one side has orders.
     */
    suspend fun getWorstOrder(marketId: Uuid, side: OrderSide): Order?
}
