package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.Comment
import com.otakuexchange.domain.market.CommentWithLikes
import kotlin.uuid.Uuid

interface ICommentRepository {
    suspend fun save(comment: Comment): Comment
    suspend fun getByEventId(eventId: Uuid, currentUserId: Uuid?): List<CommentWithLikes>
    suspend fun likeComment(commentId: Uuid, userId: Uuid): Boolean
    suspend fun unlikeComment(commentId: Uuid, userId: Uuid): Boolean
}
