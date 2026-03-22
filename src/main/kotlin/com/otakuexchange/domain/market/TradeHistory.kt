package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class TradeHistory(
    val id: Uuid = Uuid.random(),
    val marketId: Uuid,
    val yesOrderId: Uuid,     // the YES side order
    val noOrderId: Uuid,      // the NO side order
    val yesPrice: Int,        // YES execution price in cents
    val noPrice: Int,         // NO execution price = 100 - yesPrice
    val quantity: Int,
    val escrowPerContract: Int = 100, // always 100¢ per contract pair
    val executedAt: Instant
)