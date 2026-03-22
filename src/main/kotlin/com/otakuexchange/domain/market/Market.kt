package com.otakuexchange.domain.market

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

enum class MarketStatus {
    OPEN,           // accepting orders
    CLOSED,         // no new orders, awaiting resolution
    RESOLVED_YES,   // resolved YES — YES holders paid out
    RESOLVED_NO     // resolved NO — NO holders paid out
}

@Serializable
data class Market(
    val id: Uuid = Uuid.random(),
    val eventId: Uuid,
    val entityId: Uuid? = null,
    val relatedEntityId: Uuid? = null,
    val label: String,
    val isMatch: Boolean = false,
    val status: MarketStatus = MarketStatus.OPEN
)