package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.ITopicRepository
import com.otakuexchange.domain.market.Topic
import com.otakuexchange.infra.tables.TopicsTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class NeonTopicRepository : ITopicRepository {
    override suspend fun getTopics(): List<Topic> = transaction {
        TopicsTable.selectAll().map {
            Topic(
                id = it[TopicsTable.id],
                topic = it[TopicsTable.topic],
                description = it[TopicsTable.description]
            )
        }
    }
}