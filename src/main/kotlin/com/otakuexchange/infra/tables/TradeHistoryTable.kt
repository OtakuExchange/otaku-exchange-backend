package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object TradeHistoryTable : Table("trade_history") {
    val id = uuid("id")
    val marketId = uuid("market_id").references(MarketTable.id, onDelete = ReferenceOption.RESTRICT).index()
    val yesOrderId = uuid("yes_order_id").index()
    val noOrderId = uuid("no_order_id").index()
    val yesPrice = integer("yes_price")
    val noPrice = integer("no_price")
    val quantity = integer("quantity")
    val escrowPerContract = integer("escrow_per_contract").default(100)
    val executedAt = timestamp("executed_at").index()

    override val primaryKey = PrimaryKey(id)
}