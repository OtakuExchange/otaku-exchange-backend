package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class CommentUser(val id: Uuid, val username: String)

@Serializable
data class CommentWithLikes(
    val id: Uuid,
    val eventId: Uuid,
    val user: CommentUser,
    val parentId: Uuid? = null,
    val content: String,
    val createdAt: Instant,
    val likes: Long,
    val likedByUser: Boolean
)
