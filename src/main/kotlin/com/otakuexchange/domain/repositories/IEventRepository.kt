package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.event.Event

interface IEventRepository {
    suspend fun getEventsByTopicId(topicId: Int): List<Event>
}