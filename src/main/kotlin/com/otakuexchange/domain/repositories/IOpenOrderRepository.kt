package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Order

interface IOpenOrderRepository {
    suspend fun insertOrder(order: Order) : Order
}