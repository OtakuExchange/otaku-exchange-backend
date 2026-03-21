package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Topic
import com.otakuexchange.domain.market.TopicWithSubtopics
import kotlin.uuid.Uuid

interface ITopicRepository {
    suspend fun getTopics(): List<TopicWithSubtopics>
    suspend fun getById(id: Uuid): TopicWithSubtopics?
    suspend fun save(topic: Topic): Topic
    suspend fun update(topic: Topic): Topic
    suspend fun delete(id: Uuid): Boolean
}
