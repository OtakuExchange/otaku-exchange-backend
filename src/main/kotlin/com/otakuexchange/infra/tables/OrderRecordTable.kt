package com.otakuexchange.infra.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.timestamp

object OrderRecordTable : Table("order_records") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).index()
    val marketId = uuid("market_id").references(MarketTable.id, onDelete = ReferenceOption.CASCADE).index()
    val marketLabel = text("market_label")
    val eventId = uuid("event_id").references(EventTable.id, onDelete = ReferenceOption.CASCADE).index()
    val eventName = text("event_name")
    val topicId = uuid("topic_id").references(TopicTable.id, onDelete = ReferenceOption.CASCADE).index()
    val topicName = text("topic_name")
    val side = text("side")
    val price = integer("price")
    val quantity = integer("quantity")
    val remaining = integer("remaining")
    val lockedAmount = long("locked_amount")
    val notionalAmount = long("notional_amount").nullable()
    val status = text("status")
    val orderType = text("order_type")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}