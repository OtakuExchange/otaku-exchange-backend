package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class OrderStatus { OPEN, PARTIALLY_FILLED, CANCELLED, FULFILLED }

@Serializable
data class OrderRecord(
    val id: Uuid = Uuid.random(),
    val userId: Uuid,
    val marketId: Uuid,
    val marketLabel: String,
    val eventId: Uuid,
    val eventName: String,
    val topicId: Uuid,
    val topicName: String,
    val side: OrderSide,
    val price: Int,
    val quantity: Int,
    val remaining: Int,
    val lockedAmount: Long,
    val notionalAmount: Long? = null,
    val status: OrderStatus = OrderStatus.OPEN,
    val orderType: OrderType,
    val createdAt: Instant,
    val updatedAt: Instant
)