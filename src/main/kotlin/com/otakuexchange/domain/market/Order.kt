package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class OrderSide { BUY, SELL }
enum class OrderStatus { OPEN, CANCELLED, FULFILLED }
enum class OrderType { LIMIT, MARKET }

@Serializable
data class Order(
    val id: Uuid = Uuid.random(),
    val userId: Uuid,
    val marketId: Uuid,
    val side: OrderSide,
    val price: Double,
    val quantity: Double,
    val remaining: Double = quantity,
    val status: OrderStatus = OrderStatus.OPEN,
    val orderType: OrderType,
    val createdAt: Instant = Clock.System.now(),
    val executedAt: Instant? = null
)