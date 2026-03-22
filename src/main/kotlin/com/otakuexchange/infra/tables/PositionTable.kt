package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object PositionTable : Table("positions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.RESTRICT).index()
    val marketId = uuid("market_id").references(MarketTable.id, onDelete = ReferenceOption.RESTRICT).index()
    val side = text("side")           // YES or NO
    val quantity = integer("quantity")
    val avgPrice = integer("avg_price")
    val lockedAmount = long("locked_amount")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        // One position per user per market per side
        uniqueIndex(userId, marketId, side)
    }
}