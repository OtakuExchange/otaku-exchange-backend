package com.otakuexchange.domain

import kotlinx.serialization.Serializable

@Serializable
data class StreakStatus(
    val streak: Int,
    val rewardCents: Long,
    val canClaim: Boolean,
    val comebackBonusCents: Long = 0L
)