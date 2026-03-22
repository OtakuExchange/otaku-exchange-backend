package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Position(
    val id: Uuid = Uuid.random(),
    val userId: Uuid,
    val marketId: Uuid,
    val side: OrderSide,      // YES or NO
    val quantity: Int,        // number of contracts held
    val avgPrice: Int,        // average entry price in cents
    val lockedAmount: Long,   // total cents locked in escrow for this position
    val createdAt: Instant,
    val updatedAt: Instant
)