package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.market.Entity
import com.otakuexchange.domain.market.Market
import com.otakuexchange.domain.market.MarketWithEntity
import com.otakuexchange.infra.tables.EntityTable
import com.otakuexchange.infra.tables.MarketTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonMarketRepository : IMarketRepository {

    private val marketWithEntityQuery
        get() = MarketTable.join(EntityTable, JoinType.LEFT, MarketTable.entityId, EntityTable.id)

    override suspend fun getMarketsByEventId(eventId: Uuid): List<MarketWithEntity> = transaction {
        marketWithEntityQuery
            .selectAll()
            .where { MarketTable.eventId eq eventId }
            .map { it.toMarketWithEntity() }
    }

    override suspend fun getById(id: Uuid): MarketWithEntity? = transaction {
        marketWithEntityQuery
            .selectAll()
            .where { MarketTable.id eq id }
            .singleOrNull()
            ?.toMarketWithEntity()
    }

    override suspend fun save(market: Market): Market = transaction {
        MarketTable.insert {
            it[id] = market.id
            it[eventId] = market.eventId
            it[entityId] = market.entityId
            it[label] = market.label
            it[status] = market.status
        }
        market
    }

    override suspend fun update(market: Market): Market = transaction {
        MarketTable.update({ MarketTable.id eq market.id }) {
            it[entityId] = market.entityId
            it[label] = market.label
            it[status] = market.status
        }
        market
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        MarketTable.deleteWhere { MarketTable.id eq id } > 0
    }

    // ── Row mappers ───────────────────────────────────────────────────────────

    private fun ResultRow.toMarketWithEntity() = MarketWithEntity(
        id = this[MarketTable.id],
        eventId = this[MarketTable.eventId],
        entity = if (this[MarketTable.entityId] != null) toEntity() else null,
        label = this[MarketTable.label],
        status = this[MarketTable.status]
    )

    private fun ResultRow.toEntity() = Entity(
        id = this[EntityTable.id],
        name = this[EntityTable.name],
        abbreviatedName = this[EntityTable.abbreviatedName],
        logoPath = this[EntityTable.logoPath],
        color = this[EntityTable.color],
        createdAt = this[EntityTable.createdAt]
    )

    private fun ResultRow.toMarket() = Market(
        id = this[MarketTable.id],
        eventId = this[MarketTable.eventId],
        entityId = this[MarketTable.entityId],
        label = this[MarketTable.label],
        status = this[MarketTable.status]
    )
}
