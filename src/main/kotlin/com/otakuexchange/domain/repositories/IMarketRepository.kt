package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Market

interface IMarketRepository {
    suspend fun getMarketsByEventId(eventId: Int): List<Market>
}