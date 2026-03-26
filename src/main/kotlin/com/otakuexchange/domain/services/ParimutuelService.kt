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

    // ── Staking ───────────────────────────────────────────────────────────────

    suspend fun placeStake(userId: Uuid, marketPoolId: Uuid, amount: Int): Stake {
        require(amount > 0) { "Stake amount must be greater than 0" }

        val pool = withContext(Dispatchers.IO) {
            marketPoolRepository.getById(marketPoolId)
        } ?: error("Market pool $marketPoolId not found")

        val deducted = withContext(Dispatchers.IO) {
            userRepository.lockBalance(userId, amount.toLong())
        }
        check(deducted) { "Insufficient balance" }

        withContext(Dispatchers.IO) {
            userRepository.consumeLockedBalance(userId, amount.toLong())
        }

        return withContext(Dispatchers.IO) {
            stakeRepository.addToStake(pool.id, userId, amount)
        }
    }

    // ── Payout Preview ────────────────────────────────────────────────────────

    /**
     * Hypothetical payout if [hypotheticalAmount] cents were staked into [marketPoolId]
     * and that pool won, given the current pool state.
     *
     * Formula:
     *   newPoolTotal  = pool.amount + hypotheticalAmount
     *   newGrandTotal = grandTotal  + hypotheticalAmount
     *   payout        = (hypotheticalAmount / newPoolTotal) * newGrandTotal
     */
    suspend fun getPayoutPreview(eventId: Uuid, marketPoolId: Uuid, hypotheticalAmount: Int): Int {
        val pools = withContext(Dispatchers.IO) {
            marketPoolRepository.getByEventId(eventId)
        }
        val targetPool = pools.find { it.id == marketPoolId }
            ?: error("Pool $marketPoolId not found in event $eventId")

        val grandTotal    = pools.sumOf { it.amount }
        val newPoolTotal  = targetPool.amount + hypotheticalAmount
        val newGrandTotal = grandTotal + hypotheticalAmount

        if (newPoolTotal == 0) return hypotheticalAmount

        return (hypotheticalAmount.toDouble() / newPoolTotal.toDouble() * newGrandTotal.toDouble()).toInt()
    }

    /**
     * What the user would actually receive if their pool won right now,
     * based on their real recorded stake.
     */
    suspend fun getCurrentPayout(userId: Uuid, marketPoolId: Uuid): Int {
        val pool = withContext(Dispatchers.IO) {
            marketPoolRepository.getById(marketPoolId)
        } ?: error("Pool $marketPoolId not found")

        val stake = withContext(Dispatchers.IO) {
            stakeRepository.findByUserAndPool(userId, marketPoolId)
        } ?: return 0

        val pools      = withContext(Dispatchers.IO) { marketPoolRepository.getByEventId(pool.eventId) }
        val grandTotal = pools.sumOf { it.amount }

        if (pool.amount == 0) return 0

        return (stake.amount.toDouble() / pool.amount.toDouble() * grandTotal.toDouble()).toInt()
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    /**
     * Resolves an event:
     *   1. Marks [winningPoolId] as the winner.
     *   2. Sets the event status to RESOLVED.
     *   3. Pays out the entire grand total proportionally to all winning stakers.
     *
     * payout per winner = (theirStake / winningPoolTotal) * grandTotal
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
                        status         = "RESOLVED",
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
                            val payout = (stake.amount.toDouble() / winningPool.amount.toDouble() * grandTotal.toDouble()).toInt()
                            userRepository.addBalance(stake.userId, payout.toLong())
                        }
                    }
                }
            }
        }
    }
}