package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.market.Position
import kotlin.uuid.Uuid

interface IPositionRepository {
    suspend fun getPosition(userId: Uuid, marketId: Uuid, side: OrderSide): Position?
    suspend fun getPositionsByUser(userId: Uuid): List<Position>
    suspend fun getPositionsByMarket(marketId: Uuid): List<Position>
    suspend fun upsertPosition(userId: Uuid, marketId: Uuid, side: OrderSide, quantity: Int, price: Int, lockedAmount: Long): Position
    suspend fun deletePosition(userId: Uuid, marketId: Uuid, side: OrderSide)
}