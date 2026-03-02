package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable

@Serializable
data class Market(
    val marketId: Int,
    val eventId: Int,
    val label: String,
    val status: String
)