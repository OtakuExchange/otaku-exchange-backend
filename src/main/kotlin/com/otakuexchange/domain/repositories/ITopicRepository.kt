package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Topic
import com.otakuexchange.domain.market.TopicWithSubtopics
import kotlin.uuid.Uuid

interface ITopicRepository {
    suspend fun getTopics(currentUserId: Uuid? = null): List<TopicWithSubtopics>
    suspend fun getById(id: Uuid, currentUserId: Uuid? = null): TopicWithSubtopics?
    suspend fun save(topic: Topic): Topic
    suspend fun update(topic: Topic): Topic
    suspend fun delete(id: Uuid): Boolean
}
