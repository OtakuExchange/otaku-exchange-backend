package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class MarketWithEntity(
    val id: Uuid,
    val eventId: Uuid,
    val entity: Entity? = null,
    val relatedEntity: Entity? = null,
    val label: String,
    val isMatch: Boolean = false,
    val createdAt: Instant = Clock.System.now(),
    val tradeVolume: Long = 0L,
    val status: String
)
