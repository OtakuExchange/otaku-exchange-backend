package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.market.TradeHistory
import com.otakuexchange.domain.repositories.ITradeHistoryRepository
import com.otakuexchange.infra.tables.TradeHistoryTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

class NeonTradeHistoryRepository : ITradeHistoryRepository {

    override suspend fun save(trade: TradeHistory): TradeHistory = transaction {
        TradeHistoryTable.insert {
            it[id] = trade.id
            it[marketId] = trade.marketId
            it[buyOrderId] = trade.buyOrderId
            it[sellOrderId] = trade.sellOrderId
            it[price] = trade.price
            it[quantity] = trade.quantity
            it[executedAt] = trade.executedAt
        }
        trade
    }

    override suspend fun findByMarketId(marketId: Uuid): List<TradeHistory> = transaction {
        TradeHistoryTable.selectAll()
            .where { TradeHistoryTable.marketId eq marketId }
            .orderBy(TradeHistoryTable.executedAt)
            .map { it.toTradeHistory() }
    }

    override suspend fun findByMarketIdSince(marketId: Uuid, sinceEpochMillis: Long): List<TradeHistory> = transaction {
        val since = Instant.fromEpochMilliseconds(sinceEpochMillis)
        TradeHistoryTable.selectAll()
            .where {
                (TradeHistoryTable.marketId eq marketId) and
                (TradeHistoryTable.executedAt greaterEq since)
            }
            .orderBy(TradeHistoryTable.executedAt)
            .map { it.toTradeHistory() }
    }

    private fun ResultRow.toTradeHistory() = TradeHistory(
        id = this[TradeHistoryTable.id],
        marketId = this[TradeHistoryTable.marketId],
        buyOrderId = this[TradeHistoryTable.buyOrderId],
        sellOrderId = this[TradeHistoryTable.sellOrderId],
        price = this[TradeHistoryTable.price],
        quantity = this[TradeHistoryTable.quantity],
        executedAt = this[TradeHistoryTable.executedAt]
    )
}