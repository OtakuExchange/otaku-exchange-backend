package com.otakuexchange.domain.repositories.parimutuel

import com.otakuexchange.domain.parimutuel.PortfolioPool
import com.otakuexchange.domain.parimutuel.Stake
import com.otakuexchange.domain.parimutuel.StakeWithPool
import com.otakuexchange.domain.parimutuel.StakeWithUser
import kotlin.uuid.Uuid

interface IStakeRepository {
    /** All stakes a user has placed, across all events, enriched with pool label and entity. */
    suspend fun getByUserId(userId: Uuid): List<StakeWithPool>
    /** All pools for every event the user has staked in, with the user's stake amount per pool. */
    suspend fun getPortfolioForUser(userId: Uuid): List<PortfolioPool>
    /** Top [limitPerPool] stakes per pool for an event, enriched with user info, ordered by amount desc. */
    suspend fun getByEventId(eventId: Uuid, limitPerPool: Int = 10, includeAdmins: Boolean = true): List<StakeWithUser>
    /** All stakes placed into a specific pool (used for payout at resolution). */
    suspend fun getByMarketPoolId(marketPoolId: Uuid): List<Stake>
    /** Find a specific user's stake in a specific pool, if it exists. */
    suspend fun findByUserAndPool(userId: Uuid, marketPoolId: Uuid): Stake?
    /**
     * Place or increase a stake. If the user already has a stake in this pool,
     * adds [amount] to it. Otherwise creates a new stake row.
     * Also increments the pool's total amount atomically.
     */
    suspend fun addToStake(marketPoolId: Uuid, userId: Uuid, amount: Long): Stake
    suspend fun delete(id: Uuid): Boolean
}