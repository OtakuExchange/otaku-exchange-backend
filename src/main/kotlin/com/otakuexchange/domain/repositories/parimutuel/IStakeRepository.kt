package com.otakuexchange.domain.repositories.parimutuel

import com.otakuexchange.domain.parimutuel.Stake
import com.otakuexchange.domain.parimutuel.StakeWithPool
import kotlin.uuid.Uuid

interface IStakeRepository {
    /** All stakes a user has placed, across all events, enriched with pool label and entity. */
    suspend fun getByUserId(userId: Uuid): List<StakeWithPool>
    /** All stakes placed into a specific pool (used for payout at resolution). */
    suspend fun getByMarketPoolId(marketPoolId: Uuid): List<Stake>
    /** Find a specific user's stake in a specific pool, if it exists. */
    suspend fun findByUserAndPool(userId: Uuid, marketPoolId: Uuid): Stake?
    /**
     * Place or increase a stake. If the user already has a stake in this pool,
     * adds [amount] to it. Otherwise creates a new stake row.
     * Also increments the pool's total amount atomically.
     */
    suspend fun addToStake(marketPoolId: Uuid, userId: Uuid, amount: Int): Stake
    suspend fun delete(id: Uuid): Boolean
}