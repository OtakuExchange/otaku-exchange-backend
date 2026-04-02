package com.otakuexchange.infra.repositories.parimutuel

import com.otakuexchange.domain.market.Entity
import com.otakuexchange.domain.parimutuel.PortfolioPool
import com.otakuexchange.domain.parimutuel.Stake
import com.otakuexchange.domain.parimutuel.StakeWithPool
import com.otakuexchange.domain.parimutuel.StakeWithUser
import com.otakuexchange.domain.repositories.parimutuel.IStakeRepository
import com.otakuexchange.infra.tables.EntityTable
import com.otakuexchange.infra.tables.EventTable
import com.otakuexchange.infra.tables.UserTable
import com.otakuexchange.infra.tables.parimutuel.MarketPoolTable
import com.otakuexchange.infra.tables.parimutuel.StakeTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class NeonStakeRepository : IStakeRepository {

    override suspend fun getByUserId(userId: Uuid): List<StakeWithPool> = transaction {
        StakeTable
            .join(MarketPoolTable, JoinType.INNER, StakeTable.marketPoolId, MarketPoolTable.id)
            .join(EntityTable, JoinType.LEFT, MarketPoolTable.entityId, EntityTable.id)
            .selectAll()
            .where { StakeTable.userId eq userId }
            .map { it.toStakeWithPool() }
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

    override suspend fun getByEventId(eventId: Uuid, limitPerPool: Int, includeAdmins: Boolean): List<StakeWithUser> = transaction {
        StakeTable
            .join(MarketPoolTable, JoinType.INNER, StakeTable.marketPoolId, MarketPoolTable.id)
            .join(UserTable, JoinType.INNER, StakeTable.userId, UserTable.id)
            .selectAll()
            .where {
                val baseCondition = MarketPoolTable.eventId eq eventId
                if (!includeAdmins) baseCondition and (UserTable.isAdmin eq false)
                else baseCondition
            }
            .orderBy(StakeTable.amount, SortOrder.DESC)
            .map {
                StakeWithUser(
                    id           = it[StakeTable.id],
                    userId       = it[StakeTable.userId],
                    username     = it[UserTable.username],
                    avatarUrl    = it[UserTable.avatarUrl],
                    marketPoolId = it[StakeTable.marketPoolId],
                    poolLabel    = it[MarketPoolTable.label],
                    amount       = it[StakeTable.amount],
                    createdAt    = it[StakeTable.createdAt],
                    updatedAt    = it[StakeTable.updatedAt]
                )
            }
            .groupBy { it.marketPoolId }
            .flatMap { (_, stakes) -> stakes.take(limitPerPool) }
    }

    override suspend fun getPortfolioForUser(userId: Uuid): List<PortfolioPool> = transaction {
        // Step 1: find all event IDs the user has any stake in
        val stakedEventIds = StakeTable
            .join(MarketPoolTable, JoinType.INNER, StakeTable.marketPoolId, MarketPoolTable.id)
            .selectAll()
            .where { StakeTable.userId eq userId }
            .map { it[MarketPoolTable.eventId] }
            .distinct()

        if (stakedEventIds.isEmpty()) return@transaction emptyList()

        // Step 2: volume per pool
        val volumeByPool = StakeTable
            .select(StakeTable.marketPoolId, StakeTable.amount.sum())
            .groupBy(StakeTable.marketPoolId)
            .associate { it[StakeTable.marketPoolId] to (it[StakeTable.amount.sum()]?.toLong() ?: 0L) }

        // Step 3: user's stake per pool
        val userStakeByPool = StakeTable
            .selectAll()
            .where { (StakeTable.userId eq userId) and (StakeTable.marketPoolId inList
                MarketPoolTable.selectAll()
                    .where { MarketPoolTable.eventId inList stakedEventIds }
                    .map { it[MarketPoolTable.id] }) }
            .associate { it[StakeTable.marketPoolId] to it[StakeTable.amount] }

        // Step 4: all pools for those events with entity and event status
        MarketPoolTable
            .join(EntityTable, JoinType.LEFT, MarketPoolTable.entityId, EntityTable.id)
            .join(EventTable, JoinType.INNER, MarketPoolTable.eventId, EventTable.id)
            .selectAll()
            .where { MarketPoolTable.eventId inList stakedEventIds }
            .map { row ->
                val entityId = row.getOrNull(EntityTable.id)
                val entity = if (entityId != null) Entity(
                    id              = entityId,
                    name            = row[EntityTable.name],
                    abbreviatedName = row.getOrNull(EntityTable.abbreviatedName),
                    logoPath        = row[EntityTable.logoPath],
                    color           = row.getOrNull(EntityTable.color),
                    pandaScoreId    = row.getOrNull(EntityTable.pandaScoreId),
                    createdAt       = row[EntityTable.createdAt]
                ) else null
                val poolId = row[MarketPoolTable.id]
                PortfolioPool(
                    id          = poolId,
                    eventId     = row[MarketPoolTable.eventId],
                    label       = row[MarketPoolTable.label],
                    entity      = entity,
                    isWinner    = row[MarketPoolTable.isWinner],
                    amount      = row[MarketPoolTable.amount],
                    volume      = volumeByPool[poolId] ?: 0L,
                    userStake   = userStakeByPool[poolId],
                    eventStatus = row[EventTable.status],
                    createdAt   = row[MarketPoolTable.createdAt],
                )
            }
    }

    private fun ResultRow.toStake() = Stake(
        id           = this[StakeTable.id],
        userId       = this[StakeTable.userId],
        marketPoolId = this[StakeTable.marketPoolId],
        amount       = this[StakeTable.amount],
        createdAt    = this[StakeTable.createdAt],
        updatedAt    = this[StakeTable.updatedAt]
    )

    private fun ResultRow.toStakeWithPool(): StakeWithPool {
        val entityId = this.getOrNull(EntityTable.id)
        val entity = if (entityId != null) Entity(
            id              = entityId,
            name            = this[EntityTable.name],
            abbreviatedName = this.getOrNull(EntityTable.abbreviatedName),
            logoPath        = this[EntityTable.logoPath],
            color           = this.getOrNull(EntityTable.color),
            pandaScoreId    = this.getOrNull(EntityTable.pandaScoreId),
            createdAt       = this[EntityTable.createdAt]
        ) else null

        return StakeWithPool(
            id           = this[StakeTable.id],
            userId       = this[StakeTable.userId],
            marketPoolId = this[StakeTable.marketPoolId],
            label        = this[MarketPoolTable.label],
            entity       = entity,
            amount       = this[StakeTable.amount],
            createdAt    = this[StakeTable.createdAt],
            updatedAt    = this[StakeTable.updatedAt]
        )
    }
}