package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.event.EventWithBookmark
import kotlin.uuid.Uuid

interface IEventRepository {
    suspend fun getEventsByTopicId(topicId: Uuid, currentUserId: Uuid?): List<EventWithBookmark>
    suspend fun getById(id: Uuid, currentUserId: Uuid?): EventWithBookmark?
    suspend fun save(event: Event): Event
    suspend fun update(event: Event): Event
    suspend fun delete(id: Uuid): Boolean
}
