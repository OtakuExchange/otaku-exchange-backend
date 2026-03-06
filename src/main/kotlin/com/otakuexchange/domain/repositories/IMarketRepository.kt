package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Market
import kotlin.uuid.Uuid

interface IMarketRepository {
    suspend fun getMarketsByEventId(eventId: Uuid): List<Market>
    suspend fun getById(id: Uuid): Market?
    suspend fun save(market: Market): Market
    suspend fun update(market: Market): Market
    suspend fun delete(id: Uuid): Boolean
}