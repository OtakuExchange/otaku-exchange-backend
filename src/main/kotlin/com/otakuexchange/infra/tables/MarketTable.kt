package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption

object MarketTable : Table("markets") {
    val id = uuid("id")
    val eventId = uuid("event_id").references(EventTable.id, onDelete = ReferenceOption.RESTRICT).index()
    val label = text("label")
    val status = text("status")

    override val primaryKey = PrimaryKey(id)
}