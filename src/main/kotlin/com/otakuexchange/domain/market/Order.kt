package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class OrderSide { BUY, SELL }
enum class OrderType { LIMIT, MARKET }

@Serializable
data class Order(
    val id: Uuid = Uuid.random(),
    val userId: Uuid,
    val marketId: Uuid,
    val side: OrderSide,
    val price: Int,       // in cents, 1–99
    val quantity: Int,    // number of contracts
    val remaining: Int = quantity,
    val orderType: OrderType,
    val createdAt: Instant = Clock.System.now()
)