package com.otakuexchange.domain.services

import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.parimutuel.Stake
import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.repositories.parimutuel.IMarketPoolRepository
import com.otakuexchange.domain.repositories.parimutuel.IStakeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class ParimutuelService(
    private val stakeRepository: IStakeRepository,
    private val marketPoolRepository: IMarketPoolRepository,
    private val eventRepository: IEventRepository,
    private val userRepository: IUserRepository
) {

    companion object {
        /** Initial seed amount placed into each pool on creation, in cents.
         *  Also used as the winner bonus pot distributed proportionally to winners. */
        const val POOL_SEED_AMOUNT = 50000
    }

    // ── Payout Formula ────────────────────────────────────────────────────────

    /**
     * Core parimutuel payout formula.
     *
     * payout = (userStake / poolTotal) * (grandTotal + POOL_SEED_AMOUNT)
     *
     * The POOL_SEED_AMOUNT bonus is distributed proportionally to winners
     * based on their share of the winning pool.
     */
    private fun calculatePayout(userStake: Int, poolTotal: Int, grandTotal: Int): Int {
        val userPoolTotal = poolTotal - POOL_SEED_AMOUNT
        if (userPoolTotal <= 0) return 0
        return (userStake.toDouble() / userPoolTotal.toDouble() * (grandTotal - POOL_SEED_AMOUNT).toDouble()).toInt()
    }

    /**
     * Hypothetical payout preview formula — includes the hypothetical stake
     * in both the pool total and grand total before calculating.
     */
    private fun calculatePreviewPayout(
        hypotheticalAmount: Int,
        currentPoolTotal: Int,
        currentGrandTotal: Int
    ): Int {
        val newPoolTotal  = currentPoolTotal + hypotheticalAmount
        val newGrandTotal = currentGrandTotal + hypotheticalAmount
        val userPoolTotal = newPoolTotal - POOL_SEED_AMOUNT
        if (userPoolTotal <= 0) return hypotheticalAmount
        return calculatePayout(hypotheticalAmount, newPoolTotal, newGrandTotal)
    }

    // ── Staking ───────────────────────────────────────────────────────────────

    suspend fun placeStake(userId: Uuid, marketPoolId: Uuid, amount: Int): Stake {
        require(amount > 0) { "Stake amount must be greater than 0" }

        val pool = withContext(Dispatchers.IO) {
            marketPoolRepository.getById(marketPoolId)
        } ?: error("Market pool $marketPoolId not found")

        if (eventRepository.getById(pool.eventId, null)?.status != "open") {
            error("Event is not open for staking")
        }

        val hasEnough = withContext(Dispatchers.IO) {
            userRepository.hasBalance(userId, amount.toLong())
        }
        check(hasEnough) { "Insufficient balance" }

        withContext(Dispatchers.IO) {
            userRepository.subtractBalance(userId, amount.toLong())
        }

        return withContext(Dispatchers.IO) {
            stakeRepository.addToStake(pool.id, userId, amount)
        }
    }

    // ── Payout Preview ────────────────────────────────────────────────────────

    suspend fun getPayoutPreview(eventId: Uuid, marketPoolId: Uuid, hypotheticalAmount: Int): Int {
        val pools = withContext(Dispatchers.IO) {
            marketPoolRepository.getByEventId(eventId)
        }
        val targetPool = pools.find { it.id == marketPoolId }
            ?: error("Pool $marketPoolId not found in event $eventId")

        val grandTotal = pools.sumOf { it.amount }

        return calculatePreviewPayout(hypotheticalAmount, targetPool.amount, grandTotal)
    }

    // ── Current Payout ────────────────────────────────────────────────────────

    suspend fun getCurrentPayout(userId: Uuid, marketPoolId: Uuid): Int {
        val pool = withContext(Dispatchers.IO) {
            marketPoolRepository.getById(marketPoolId)
        } ?: error("Pool $marketPoolId not found")

        val stake = withContext(Dispatchers.IO) {
            stakeRepository.findByUserAndPool(userId, marketPoolId)
        } ?: return 0

        val pools      = withContext(Dispatchers.IO) { marketPoolRepository.getByEventId(pool.eventId) }
        val grandTotal = pools.sumOf { it.amount }

        return calculatePayout(stake.amount, pool.amount, grandTotal)
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    /**
     * Resolves an event:
     *   1. Marks [winningPoolId] as the winner.
     *   2. Sets the event status to resolved.
     *   3. Pays out the entire grand total + POOL_SEED_AMOUNT bonus proportionally
     *      to all winning stakers based on their share of the winning pool.
     */
    suspend fun resolveEvent(eventId: Uuid, winningPoolId: Uuid) {
        val pools = withContext(Dispatchers.IO) {
            marketPoolRepository.getByEventId(eventId)
        }
        require(pools.any { it.id == winningPoolId }) {
            "Pool $winningPoolId does not belong to event $eventId"
        }

        val grandTotal    = pools.sumOf { it.amount }
        val winningPool   = pools.first { it.id == winningPoolId }
        val winningStakes = withContext(Dispatchers.IO) {
            stakeRepository.getByMarketPoolId(winningPoolId)
        }

        coroutineScope {
            launch(Dispatchers.IO) {
                marketPoolRepository.markWinner(winningPoolId)
            }

            launch(Dispatchers.IO) {
                val eventWithBookmark = eventRepository.getById(eventId, null) ?: return@launch
                eventRepository.update(
                    Event(
                        id             = eventWithBookmark.id,
                        topicId        = eventWithBookmark.topicId,
                        format         = eventWithBookmark.format,
                        name           = eventWithBookmark.name,
                        description    = eventWithBookmark.description,
                        closeTime      = eventWithBookmark.closeTime,
                        status         = "resolved",
                        resolutionRule = eventWithBookmark.resolutionRule,
                        logoPath       = eventWithBookmark.logoPath,
                        pandaScoreId   = eventWithBookmark.pandaScoreId
                    )
                )
            }

            if (winningPool.amount > 0 && grandTotal > 0) {
                winningStakes.forEach { stake: Stake ->
                    if (stake.amount > 0) {
                        launch(Dispatchers.IO) {
                            val payout = calculatePayout(stake.amount, winningPool.amount, grandTotal)
                            userRepository.addBalance(stake.userId, payout.toLong())
                        }
                    }
                }
            }
        }
    }
}