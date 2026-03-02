package com.otakuexchange.domain.event

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Event(
    val eventId: Int,
    val topicId: Int,
    val format: String,
    val name: String,
    val description: String,
    val closeTime: Instant,
    val status: String,
    val resolutionRule: String
)