package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.market.Comment
import com.otakuexchange.domain.market.CommentUser
import com.otakuexchange.domain.market.CommentWithLikes
import com.otakuexchange.domain.repositories.ICommentRepository
import com.otakuexchange.infra.tables.CommentLikeTable
import com.otakuexchange.infra.tables.CommentTable
import com.otakuexchange.infra.tables.UserTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class NeonCommentRepository : ICommentRepository {

    override suspend fun save(comment: Comment): Comment = transaction {
        CommentTable.insert {
            it[id]        = comment.id
            it[eventId]   = comment.eventId
            it[userId]    = comment.userId
            it[parentId]  = comment.parentId
            it[content]   = comment.content
            it[createdAt] = comment.createdAt
        }
        comment
    }

    override suspend fun getByEventId(eventId: Uuid, currentUserId: Uuid?): List<CommentWithLikes> = transaction {
        val rows = (CommentTable innerJoin UserTable)
            .selectAll()
            .where { CommentTable.eventId eq eventId }

        val comments = rows.map { it.toComment() }

        if (comments.isEmpty()) return@transaction emptyList()

        val commentIds = comments.map { it.first.id }

        val likes = CommentLikeTable.selectAll()
            .where { CommentLikeTable.commentId inList commentIds }
            .map { Pair(it[CommentLikeTable.commentId], it[CommentLikeTable.userId]) }

        val likeCountByComment = likes.groupBy { it.first }.mapValues { it.value.size.toLong() }
        val likedByUser = if (currentUserId != null) {
            likes.filter { it.second == currentUserId }.map { it.first }.toSet()
        } else emptySet()

        comments.map { (comment, user) ->
            CommentWithLikes(
                id          = comment.id,
                eventId     = comment.eventId,
                user        = user,
                parentId    = comment.parentId,
                content     = comment.content,
                createdAt   = comment.createdAt,
                likes       = likeCountByComment[comment.id] ?: 0L,
                likedByUser = comment.id in likedByUser
            )
        }
    }

    override suspend fun likeComment(commentId: Uuid, userId: Uuid): Boolean = transaction {
        val alreadyLiked = CommentLikeTable.selectAll()
            .where { (CommentLikeTable.commentId eq commentId) and (CommentLikeTable.userId eq userId) }
            .singleOrNull() != null

        if (alreadyLiked) return@transaction false

        CommentLikeTable.insert {
            it[id]                   = Uuid.random()
            it[CommentLikeTable.commentId] = commentId
            it[CommentLikeTable.userId]    = userId
            it[createdAt]            = Clock.System.now()
        }
        true
    }

    override suspend fun unlikeComment(commentId: Uuid, userId: Uuid): Boolean = transaction {
        val deleted = CommentLikeTable.deleteWhere {
            (CommentLikeTable.commentId eq commentId) and (CommentLikeTable.userId eq userId)
        }
        deleted > 0
    }

    private fun ResultRow.toComment(): Pair<Comment, CommentUser> {
        val comment = Comment(
            id        = this[CommentTable.id],
            eventId   = this[CommentTable.eventId],
            userId    = this[CommentTable.userId],
            parentId  = this[CommentTable.parentId],
            content   = this[CommentTable.content],
            createdAt = this[CommentTable.createdAt]
        )
        val user = CommentUser(
            id       = this[UserTable.id],
            username = this[UserTable.username]
        )
        return Pair(comment, user)
    }
}
