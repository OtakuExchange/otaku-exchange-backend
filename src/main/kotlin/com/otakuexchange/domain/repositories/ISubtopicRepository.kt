package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.event.EventWithBookmark
import com.otakuexchange.domain.market.Subtopic
import kotlin.uuid.Uuid

interface ISubtopicRepository {
    suspend fun getByTopicId(topicId: Uuid): List<Subtopic>
    suspend fun getById(id: Uuid): Subtopic?
    suspend fun save(subtopic: Subtopic): Subtopic
    suspend fun delete(id: Uuid): Boolean
    suspend fun getEventsBySubtopicId(subtopicId: Uuid, currentUserId: Uuid?): List<EventWithBookmark>
    suspend fun addEventToSubtopic(eventId: Uuid, subtopicId: Uuid)
    suspend fun removeEventFromSubtopic(eventId: Uuid, subtopicId: Uuid)
}
