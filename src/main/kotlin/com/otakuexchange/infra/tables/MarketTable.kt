package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table

object MarketTable : Table("markets") {
    val marketId = integer("market_id").autoIncrement()
    val eventId = integer("event_id")
    val label = text("label")
    val status = text("status")
}