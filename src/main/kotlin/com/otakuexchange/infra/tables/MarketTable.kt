package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption

object MarketTable : Table("markets") {
    val id = uuid("id")
    val eventId = uuid("event_id").references(EventTable.id, onDelete = ReferenceOption.CASCADE).index()
    val entityId = uuid("entity_id").references(EntityTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val label = text("label")
    val status = text("status").default("OPEN")

    override val primaryKey = PrimaryKey(id)
}