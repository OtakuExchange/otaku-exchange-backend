package com.otakuexchange.infra.tables.parimutuel

import com.otakuexchange.infra.tables.EntityTable
import com.otakuexchange.infra.tables.EventTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object MarketPoolTable : Table("market_pools") {
    val id        = uuid("id")
    val eventId   = uuid("event_id").references(EventTable.id, onDelete = ReferenceOption.CASCADE).index()
    val entityId  = uuid("entity_id").references(EntityTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val label     = text("label")
    val isWinner  = bool("is_winner").default(false)
    val amount    = integer("amount").default(0)   // total cents staked in this pool
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}