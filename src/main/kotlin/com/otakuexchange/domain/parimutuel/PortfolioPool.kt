package com.otakuexchange.domain.parimutuel

import com.otakuexchange.domain.market.Entity
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class PortfolioPool(
    val id: Uuid,
    val eventId: Uuid,
    val label: String,
    val entity: Entity? = null,
    val isWinner: Boolean,
    val amount: Int,
    val volume: Long,
    val userStake: Int?,  // null if user has no stake in this pool
    val eventStatus: String
)
