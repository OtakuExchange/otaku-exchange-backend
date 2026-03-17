package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.IBookmarkRepository
import com.otakuexchange.infra.tables.BookmarkTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class NeonBookmarkRepository : IBookmarkRepository {

    override suspend fun addBookmark(userId: Uuid, eventId: Uuid): Boolean = transaction {
        val exists = BookmarkTable.selectAll()
            .where { (BookmarkTable.userId eq userId) and (BookmarkTable.eventId eq eventId) }
            .singleOrNull() != null

        if (exists) return@transaction false

        BookmarkTable.insert {
            it[id]                      = Uuid.random()
            it[BookmarkTable.userId]    = userId
            it[BookmarkTable.eventId]   = eventId
            it[createdAt]               = Clock.System.now()
        }
        true
    }

    override suspend fun removeBookmark(userId: Uuid, eventId: Uuid): Boolean = transaction {
        BookmarkTable.deleteWhere {
            (BookmarkTable.userId eq userId) and (BookmarkTable.eventId eq eventId)
        } > 0
    }
}
