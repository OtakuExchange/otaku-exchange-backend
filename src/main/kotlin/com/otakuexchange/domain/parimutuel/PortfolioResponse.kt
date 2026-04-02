package com.otakuexchange.domain.parimutuel

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class PortfolioResponse(
    val userId: Uuid,
    val username: String,
    val avatarUrl: String? = null,
    val balance: Long,
    val pools: List<PortfolioPool>
)
