package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.ITopicRepository
import com.otakuexchange.domain.market.Topic
import com.otakuexchange.domain.market.TopicWithSubtopics
import com.otakuexchange.domain.market.Subtopic
import com.otakuexchange.infra.tables.TopicTable
import com.otakuexchange.infra.tables.SubtopicTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonTopicRepository : ITopicRepository {

    override suspend fun getTopics(): List<TopicWithSubtopics> = transaction {
        val topics = TopicTable.selectAll().map { it.toTopic() }
        val topicIds = topics.map { it.id }
        val subtopicsByTopic = if (topicIds.isEmpty()) emptyMap() else {
            SubtopicTable.selectAll()
                .where { SubtopicTable.topicId inList topicIds }
                .map { it.toSubtopic() }
                .groupBy { it.topicId }
        }
        topics.map { topic ->
            TopicWithSubtopics(
                id          = topic.id,
                topic       = topic.topic,
                description = topic.description,
                subtopics   = subtopicsByTopic[topic.id] ?: emptyList()
            )
        }
    }

    override suspend fun getById(id: Uuid): TopicWithSubtopics? = transaction {
        val topic = TopicTable.selectAll()
            .where { TopicTable.id eq id }
            .singleOrNull()
            ?.toTopic() ?: return@transaction null

        val subtopics = SubtopicTable.selectAll()
            .where { SubtopicTable.topicId eq id }
            .map { it.toSubtopic() }

        TopicWithSubtopics(
            id          = topic.id,
            topic       = topic.topic,
            description = topic.description,
            subtopics   = subtopics
        )
    }

    override suspend fun save(topic: Topic): Topic = transaction {
        TopicTable.insert {
            it[id] = topic.id
            it[TopicTable.topic] = topic.topic
            it[description] = topic.description
        }
        topic
    }

    override suspend fun update(topic: Topic): Topic = transaction {
        TopicTable.update({ TopicTable.id eq topic.id }) {
            it[TopicTable.topic] = topic.topic
            it[description] = topic.description
        }
        topic
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        TopicTable.deleteWhere { TopicTable.id eq id } > 0
    }

    private fun ResultRow.toTopic() = Topic(
        id          = this[TopicTable.id],
        topic       = this[TopicTable.topic],
        description = this[TopicTable.description]
    )

    private fun ResultRow.toSubtopic() = Subtopic(
        id        = this[SubtopicTable.id],
        topicId   = this[SubtopicTable.topicId],
        name      = this[SubtopicTable.name],
        createdAt = this[SubtopicTable.createdAt]
    )
}
