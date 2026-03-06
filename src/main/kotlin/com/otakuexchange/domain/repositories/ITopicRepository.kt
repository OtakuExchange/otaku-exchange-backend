package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Topic
import kotlin.uuid.Uuid

interface ITopicRepository {
    suspend fun getTopics(): List<Topic>
    suspend fun getById(id: Uuid): Topic?
    suspend fun save(topic: Topic): Topic
    suspend fun update(topic: Topic): Topic
    suspend fun delete(id: Uuid): Boolean
}