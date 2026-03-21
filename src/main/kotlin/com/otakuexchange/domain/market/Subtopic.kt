package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Subtopic(
    val id: Uuid = Uuid.random(),
    val topicId: Uuid,
    val name: String,
    val createdAt: Instant = Clock.System.now()
)
