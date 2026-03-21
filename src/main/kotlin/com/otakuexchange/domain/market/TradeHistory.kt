package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class TradeHistory(
    val id: Uuid = Uuid.random(),
    val marketId: Uuid,
    val buyOrderId: Uuid,
    val sellOrderId: Uuid,
    val price: Int,       // in cents, the execution price
    val quantity: Int,    // contracts filled in this trade
    val executedAt: Instant
)