package com.otakuexchange.testutil

import com.otakuexchange.infra.tables.DailyStreakTable
import com.otakuexchange.infra.tables.BookmarkTable
import com.otakuexchange.infra.tables.CommentLikeTable
import com.otakuexchange.infra.tables.CommentTable
import com.otakuexchange.infra.tables.EntityTable
import com.otakuexchange.infra.tables.EventTable
import com.otakuexchange.infra.tables.EventSubtopicTable
import com.otakuexchange.infra.tables.MarketTable
import com.otakuexchange.infra.tables.SubtopicTable
import com.otakuexchange.infra.tables.TopicTable
import com.otakuexchange.infra.tables.UserTable
import com.otakuexchange.infra.tables.parimutuel.MarketPoolTable
import com.otakuexchange.infra.tables.parimutuel.StakeTable
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

object Seed {
    val fixedInstant: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)

    fun topic(
        db: Database,
        id: Uuid,
        topic: String = "League of Legends Esports",
        description: String? = "test topic description",
        hidden: Boolean = false
    ): Uuid = transaction(db) {
        TopicTable.insert {
            it[TopicTable.id] = id
            it[TopicTable.topic] = topic
            it[TopicTable.description] = description
            it[TopicTable.hidden] = hidden
        }
        id
    }

    fun event(
        db: Database,
        id: Uuid,
        topicId: Uuid,
        format: String = "single",
        name: String = "Test Event",
        description: String = "Test event description",
        closeTime: Instant = fixedInstant,
        status: String = "open",
        resolutionRule: String = "Test resolution rule",
        createdAt: Instant = fixedInstant
    ): Uuid = transaction(db) {
        EventTable.insert {
            it[EventTable.id] = id
            it[EventTable.topicId] = topicId
            it[EventTable.format] = format
            it[EventTable.name] = name
            it[EventTable.description] = description
            it[EventTable.closeTime] = closeTime
            it[EventTable.status] = status
            it[EventTable.resolutionRule] = resolutionRule
            it[EventTable.logoPath] = null
            it[EventTable.pandaScoreId] = null
            it[EventTable.createdAt] = createdAt
        }
        id
    }

    fun user(
        db: Database,
        id: Uuid,
        username: String,
        email: String,
        authProvider: String = "CLERK",
        providerUserId: String? = null,
        balance: Long = 0L,
        isAdmin: Boolean = false,
        createdAt: Instant = fixedInstant
    ): Uuid = transaction(db) {
        UserTable.insert {
            it[UserTable.id] = id
            it[UserTable.username] = username
            it[UserTable.email] = email
            it[UserTable.authProvider] = authProvider
            it[UserTable.providerUserId] = providerUserId
            it[UserTable.balance] = balance
            it[UserTable.lockedBalance] = 0L
            it[UserTable.isAdmin] = isAdmin
            it[UserTable.avatarUrl] = null
            it[UserTable.createdAt] = createdAt
        }
        id
    }

    fun entity(
        db: Database,
        id: Uuid,
        name: String,
        abbreviatedName: String? = null,
        logoPath: String = "logo.png",
        color: String? = null,
        pandaScoreId: Long? = null,
        createdAt: Instant = fixedInstant
    ): Uuid = transaction(db) {
        EntityTable.insert {
            it[EntityTable.id] = id
            it[EntityTable.name] = name
            it[EntityTable.abbreviatedName] = abbreviatedName
            it[EntityTable.logoPath] = logoPath
            it[EntityTable.color] = color
            it[EntityTable.pandaScoreId] = pandaScoreId
            it[EntityTable.createdAt] = createdAt
        }
        id
    }

    fun subtopic(
        db: Database,
        id: Uuid,
        topicId: Uuid,
        name: String = "Test Subtopic",
        createdAt: Instant = fixedInstant
    ): Uuid = transaction(db) {
        SubtopicTable.insert {
            it[SubtopicTable.id] = id
            it[SubtopicTable.topicId] = topicId
            it[SubtopicTable.name] = name
            it[SubtopicTable.createdAt] = createdAt
        }
        id
    }

    fun addEventToSubtopic(db: Database, eventId: Uuid, subtopicId: Uuid) {
        transaction(db) {
            EventSubtopicTable.insert {
                it[EventSubtopicTable.eventId] = eventId
                it[EventSubtopicTable.subtopicId] = subtopicId
            }
        }
    }

    fun bookmark(
        db: Database,
        id: Uuid,
        userId: Uuid,
        eventId: Uuid,
        createdAt: Instant = fixedInstant
    ): Uuid = transaction(db) {
        BookmarkTable.insert {
            it[BookmarkTable.id] = id
            it[BookmarkTable.userId] = userId
            it[BookmarkTable.eventId] = eventId
            it[BookmarkTable.createdAt] = createdAt
        }
        id
    }

    fun comment(
        db: Database,
        id: Uuid,
        eventId: Uuid,
        userId: Uuid,
        content: String = "Test comment",
        parentId: Uuid? = null,
        createdAt: Instant = fixedInstant
    ): Uuid = transaction(db) {
        CommentTable.insert {
            it[CommentTable.id] = id
            it[CommentTable.eventId] = eventId
            it[CommentTable.userId] = userId
            it[CommentTable.parentId] = parentId
            it[CommentTable.content] = content
            it[CommentTable.createdAt] = createdAt
        }
        id
    }

    fun commentLike(
        db: Database,
        id: Uuid,
        commentId: Uuid,
        userId: Uuid,
        createdAt: Instant = fixedInstant
    ): Uuid = transaction(db) {
        CommentLikeTable.insert {
            it[CommentLikeTable.id] = id
            it[CommentLikeTable.commentId] = commentId
            it[CommentLikeTable.userId] = userId
            it[CommentLikeTable.createdAt] = createdAt
        }
        id
    }

    fun market(
        db: Database,
        id: Uuid,
        eventId: Uuid,
        label: String = "Test Market",
        entityId: Uuid? = null,
        relatedEntityId: Uuid? = null,
        isMatch: Boolean = false,
        status: String = "OPEN",
        createdAt: Instant = fixedInstant
    ): Uuid = transaction(db) {
        MarketTable.insert {
            it[MarketTable.id] = id
            it[MarketTable.eventId] = eventId
            it[MarketTable.entityId] = entityId
            it[MarketTable.relatedEntityId] = relatedEntityId
            it[MarketTable.label] = label
            it[MarketTable.isMatch] = isMatch
            it[MarketTable.createdAt] = createdAt
            it[MarketTable.status] = status
        }
        id
    }

    fun marketPool(
        db: Database,
        id: Uuid,
        eventId: Uuid,
        entityId: Uuid? = null,
        label: String,
        isWinner: Boolean = false,
        amount: Long = 0L,
        createdAt: Instant = fixedInstant,
        updatedAt: Instant = fixedInstant
    ): Uuid = transaction(db) {
        MarketPoolTable.insert {
            it[MarketPoolTable.id] = id
            it[MarketPoolTable.eventId] = eventId
            it[MarketPoolTable.entityId] = entityId
            it[MarketPoolTable.label] = label
            it[MarketPoolTable.isWinner] = isWinner
            it[MarketPoolTable.amount] = amount
            it[MarketPoolTable.createdAt] = createdAt
            it[MarketPoolTable.updatedAt] = updatedAt
        }
        id
    }

    fun stakeRow(
        db: Database,
        id: Uuid,
        userId: Uuid,
        marketPoolId: Uuid,
        amount: Long,
        createdAt: Instant = fixedInstant,
        updatedAt: Instant = fixedInstant
    ): Uuid = transaction(db) {
        StakeTable.insert {
            it[StakeTable.id] = id
            it[StakeTable.userId] = userId
            it[StakeTable.marketPoolId] = marketPoolId
            it[StakeTable.amount] = amount
            it[StakeTable.createdAt] = createdAt
            it[StakeTable.updatedAt] = updatedAt
        }
        id
    }

    fun setPoolAmount(db: Database, marketPoolId: Uuid, amount: Long) {
        transaction(db) {
            MarketPoolTable.update({ MarketPoolTable.id eq marketPoolId }) {
                it[MarketPoolTable.amount] = amount
            }
        }
    }

    fun poolAmount(db: Database, marketPoolId: Uuid): Long = transaction(db) {
        MarketPoolTable.selectAll()
            .where { MarketPoolTable.id eq marketPoolId }
            .single()[MarketPoolTable.amount]
    }

    fun dailyStreak(db: Database, userId: Uuid, streak: Int, lastClaim: LocalDate) {
        transaction(db) {
            DailyStreakTable.insert {
                it[DailyStreakTable.userId] = userId
                it[DailyStreakTable.streak] = streak
                it[DailyStreakTable.lastClaim] = lastClaim
            }
        }
    }
}

