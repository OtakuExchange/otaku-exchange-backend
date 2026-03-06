package com.otakuexchange.domain.event

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Event(
    val id: Uuid = Uuid.random(),
    val topicId: Uuid,
    val format: String,
    val name: String,
    val description: String,
    val closeTime: Instant,
    val status: String,
    val resolutionRule: String
)