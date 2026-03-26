package com.otakuexchange.domain.repositories.parimutuel

import com.otakuexchange.domain.parimutuel.MarketPool
import kotlin.uuid.Uuid

interface IMarketPoolRepository {
    suspend fun getByEventId(eventId: Uuid): List<MarketPool>
    suspend fun getById(id: Uuid): MarketPool?
    suspend fun create(marketPool: MarketPool): MarketPool
    suspend fun update(marketPool: MarketPool): MarketPool
    suspend fun addToPool(id: Uuid, amount: Int): MarketPool
    suspend fun markWinner(id: Uuid): MarketPool
    suspend fun delete(id: Uuid): Boolean
}