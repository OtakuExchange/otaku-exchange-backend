package com.otakuexchange.domain.parimutuel

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class MarketPool(
    val id: Uuid = Uuid.random(),
    val eventId: Uuid,
    val entityId: Uuid? = null,
    val label: String,
    val isWinner: Boolean = false,
    val amount: Int = 0,   // total cents staked into this pool, default $50
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now()
)