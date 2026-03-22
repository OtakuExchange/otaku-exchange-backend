package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.market.OrderRecord
import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.market.OrderStatus
import com.otakuexchange.domain.market.OrderType
import com.otakuexchange.domain.repositories.IOrderRecordRepository
import com.otakuexchange.infra.tables.OrderRecordTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonOrderRecordRepository : IOrderRecordRepository {

    override suspend fun save(record: OrderRecord): OrderRecord = transaction {
        OrderRecordTable.insert {
            it[id] = record.id
            it[userId] = record.userId
            it[marketId] = record.marketId
            it[marketLabel] = record.marketLabel
            it[eventId] = record.eventId
            it[eventName] = record.eventName
            it[topicId] = record.topicId
            it[topicName] = record.topicName
            it[side] = record.side.name
            it[price] = record.price
            it[quantity] = record.quantity
            it[remaining] = record.remaining
            it[lockedAmount] = record.lockedAmount
            it[status] = record.status.name
            it[orderType] = record.orderType.name
            it[createdAt] = record.createdAt
            it[updatedAt] = record.updatedAt
        }
        record
    }

    override suspend fun update(record: OrderRecord): OrderRecord = transaction {
        OrderRecordTable.update({ OrderRecordTable.id eq record.id }) {
            it[remaining] = record.remaining
            it[status] = record.status.name
            it[updatedAt] = record.updatedAt
        }
        record
    }

    override suspend fun findById(id: Uuid): OrderRecord? = transaction {
        OrderRecordTable.selectAll()
            .where { OrderRecordTable.id eq id }
            .singleOrNull()
            ?.toOrderRecord()
    }

    override suspend fun findByUserId(userId: Uuid): List<OrderRecord> = transaction {
        OrderRecordTable.selectAll()
            .where { OrderRecordTable.userId eq userId }
            .map { it.toOrderRecord() }
    }

    override suspend fun findByMarketId(marketId: Uuid): List<OrderRecord> = transaction {
        OrderRecordTable.selectAll()
            .where { OrderRecordTable.marketId eq marketId }
            .map { it.toOrderRecord() }
    }

    private fun ResultRow.toOrderRecord() = OrderRecord(
        id = this[OrderRecordTable.id],
        userId = this[OrderRecordTable.userId],
        marketId = this[OrderRecordTable.marketId],
        marketLabel = this[OrderRecordTable.marketLabel],
        eventId = this[OrderRecordTable.eventId],
        eventName = this[OrderRecordTable.eventName],
        topicId = this[OrderRecordTable.topicId],
        topicName = this[OrderRecordTable.topicName],
        side = OrderSide.valueOf(this[OrderRecordTable.side]),
        price = this[OrderRecordTable.price],
        quantity = this[OrderRecordTable.quantity],
        remaining = this[OrderRecordTable.remaining],
        lockedAmount = this[OrderRecordTable.lockedAmount],
        status = OrderStatus.valueOf(this[OrderRecordTable.status]),
        orderType = OrderType.valueOf(this[OrderRecordTable.orderType]),
        createdAt = this[OrderRecordTable.createdAt],
        updatedAt = this[OrderRecordTable.updatedAt]
    )
}