package com.otakuexchange.domain.parimutuel

import com.otakuexchange.domain.market.Entity
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import kotlin.time.Instant
import kotlin.time.Clock

@Serializable
data class PortfolioPool(
    val id: Uuid,
    val eventId: Uuid,
    val label: String,
    val entity: Entity? = null,
    val isWinner: Boolean,
    val amount: Long,
    val volume: Long,
    val userStake: Long?,  // null if user has no stake in this pool
    val eventStatus: String,
    val createdAt: Instant = Clock.System.now(),
    val eventMultiplier: Int = 1
)
