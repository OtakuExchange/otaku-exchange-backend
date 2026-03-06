package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.market.Market
import com.otakuexchange.infra.tables.MarketTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonMarketRepository : IMarketRepository {

    override suspend fun getMarketsByEventId(eventId: Uuid): List<Market> = transaction {
        MarketTable.selectAll()
            .where { MarketTable.eventId eq eventId }
            .map { it.toMarket() }
    }

    override suspend fun getById(id: Uuid): Market? = transaction {
        MarketTable.selectAll()
            .where { MarketTable.id eq id }
            .singleOrNull()
            ?.toMarket()
    }

    override suspend fun save(market: Market): Market = transaction {
        MarketTable.insert {
            it[id] = market.id
            it[eventId] = market.eventId
            it[label] = market.label
            it[status] = market.status
        }
        market
    }

    override suspend fun update(market: Market): Market = transaction {
        MarketTable.update({ MarketTable.id eq market.id }) {
            it[label] = market.label
            it[status] = market.status
        }
        market
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        MarketTable.deleteWhere { MarketTable.id eq id } > 0
    }

    // ── Row mapper ────────────────────────────────────────────────────────────

    private fun ResultRow.toMarket() = Market(
        id = this[MarketTable.id],
        eventId = this[MarketTable.eventId],
        label = this[MarketTable.label],
        status = this[MarketTable.status]
    )
}