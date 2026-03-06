package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.event.Event
import kotlin.uuid.Uuid

interface IEventRepository {
    suspend fun getEventsByTopicId(topicId: Uuid): List<Event>
    suspend fun getById(id: Uuid): Event?
    suspend fun save(event: Event): Event
    suspend fun update(event: Event): Event
    suspend fun delete(id: Uuid): Boolean
}