package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class OrderSide { YES, NO }
enum class OrderType { LIMIT, MARKET }

@Serializable
data class Order(
    val id: Uuid = Uuid.random(),
    val userId: Uuid = Uuid.random(),
    val marketId: Uuid,
    val side: OrderSide,
    val price: Int,           // YES price in cents, 1–99. NO price = 100 - price
    val quantity: Int,
    val remaining: Int = quantity,
    val lockedAmount: Long,   // cents locked for this order
    val orderType: OrderType,
    val createdAt: Instant = Clock.System.now()
)