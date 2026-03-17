package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Comment(
    val id: Uuid = Uuid.random(),
    val eventId: Uuid,
    val userId: Uuid,
    val parentId: Uuid? = null,
    val content: String,
    val createdAt: Instant = Clock.System.now()
)
