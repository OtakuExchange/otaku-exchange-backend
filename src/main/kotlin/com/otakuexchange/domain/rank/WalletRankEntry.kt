package com.otakuexchange.domain.rank

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class WalletRankEntry(
    val rank: Int,
    val userId: Uuid,
    val username: String,
    val balance: Long,
    val avatarUrl: String? = null
)