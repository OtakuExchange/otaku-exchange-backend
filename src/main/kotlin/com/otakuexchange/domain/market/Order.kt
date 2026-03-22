package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class OrderSide { YES, NO }
enum class OrderType { LIMIT, MARKET, NOTIONAL }

@Serializable
data class Order(
    val id: Uuid = Uuid.random(),
    val userId: Uuid,
    val marketId: Uuid,
    val side: OrderSide,
    val price: Int,               // LIMIT: limit price. MARKET: ignored. NOTIONAL: max price cap (1-99, use 99 for no cap)
    val quantity: Int,            // LIMIT/MARKET: number of contracts. NOTIONAL: 0 (determined at fill time)
    val remaining: Int = quantity,
    val lockedAmount: Long,       // cents locked for this order
    val notionalAmount: Long? = null, // NOTIONAL only: budget in cents (e.g. 10000 = $100)
    val orderType: OrderType,
    val createdAt: Instant = Clock.System.now()
)