package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.ITopicRepository
import com.otakuexchange.domain.market.Topic
import com.otakuexchange.infra.tables.TopicTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonTopicRepository : ITopicRepository {

    override suspend fun getTopics(): List<Topic> = transaction {
        TopicTable.selectAll().map { it.toTopic() }
    }

    override suspend fun getById(id: Uuid): Topic? = transaction {
        TopicTable.selectAll()
            .where { TopicTable.id eq id }
            .singleOrNull()
            ?.toTopic()
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

    // ── Row mapper ────────────────────────────────────────────────────────────

    private fun ResultRow.toTopic() = Topic(
        id = this[TopicTable.id],
        topic = this[TopicTable.topic],
        description = this[TopicTable.description]
    )
}