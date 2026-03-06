package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Market(
    val id: Uuid = Uuid.random(),
    val eventId: Uuid,
    val label: String,
    val status: String
)