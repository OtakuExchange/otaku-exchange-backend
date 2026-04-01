package com.otakuexchange.domain.parimutuel

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class StakeWithUser(
    val id: Uuid,
    val userId: Uuid,
    val username: String,
    val avatarUrl: String? = null,
    val marketPoolId: Uuid,
    val poolLabel: String,
    val amount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)
