package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class MarketWithEntity(
    val id: Uuid,
    val eventId: Uuid,
    val entity: Entity? = null,
    val label: String,
    val status: String
)
