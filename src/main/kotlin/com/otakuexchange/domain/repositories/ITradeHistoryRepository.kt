package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.TradeHistory
import kotlin.uuid.Uuid

interface ITradeHistoryRepository {
    suspend fun save(trade: TradeHistory): TradeHistory
    suspend fun findByMarketId(marketId: Uuid): List<TradeHistory>
    suspend fun findByMarketIdSince(marketId: Uuid, sinceEpochMillis: Long): List<TradeHistory>
    suspend fun getLastTradedPrice(marketId: Uuid): Int?  // null if no trades yet
}