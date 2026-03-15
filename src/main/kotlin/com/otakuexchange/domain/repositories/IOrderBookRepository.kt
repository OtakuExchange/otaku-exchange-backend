package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Order
import com.otakuexchange.domain.market.OrderSide
import kotlin.uuid.Uuid

interface IOrderBookRepository {
    suspend fun insertOrder(order: Order): Order
    suspend fun getBestOrders(marketId: Uuid, side: OrderSide, limit: Int = 10): List<Order>
    suspend fun removeOrder(order: Order)
    suspend fun updateRemaining(order: Order)
    suspend fun getOrder(orderId: Uuid): Order?
}