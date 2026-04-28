package com.otakuexchange.domain.parimutuel

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Stake(
    val id: Uuid = Uuid.random(),
    val userId: Uuid,
    val marketPoolId: Uuid,
    val amount: Long = 0L,   // total cents this user has staked into this pool
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now()
)