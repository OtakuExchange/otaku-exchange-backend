package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.event.EventWithBookmark
import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.market.Subtopic
import com.otakuexchange.domain.repositories.ISubtopicRepository
import com.otakuexchange.infra.tables.BookmarkTable
import com.otakuexchange.infra.tables.EventSubtopicTable
import com.otakuexchange.infra.tables.EventTable
import com.otakuexchange.infra.tables.SubtopicTable
import com.otakuexchange.infra.tables.UserEventViewTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid
import com.otakuexchange.domain.event.EventStatus
import com.otakuexchange.infra.tables.parimutuel.FirstStakeBonusTable

class NeonSubtopicRepository : ISubtopicRepository {

    override suspend fun getByTopicId(topicId: Uuid): List<Subtopic> = transaction {
        SubtopicTable.selectAll()
            .where { SubtopicTable.topicId eq topicId }
            .map { it.toSubtopic() }
    }

    override suspend fun getById(id: Uuid): Subtopic? = transaction {
        SubtopicTable.selectAll()
            .where { SubtopicTable.id eq id }
            .singleOrNull()
            ?.toSubtopic()
    }

    override suspend fun save(subtopic: Subtopic): Subtopic = transaction {
        SubtopicTable.insert {
            it[id]        = subtopic.id
            it[topicId]   = subtopic.topicId
            it[name]      = subtopic.name
            it[createdAt] = subtopic.createdAt
        }
        subtopic
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        SubtopicTable.deleteWhere { SubtopicTable.id eq id } > 0
    }

    override suspend fun getEventsBySubtopicId(subtopicId: Uuid, currentUserId: Uuid?): List<EventWithBookmark> = transaction {
        val events = (EventSubtopicTable innerJoin EventTable)
            .selectAll()
            .where { EventSubtopicTable.subtopicId eq subtopicId }
            .map { row ->
                Event(
                    id             = row[EventTable.id],
                    topicId        = row[EventTable.topicId],
                    format         = row[EventTable.format],
                    name           = row[EventTable.name],
                    description    = row[EventTable.description],
                    closeTime      = row[EventTable.closeTime],
                    status = EventStatus.valueOf(row[EventTable.status]),
                    resolutionRule = row[EventTable.resolutionRule],
                    logoPath       = row[EventTable.logoPath],
                    pandaScoreId   = row[EventTable.pandaScoreId],
                    multiplier     = row[EventTable.multiplier],
                    alias          = row[EventTable.alias]
                )
            }

        events.map { event ->
            val bookmarked = if (currentUserId != null) {
                BookmarkTable.selectAll()
                    .where { (BookmarkTable.eventId eq event.id) and (BookmarkTable.userId eq currentUserId) }
                    .singleOrNull() != null
            } else false

            val isNew = if (currentUserId != null) {
                UserEventViewTable.selectAll()
                    .where { (UserEventViewTable.eventId eq event.id) and (UserEventViewTable.userId eq currentUserId) }
                    .singleOrNull() == null
            } else false

            val isFirstStakeBonusEligible = if (currentUserId != null) {
                FirstStakeBonusTable.selectAll()
                    .where {
                        (FirstStakeBonusTable.eventId eq event.id) and
                        (FirstStakeBonusTable.userId eq currentUserId)
                    }
                    .singleOrNull() == null
            } else false

            println(isFirstStakeBonusEligible)

            EventWithBookmark(
                id             = event.id,
                topicId        = event.topicId,
                format         = event.format,
                name           = event.name,
                description    = event.description,
                closeTime      = event.closeTime,
                status         = event.status,
                resolutionRule = event.resolutionRule,
                logoPath       = event.logoPath,
                pandaScoreId   = event.pandaScoreId,
                bookmarked     = bookmarked,
                multiplier     = event.multiplier,
                isNew          = isNew,
                isFirstStakeBonusEligible = isFirstStakeBonusEligible,
                alias          = event.alias,
                isNew          = isNew
            )
        }
    }

    override suspend fun addEventToSubtopic(eventId: Uuid, subtopicId: Uuid): Unit = transaction {
        EventSubtopicTable.insert {
            it[EventSubtopicTable.eventId]    = eventId
            it[EventSubtopicTable.subtopicId] = subtopicId
        }
    }

    override suspend fun removeEventFromSubtopic(eventId: Uuid, subtopicId: Uuid): Unit = transaction {
        EventSubtopicTable.deleteWhere {
            (EventSubtopicTable.eventId eq eventId) and (EventSubtopicTable.subtopicId eq subtopicId)
        }
    }

    private fun ResultRow.toSubtopic() = Subtopic(
        id        = this[SubtopicTable.id],
        topicId   = this[SubtopicTable.topicId],
        name      = this[SubtopicTable.name],
        createdAt = this[SubtopicTable.createdAt]
    )
}
