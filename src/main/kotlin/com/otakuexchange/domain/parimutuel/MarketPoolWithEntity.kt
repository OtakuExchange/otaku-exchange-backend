package com.otakuexchange.domain.parimutuel

import com.otakuexchange.domain.market.Entity
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class MarketPoolWithEntity(
    val id: Uuid = Uuid.random(),
    val eventId: Uuid,
    val entity: Entity? = null,
    val label: String,
    val isWinner: Boolean = false,
    val amount: Long = 0L,
    val volume: Long = 0L,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now()
)
