package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.market.Position
import com.otakuexchange.domain.repositories.IPositionRepository
import com.otakuexchange.infra.tables.PositionTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class NeonPositionRepository : IPositionRepository {

    override suspend fun getPosition(userId: Uuid, marketId: Uuid, side: OrderSide): Position? = transaction {
        PositionTable.selectAll()
            .where {
                (PositionTable.userId eq userId) and
                (PositionTable.marketId eq marketId) and
                (PositionTable.side eq side.name)
            }
            .singleOrNull()
            ?.toPosition()
    }

    override suspend fun getPositionsByUser(userId: Uuid): List<Position> = transaction {
        PositionTable.selectAll()
            .where { PositionTable.userId eq userId }
            .map { it.toPosition() }
    }

    override suspend fun getPositionsByMarket(marketId: Uuid): List<Position> = transaction {
        PositionTable.selectAll()
            .where { PositionTable.marketId eq marketId }
            .map { it.toPosition() }
    }

    override suspend fun upsertPosition(
        userId: Uuid,
        marketId: Uuid,
        side: OrderSide,
        quantity: Int,
        price: Int,
        lockedAmount: Long
    ): Position = transaction {
        val now = Clock.System.now()
        val existing = PositionTable.selectAll()
            .where {
                (PositionTable.userId eq userId) and
                (PositionTable.marketId eq marketId) and
                (PositionTable.side eq side.name)
            }
            .singleOrNull()

        if (existing == null) {
            // New position
            val id = Uuid.random()
            PositionTable.insert {
                it[PositionTable.id] = id
                it[PositionTable.userId] = userId
                it[PositionTable.marketId] = marketId
                it[PositionTable.side] = side.name
                it[PositionTable.quantity] = quantity
                it[PositionTable.avgPrice] = price
                it[PositionTable.lockedAmount] = lockedAmount
                it[PositionTable.createdAt] = now
                it[PositionTable.updatedAt] = now
            }
            Position(
                id = id,
                userId = userId,
                marketId = marketId,
                side = side,
                quantity = quantity,
                avgPrice = price,
                lockedAmount = lockedAmount,
                createdAt = now,
                updatedAt = now
            )
        } else {
            // Update existing — recalculate weighted average price
            val existingQty = existing[PositionTable.quantity]
            val existingAvg = existing[PositionTable.avgPrice]
            val existingLocked = existing[PositionTable.lockedAmount]
            val newQty = existingQty + quantity
            val newAvg = ((existingAvg * existingQty) + (price * quantity)) / newQty
            val newLocked = existingLocked + lockedAmount

            PositionTable.update({
                (PositionTable.userId eq userId) and
                (PositionTable.marketId eq marketId) and
                (PositionTable.side eq side.name)
            }) {
                it[PositionTable.quantity] = newQty
                it[PositionTable.avgPrice] = newAvg
                it[PositionTable.lockedAmount] = newLocked
                it[PositionTable.updatedAt] = now
            }
            existing.toPosition().copy(
                quantity = newQty,
                avgPrice = newAvg,
                lockedAmount = newLocked,
                updatedAt = now
            )
        }
    }

    override suspend fun deletePosition(userId: Uuid, marketId: Uuid, side: OrderSide): Unit = transaction {
        PositionTable.deleteWhere {
            (PositionTable.userId eq userId) and
            (PositionTable.marketId eq marketId) and
            (PositionTable.side eq side.name)
        }
    }

    private fun ResultRow.toPosition() = Position(
        id = this[PositionTable.id],
        userId = this[PositionTable.userId],
        marketId = this[PositionTable.marketId],
        side = OrderSide.valueOf(this[PositionTable.side]),
        quantity = this[PositionTable.quantity],
        avgPrice = this[PositionTable.avgPrice],
        lockedAmount = this[PositionTable.lockedAmount],
        createdAt = this[PositionTable.createdAt],
        updatedAt = this[PositionTable.updatedAt]
    )
}