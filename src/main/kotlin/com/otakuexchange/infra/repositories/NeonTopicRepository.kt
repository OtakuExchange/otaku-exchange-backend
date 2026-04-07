package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.ITopicRepository
import com.otakuexchange.domain.market.Topic
import com.otakuexchange.domain.market.TopicWithSubtopics
import com.otakuexchange.domain.market.Subtopic
import com.otakuexchange.infra.tables.EventSubtopicTable
import com.otakuexchange.infra.tables.EventTable
import com.otakuexchange.infra.tables.TopicTable
import com.otakuexchange.infra.tables.SubtopicTable
import com.otakuexchange.infra.tables.UserEventViewTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonTopicRepository : ITopicRepository {

    override suspend fun getTopics(currentUserId: Uuid?): List<TopicWithSubtopics> = transaction {
        val topics = TopicTable.selectAll().map { it.toTopic() }
        val topicIds = topics.map { it.id }
        val subtopics = if (topicIds.isEmpty()) emptyList() else {
            SubtopicTable.selectAll()
                .where { SubtopicTable.topicId inList topicIds }
                .map { it.toSubtopic() }
        }
        val allEventSubtopicRows = EventSubtopicTable.selectAll().toList()
        val openEventIds = EventTable.selectAll()
            .where { EventTable.status eq "open" }
            .map { it[EventTable.id].toString() }
            .toSet()
        val viewedEventIds = if (currentUserId != null) {
            UserEventViewTable.selectAll()
                .where { UserEventViewTable.userId eq currentUserId }
                .map { it[UserEventViewTable.eventId].toString() }
                .toSet()
        } else emptySet()
        val subtopicsWithNew = subtopics.map { subtopic ->
            val subtopicEvents = allEventSubtopicRows.filter {
                it[EventSubtopicTable.subtopicId].toString() == subtopic.id.toString()
            }
            val isNew = currentUserId != null && subtopicEvents.any { row ->
                val eventId = row[EventSubtopicTable.eventId].toString()
                eventId in openEventIds && eventId !in viewedEventIds
            }
            subtopic.copy(isNew = isNew)
        }
        val subtopicsByTopic = subtopicsWithNew.groupBy { it.topicId }
        topics.map { topic ->
            TopicWithSubtopics(
                id          = topic.id,
                topic       = topic.topic,
                description = topic.description,
                hidden      = topic.hidden,
                subtopics   = subtopicsByTopic[topic.id] ?: emptyList()
            )
        }
    }

    override suspend fun getById(id: Uuid, currentUserId: Uuid?): TopicWithSubtopics? = transaction {
        val topic = TopicTable.selectAll()
            .where { TopicTable.id eq id }
            .singleOrNull()
            ?.toTopic() ?: return@transaction null

        val subtopics = SubtopicTable.selectAll()
            .where { SubtopicTable.topicId eq id }
            .map { it.toSubtopic() }

        val eventSubtopicRows = EventSubtopicTable.selectAll().toList()
        val openEventIds = EventTable.selectAll()
            .where { EventTable.status eq "open" }
            .map { it[EventTable.id].toString() }
            .toSet()
        val viewedEventIds = if (currentUserId != null) {
            UserEventViewTable.selectAll()
                .where { UserEventViewTable.userId eq currentUserId }
                .map { it[UserEventViewTable.eventId].toString() }
                .toSet()
        } else emptySet()
        val subtopicsWithNew = subtopics.map { subtopic ->
            val subtopicEvents = eventSubtopicRows.filter {
                it[EventSubtopicTable.subtopicId].toString() == subtopic.id.toString()
            }
            val isNew = currentUserId != null && subtopicEvents.any { row ->
                val eventId = row[EventSubtopicTable.eventId].toString()
                eventId in openEventIds && eventId !in viewedEventIds
            }
            subtopic.copy(isNew = isNew)
        }

        TopicWithSubtopics(
            id          = topic.id,
            topic       = topic.topic,
            description = topic.description,
            hidden      = topic.hidden,
            subtopics   = subtopicsWithNew
        )
    }

    override suspend fun getEventCountsBySubtopic(topicId: Uuid): Map<Uuid, Map<String, Int>> = transaction {
        val subtopicIds = SubtopicTable
            .selectAll()
            .where { SubtopicTable.topicId eq topicId }
            .map { it[SubtopicTable.id] }

        if (subtopicIds.isEmpty()) return@transaction emptyMap()

        val counts: MutableMap<Uuid, MutableMap<String, Int>> =
            subtopicIds.associateWith { mutableMapOf<String, Int>() }.toMutableMap()

        val countExpr = EventTable.id.count()
        (EventSubtopicTable innerJoin EventTable)
            .select(EventSubtopicTable.subtopicId, EventTable.status, countExpr)
            .where { EventSubtopicTable.subtopicId inList subtopicIds }
            .groupBy(EventSubtopicTable.subtopicId, EventTable.status)
            .forEach { row ->
                val subId = row[EventSubtopicTable.subtopicId]
                val status = row[EventTable.status]
                val n = row[countExpr].toInt()
                counts.getValue(subId)[status] = n
            }

        counts.mapValues { (_, byStatus) -> byStatus.toMap() }
    }

    override suspend fun save(topic: Topic): Topic = transaction {
        TopicTable.insert {
            it[id] = topic.id
            it[TopicTable.topic] = topic.topic
            it[description] = topic.description
            it[hidden] = topic.hidden
        }
        topic
    }

    override suspend fun update(topic: Topic): Topic = transaction {
        TopicTable.update({ TopicTable.id eq topic.id }) {
            it[TopicTable.topic] = topic.topic
            it[description] = topic.description
            it[hidden] = topic.hidden
        }
        topic
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        TopicTable.deleteWhere { TopicTable.id eq id } > 0
    }

    private fun ResultRow.toTopic() = Topic(
        id          = this[TopicTable.id],
        topic       = this[TopicTable.topic],
        description = this[TopicTable.description],
        hidden      = this[TopicTable.hidden]
    )

    private fun ResultRow.toSubtopic() = Subtopic(
        id        = this[SubtopicTable.id],
        topicId   = this[SubtopicTable.topicId],
        name      = this[SubtopicTable.name],
        createdAt = this[SubtopicTable.createdAt]
    )
}
