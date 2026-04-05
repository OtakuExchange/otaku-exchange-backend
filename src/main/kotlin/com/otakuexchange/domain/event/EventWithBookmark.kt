package com.otakuexchange.domain.event

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class EventWithBookmark(
    val id: Uuid,
    val topicId: Uuid,
    val format: String,
    val name: String,
    val description: String,
    val closeTime: Instant,
    val status: String,
    val resolutionRule: String,
    val logoPath: String? = null,
    val pandaScoreId: Long? = null,
    val createdAt: Instant = Clock.System.now(),
    val tradeVolume: Long = 0L,
    val bookmarked: Boolean,
    val multiplier: Int = 1,
    val isNew: Boolean = false
)
