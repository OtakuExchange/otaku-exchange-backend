package com.otakuexchange.domain.repositories

import kotlin.uuid.Uuid

interface IBookmarkRepository {
    suspend fun addBookmark(userId: Uuid, eventId: Uuid): Boolean
    suspend fun removeBookmark(userId: Uuid, eventId: Uuid): Boolean
}
