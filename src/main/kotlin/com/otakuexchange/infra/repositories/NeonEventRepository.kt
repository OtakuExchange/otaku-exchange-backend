package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.event.EventWithBookmark
import com.otakuexchange.infra.tables.BookmarkTable
import com.otakuexchange.infra.tables.EventTable
import com.otakuexchange.infra.tables.MarketTable
import com.otakuexchange.infra.tables.TradeHistoryTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonEventRepository : IEventRepository {

    override suspend fun getEventsByTopicId(topicId: Uuid, currentUserId: Uuid?): List<EventWithBookmark> = transaction {
        val events = EventTable.selectAll()
            .where { EventTable.topicId eq topicId }
            .map { it.toEvent() }
        val eventIds = events.map { it.id }
        val volumeByEvent = calcVolumeByEvent(eventIds)
        events.map { it.withBookmark(currentUserId, volumeByEvent[it.id] ?: 0L) }
    }

    override suspend fun getById(id: Uuid, currentUserId: Uuid?): EventWithBookmark? = transaction {
        val event = EventTable.selectAll()
            .where { EventTable.id eq id }
            .singleOrNull()
            ?.toEvent() ?: return@transaction null
        val volume = calcVolumeByEvent(listOf(id))[id] ?: 0L
        event.withBookmark(currentUserId, volume)
    }

    override suspend fun save(event: Event): Event = transaction {
        EventTable.insert {
            it[id]             = event.id
            it[topicId]        = event.topicId
            it[format]         = event.format
            it[name]           = event.name
            it[description]    = event.description
            it[closeTime]      = event.closeTime
            it[status]         = event.status
            it[resolutionRule] = event.resolutionRule
            it[logoPath]       = event.logoPath
            it[pandaScoreId]   = event.pandaScoreId
            it[createdAt]      = event.createdAt
        }
        event
    }

    override suspend fun update(event: Event): Event = transaction {
        EventTable.update({ EventTable.id eq event.id }) {
            it[format]         = event.format
            it[name]           = event.name
            it[description]    = event.description
            it[closeTime]      = event.closeTime
            it[status]         = event.status
            it[resolutionRule] = event.resolutionRule
            it[logoPath]       = event.logoPath
            it[pandaScoreId]   = event.pandaScoreId
        }
        event
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        EventTable.deleteWhere { EventTable.id eq id } > 0
    }

    override suspend fun closeStaking(id: Uuid): Boolean = transaction {
        EventTable.update({ EventTable.id eq id }) {
            it[status] = "STAKING_CLOSED"
        } > 0
    }

    // ── Row mapper ────────────────────────────────────────────────────────────

    private fun ResultRow.toEvent() = Event(
        id             = this[EventTable.id],
        topicId        = this[EventTable.topicId],
        format         = this[EventTable.format],
        name           = this[EventTable.name],
        description    = this[EventTable.description],
        closeTime      = this[EventTable.closeTime],
        status         = this[EventTable.status],
        resolutionRule = this[EventTable.resolutionRule],
        logoPath       = this[EventTable.logoPath],
        pandaScoreId   = this[EventTable.pandaScoreId],
        createdAt      = this[EventTable.createdAt]
    )

    private fun calcVolumeByEvent(eventIds: List<Uuid>): Map<Uuid, Long> {
        if (eventIds.isEmpty()) return emptyMap()
        val marketsByEvent = MarketTable.selectAll()
            .where { MarketTable.eventId inList eventIds }
            .groupBy({ it[MarketTable.eventId] }, { it[MarketTable.id] })

        val allMarketIds = marketsByEvent.values.flatten()
        if (allMarketIds.isEmpty()) return emptyMap()

        val tradesByMarket = TradeHistoryTable.selectAll()
            .where { TradeHistoryTable.marketId inList allMarketIds }
            .groupBy { it[TradeHistoryTable.marketId] }

        return marketsByEvent.mapValues { (_, marketIds) ->
            marketIds.sumOf { marketId ->
                tradesByMarket[marketId]?.sumOf {
                    it[TradeHistoryTable.escrowPerContract].toLong() * it[TradeHistoryTable.quantity].toLong()
                } ?: 0L
            }
        }
    }

    private fun Event.withBookmark(currentUserId: Uuid?, tradeVolume: Long = 0L): EventWithBookmark {
        val bookmarked = if (currentUserId != null) {
            BookmarkTable.selectAll()
                .where { (BookmarkTable.eventId eq id) and (BookmarkTable.userId eq currentUserId) }
                .singleOrNull() != null
        } else false

        return EventWithBookmark(
            id             = id,
            topicId        = topicId,
            format         = format,
            name           = name,
            description    = description,
            closeTime      = closeTime,
            status         = status,
            resolutionRule = resolutionRule,
            logoPath       = logoPath,
            pandaScoreId   = pandaScoreId,
            createdAt      = createdAt,
            tradeVolume    = tradeVolume,
            bookmarked     = bookmarked
        )
    }
}
