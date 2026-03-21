package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object TradeHistoryTable : Table("trade_history") {
    val id = uuid("id")
    val marketId = uuid("market_id").references(MarketTable.id, onDelete = ReferenceOption.RESTRICT).index()
    val buyOrderId = uuid("buy_order_id").index()
    val sellOrderId = uuid("sell_order_id").index()
    val price = integer("price")
    val quantity = integer("quantity")
    val executedAt = timestamp("executed_at").index()

    override val primaryKey = PrimaryKey(id)
}