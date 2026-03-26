package com.otakuexchange.infra.repositories.parimutuel

import com.otakuexchange.domain.parimutuel.MarketPool
import com.otakuexchange.domain.repositories.parimutuel.IMarketPoolRepository
import com.otakuexchange.infra.tables.parimutuel.MarketPoolTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class NeonMarketPoolRepository : IMarketPoolRepository {

    override suspend fun getByEventId(eventId: Uuid): List<MarketPool> = transaction {
        MarketPoolTable.selectAll()
            .where { MarketPoolTable.eventId eq eventId }
            .map { it.toMarketPool() }
    }

    override suspend fun getById(id: Uuid): MarketPool? = transaction {
        MarketPoolTable.selectAll()
            .where { MarketPoolTable.id eq id }
            .singleOrNull()
            ?.toMarketPool()
    }

    override suspend fun create(marketPool: MarketPool): MarketPool = transaction {
        MarketPoolTable.insert {
            it[id]        = marketPool.id
            it[eventId]   = marketPool.eventId
            it[entityId]  = marketPool.entityId
            it[label]     = marketPool.label
            it[isWinner]  = marketPool.isWinner
            it[amount]    = marketPool.amount
            it[createdAt] = marketPool.createdAt
            it[updatedAt] = marketPool.updatedAt
        }
        marketPool
    }

    override suspend fun update(marketPool: MarketPool): MarketPool = transaction {
        MarketPoolTable.update({ MarketPoolTable.id eq marketPool.id }) {
            it[label]     = marketPool.label
            it[isWinner]  = marketPool.isWinner
            it[amount]    = marketPool.amount
            it[updatedAt] = Clock.System.now()
        }
        marketPool
    }

    override suspend fun addToPool(id: Uuid, amount: Int): MarketPool = transaction {
        MarketPoolTable.update({ MarketPoolTable.id eq id }) {
            it[MarketPoolTable.amount]    = MarketPoolTable.amount + amount
            it[MarketPoolTable.updatedAt] = Clock.System.now()
        }
        MarketPoolTable.selectAll()
            .where { MarketPoolTable.id eq id }
            .single()
            .toMarketPool()
    }

    override suspend fun markWinner(id: Uuid): MarketPool = transaction {
        MarketPoolTable.update({ MarketPoolTable.id eq id }) {
            it[isWinner]  = true
            it[updatedAt] = Clock.System.now()
        }
        MarketPoolTable.selectAll()
            .where { MarketPoolTable.id eq id }
            .single()
            .toMarketPool()
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        MarketPoolTable.deleteWhere { MarketPoolTable.id eq id } > 0
    }

    private fun ResultRow.toMarketPool() = MarketPool(
        id        = this[MarketPoolTable.id],
        eventId   = this[MarketPoolTable.eventId],
        entityId  = this[MarketPoolTable.entityId],
        label     = this[MarketPoolTable.label],
        isWinner  = this[MarketPoolTable.isWinner],
        amount    = this[MarketPoolTable.amount],
        createdAt = this[MarketPoolTable.createdAt],
        updatedAt = this[MarketPoolTable.updatedAt]
    )
}