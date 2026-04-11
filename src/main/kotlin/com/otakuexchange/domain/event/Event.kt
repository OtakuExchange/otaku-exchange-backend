package com.otakuexchange.domain.event

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class EventStatus {
    open, resolved, staking_closed, hidden, closed
}

@Serializable
data class Event(
    val id: Uuid = Uuid.random(),
    val topicId: Uuid,
    val format: String,
    val name: String,
    val description: String,
    val closeTime: Instant,
    val status: EventStatus,
    val resolutionRule: String,
    val logoPath: String? = null,
    val pandaScoreId: Long? = null,
    val createdAt: Instant = Clock.System.now(),
    val multiplier: Int = 1,
    val alias: String? = null
)