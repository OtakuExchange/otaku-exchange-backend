package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.event.Event
import com.otakuexchange.infra.tables.EventTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonEventRepository : IEventRepository {

    override suspend fun getEventsByTopicId(topicId: Uuid): List<Event> = transaction {
        EventTable.selectAll()
            .where { EventTable.topicId eq topicId }
            .map { it.toEvent() }
    }

    override suspend fun getById(id: Uuid): Event? = transaction {
        EventTable.selectAll()
            .where { EventTable.id eq id }
            .singleOrNull()
            ?.toEvent()
    }

    override suspend fun save(event: Event): Event = transaction {
        EventTable.insert {
            it[id] = event.id
            it[topicId] = event.topicId
            it[format] = event.format
            it[name] = event.name
            it[description] = event.description
            it[closeTime] = event.closeTime
            it[status] = event.status
            it[resolutionRule] = event.resolutionRule
        }
        event
    }

    override suspend fun update(event: Event): Event = transaction {
        EventTable.update({ EventTable.id eq event.id }) {
            it[format] = event.format
            it[name] = event.name
            it[description] = event.description
            it[closeTime] = event.closeTime
            it[status] = event.status
            it[resolutionRule] = event.resolutionRule
        }
        event
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        EventTable.deleteWhere { EventTable.id eq id } > 0
    }

    // ── Row mapper ────────────────────────────────────────────────────────────

    private fun ResultRow.toEvent() = Event(
        id = this[EventTable.id],
        topicId = this[EventTable.topicId],
        format = this[EventTable.format],
        name = this[EventTable.name],
        description = this[EventTable.description],
        closeTime = this[EventTable.closeTime],
        status = this[EventTable.status],
        resolutionRule = this[EventTable.resolutionRule]
    )
}