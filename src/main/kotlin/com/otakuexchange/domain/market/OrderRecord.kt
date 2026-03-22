package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class OrderStatus { OPEN, PARTIALLY_FILLED, CANCELLED, FULFILLED }

@Serializable
data class OrderRecord(
    val id: Uuid = Uuid.random(),
    val userId: Uuid,
    // Market context — denormalized for fast reads
    val marketId: Uuid,
    val marketLabel: String,
    val eventId: Uuid,
    val eventName: String,
    val topicId: Uuid,
    val topicName: String,
    // Order details
    val side: OrderSide,      // YES or NO
    val price: Int,           // YES price in cents 1-99
    val quantity: Int,
    val remaining: Int,
    val lockedAmount: Long,   // total cents locked for this order
    val status: OrderStatus = OrderStatus.OPEN,
    val orderType: OrderType,
    val createdAt: Instant,
    val updatedAt: Instant
)