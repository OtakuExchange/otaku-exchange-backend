package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.market.Market
import com.otakuexchange.infra.tables.MarketTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class NeonMarketRepository : IMarketRepository {
    override suspend fun getMarketsByEventId(eventId: Int): List<Market> = transaction {
        MarketTable.selectAll().where { MarketTable.eventId eq eventId }.map {
            Market(
                marketId = it[MarketTable.marketId],
                eventId = it[MarketTable.eventId],
                label = it[MarketTable.label],
                status = it[MarketTable.status]
            )
        }
    }
}