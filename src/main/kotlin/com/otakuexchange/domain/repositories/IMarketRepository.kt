package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Market
import com.otakuexchange.domain.market.MarketWithEntity
import kotlin.uuid.Uuid

interface IMarketRepository {
    suspend fun getMarketsByEventId(eventId: Uuid): List<MarketWithEntity>
    suspend fun getById(id: Uuid): MarketWithEntity?
    suspend fun save(market: Market): Market
    suspend fun update(market: Market): Market
    suspend fun delete(id: Uuid): Boolean
}