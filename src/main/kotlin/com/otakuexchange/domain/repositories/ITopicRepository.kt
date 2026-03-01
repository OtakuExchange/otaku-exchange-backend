package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Topic

interface ITopicRepository {
    suspend fun getTopics(): List<Topic>
}