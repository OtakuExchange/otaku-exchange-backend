package com.otakuexchange.domain.parimutuel

import com.otakuexchange.domain.market.Entity
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class StakeWithPool(
    val id: Uuid,
    val userId: Uuid,
    val marketPoolId: Uuid,
    val label: String,
    val entity: Entity? = null,
    val amount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)
