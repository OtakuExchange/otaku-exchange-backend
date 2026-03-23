package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.market.Entity
import com.otakuexchange.domain.market.Market
import com.otakuexchange.domain.market.MarketStatus
import com.otakuexchange.domain.market.MarketWithEntity
import com.otakuexchange.infra.tables.EntityTable
import com.otakuexchange.infra.tables.MarketTable
import com.otakuexchange.infra.tables.TradeHistoryTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonMarketRepository : IMarketRepository {

    private val RelatedEntityTable = EntityTable.alias("related_entity")

    private val marketWithEntityQuery
        get() = MarketTable
            .join(EntityTable, JoinType.LEFT, MarketTable.entityId, EntityTable.id)
            .join(RelatedEntityTable, JoinType.LEFT, MarketTable.relatedEntityId, RelatedEntityTable[EntityTable.id])

    override suspend fun getMarketsByEventId(eventId: Uuid): List<MarketWithEntity> = transaction {
        val markets = marketWithEntityQuery
            .selectAll()
            .where { MarketTable.eventId eq eventId }
            .map { it.toMarketWithEntity() }
        val marketIds = markets.map { it.id }
        val volumeByMarket = calcVolumeByMarket(marketIds)
        markets.map { it.copy(tradeVolume = volumeByMarket[it.id] ?: 0L) }
    }

    override suspend fun getById(id: Uuid): MarketWithEntity? = transaction {
        val market = marketWithEntityQuery
            .selectAll()
            .where { MarketTable.id eq id }
            .singleOrNull()
            ?.toMarketWithEntity() ?: return@transaction null
        val volume = calcVolumeByMarket(listOf(id))[id] ?: 0L
        market.copy(tradeVolume = volume)
    }

    override suspend fun save(market: Market): Market = transaction {
        MarketTable.insert {
            it[id]              = market.id
            it[eventId]         = market.eventId
            it[entityId]        = market.entityId
            it[relatedEntityId] = market.relatedEntityId
            it[label]           = market.label
            it[isMatch]         = market.isMatch
            it[createdAt]       = market.createdAt
            it[status]          = market.status.name
        }
        market
    }

    override suspend fun update(market: Market): Market = transaction {
        MarketTable.update({ MarketTable.id eq market.id }) {
            it[entityId]        = market.entityId
            it[relatedEntityId] = market.relatedEntityId
            it[label]           = market.label
            it[isMatch]         = market.isMatch
            it[status]          = market.status.name
        }
        market
    }

    override suspend fun updateStatus(marketId: Uuid, status: MarketStatus): Unit = transaction {
        MarketTable.update({ MarketTable.id eq marketId }) {
            it[MarketTable.status] = status.name
        }
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        MarketTable.deleteWhere { MarketTable.id eq id } > 0
    }

    // ── Volume ────────────────────────────────────────────────────────────────

    private fun calcVolumeByMarket(marketIds: List<Uuid>): Map<Uuid, Long> {
        if (marketIds.isEmpty()) return emptyMap()
        return TradeHistoryTable.selectAll()
            .where { TradeHistoryTable.marketId inList marketIds }
            .groupBy { it[TradeHistoryTable.marketId] }
            .mapValues { (_, trades) ->
                trades.sumOf {
                    it[TradeHistoryTable.escrowPerContract].toLong() * it[TradeHistoryTable.quantity].toLong()
                }
            }
    }

    // ── Row mappers ───────────────────────────────────────────────────────────

    private fun ResultRow.toMarketWithEntity() = MarketWithEntity(
        id            = this[MarketTable.id],
        eventId       = this[MarketTable.eventId],
        entity        = if (this[MarketTable.entityId] != null) toEntity() else null,
        relatedEntity = if (this[MarketTable.relatedEntityId] != null) toRelatedEntity() else null,
        label         = this[MarketTable.label],
        isMatch       = this[MarketTable.isMatch],
        createdAt     = this[MarketTable.createdAt],
        status        = this[MarketTable.status]
    )

    private fun ResultRow.toEntity() = Entity(
        id              = this[EntityTable.id],
        name            = this[EntityTable.name],
        abbreviatedName = this[EntityTable.abbreviatedName],
        logoPath        = this[EntityTable.logoPath],
        color           = this[EntityTable.color],
        pandaScoreId    = this[EntityTable.pandaScoreId],
        createdAt       = this[EntityTable.createdAt]
    )

    private fun ResultRow.toRelatedEntity() = Entity(
        id              = this[RelatedEntityTable[EntityTable.id]],
        name            = this[RelatedEntityTable[EntityTable.name]],
        abbreviatedName = this[RelatedEntityTable[EntityTable.abbreviatedName]],
        logoPath        = this[RelatedEntityTable[EntityTable.logoPath]],
        color           = this[RelatedEntityTable[EntityTable.color]],
        pandaScoreId    = this[RelatedEntityTable[EntityTable.pandaScoreId]],
        createdAt       = this[RelatedEntityTable[EntityTable.createdAt]]
    )

    private fun ResultRow.toMarket() = Market(
        id              = this[MarketTable.id],
        eventId         = this[MarketTable.eventId],
        entityId        = this[MarketTable.entityId],
        relatedEntityId = this[MarketTable.relatedEntityId],
        label           = this[MarketTable.label],
        isMatch         = this[MarketTable.isMatch],
        createdAt       = this[MarketTable.createdAt],
        status          = MarketStatus.valueOf(this[MarketTable.status])
    )
}
