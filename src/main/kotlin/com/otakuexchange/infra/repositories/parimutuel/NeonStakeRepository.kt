package com.otakuexchange.infra.repositories.parimutuel

import com.otakuexchange.domain.parimutuel.Stake
import com.otakuexchange.domain.repositories.parimutuel.IStakeRepository
import com.otakuexchange.infra.tables.parimutuel.MarketPoolTable
import com.otakuexchange.infra.tables.parimutuel.StakeTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class NeonStakeRepository : IStakeRepository {

    override suspend fun getByUserId(userId: Uuid): List<Stake> = transaction {
        StakeTable.selectAll()
            .where { StakeTable.userId eq userId }
            .map { it.toStake() }
    }

    override suspend fun getByMarketPoolId(marketPoolId: Uuid): List<Stake> = transaction {
        StakeTable.selectAll()
            .where { StakeTable.marketPoolId eq marketPoolId }
            .map { it.toStake() }
    }

    override suspend fun findByUserAndPool(userId: Uuid, marketPoolId: Uuid): Stake? = transaction {
        StakeTable.selectAll()
            .where { (StakeTable.userId eq userId) and (StakeTable.marketPoolId eq marketPoolId) }
            .singleOrNull()
            ?.toStake()
    }

    /**
     * Upserts the stake and increments the pool total in one transaction so
     * pool.amount is always consistent with the sum of its stakes.
     */
    override suspend fun addToStake(marketPoolId: Uuid, userId: Uuid, amount: Int): Stake = transaction {
        val now = Clock.System.now()

        // Increment pool total
        MarketPoolTable.update({ MarketPoolTable.id eq marketPoolId }) {
            it[MarketPoolTable.amount]    = MarketPoolTable.amount + amount
            it[MarketPoolTable.updatedAt] = now
        }

        // Upsert stake
        val existing = StakeTable.selectAll()
            .where { (StakeTable.userId eq userId) and (StakeTable.marketPoolId eq marketPoolId) }
            .singleOrNull()

        if (existing != null) {
            StakeTable.update({
                (StakeTable.userId eq userId) and (StakeTable.marketPoolId eq marketPoolId)
            }) {
                it[StakeTable.amount]    = StakeTable.amount + amount
                it[StakeTable.updatedAt] = now
            }
            StakeTable.selectAll()
                .where { (StakeTable.userId eq userId) and (StakeTable.marketPoolId eq marketPoolId) }
                .single()
                .toStake()
        } else {
            val newStake = Stake(
                userId       = userId,
                marketPoolId = marketPoolId,
                amount       = amount,
                createdAt    = now,
                updatedAt    = now
            )
            StakeTable.insert {
                it[StakeTable.id]           = newStake.id
                it[StakeTable.userId]       = newStake.userId
                it[StakeTable.marketPoolId] = newStake.marketPoolId
                it[StakeTable.amount]       = newStake.amount
                it[StakeTable.createdAt]    = newStake.createdAt
                it[StakeTable.updatedAt]    = newStake.updatedAt
            }
            newStake
        }
    }

    override suspend fun delete(id: Uuid): Boolean = transaction {
        StakeTable.deleteWhere { StakeTable.id eq id } > 0
    }

    private fun ResultRow.toStake() = Stake(
        id           = this[StakeTable.id],
        userId       = this[StakeTable.userId],
        marketPoolId = this[StakeTable.marketPoolId],
        amount       = this[StakeTable.amount],
        createdAt    = this[StakeTable.createdAt],
        updatedAt    = this[StakeTable.updatedAt]
    )
}