package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.event.Event
import com.otakuexchange.infra.tables.EventTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class NeonEventRepository : IEventRepository {
    override suspend fun getEventsByTopicId(topicId: Int): List<Event> = transaction {
        EventTable.selectAll().where { EventTable.topicId eq topicId }.map {
            Event(
                eventId = it[EventTable.eventId],
                topicId = it[EventTable.topicId],
                format = it[EventTable.format],
                name = it[EventTable.name],
                description = it[EventTable.description],
                closeTime = it[EventTable.closeTime],
                status = it[EventTable.status],
                resolutionRule = it[EventTable.resolutionRule]
            )
        }
    }
}