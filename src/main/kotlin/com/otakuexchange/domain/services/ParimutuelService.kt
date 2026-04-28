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
import com.otakuexchange.domain.event.EventStatus
import com.otakuexchange.domain.repositories.parimutuel.IFirstStakeBonusRepository
import com.otakuexchange.domain.repositories.IBalanceTransactionRepository
import com.otakuexchange.domain.BalanceTransactionType

class ParimutuelService(
    private val stakeRepository: IStakeRepository,
    private val marketPoolRepository: IMarketPoolRepository,
    private val eventRepository: IEventRepository,
    private val userRepository: IUserRepository,
    private val firstStakeBonusRepository: IFirstStakeBonusRepository,
    private val balanceTransactionRepository: IBalanceTransactionRepository
) {

    companion object {
        const val FIRST_STAKE_BONUS_CAP = 50000L  // max $500 bonus
    }

    private fun calculatePayout(userStake: Long, poolTotal: Long, grandTotal: Long, multiplier: Int = 1): Long {
        if (poolTotal <= 0L) return 0L
        val basePayout = (userStake.toDouble() / poolTotal.toDouble() * grandTotal.toDouble()).toLong()
        val profit = basePayout - userStake
        return userStake + (profit * multiplier)
    }

    private fun calculatePreviewPayout(
        hypotheticalAmount: Long,
        currentPoolTotal: Long,
        currentGrandTotal: Long,
        multiplier: Int = 1
    ): Long {
        val newPoolTotal  = currentPoolTotal + hypotheticalAmount
        val newGrandTotal = currentGrandTotal + hypotheticalAmount
        return calculatePayout(hypotheticalAmount, newPoolTotal, newGrandTotal, multiplier)
    }

    suspend fun placeStake(userId: Uuid, marketPoolId: Uuid, amount: Long): Stake {
        require(amount > 0L) { "Stake amount must be greater than 0" }

        val pool = withContext(Dispatchers.IO) {
            marketPoolRepository.getById(marketPoolId)
        } ?: error("Market pool $marketPoolId not found")

        if (eventRepository.getById(pool.eventId, null)?.status !in listOf(EventStatus.open, EventStatus.hidden)) {
            error("Event is not open for staking")
        }

        val hasEnough = withContext(Dispatchers.IO) {
            userRepository.hasBalance(userId, amount)
        }
        check(hasEnough) { "Insufficient balance" }

        val isFirstStake = withContext(Dispatchers.IO) {
            !firstStakeBonusRepository.hasBonus(userId, pool.eventId)
        }
        val bonus = if (isFirstStake) minOf(amount, FIRST_STAKE_BONUS_CAP) else 0L
        val totalAmount = amount + bonus

        withContext(Dispatchers.IO) {
            userRepository.subtractBalance(userId, amount)
        }

        if (isFirstStake && bonus > 0L) {
            withContext(Dispatchers.IO) {
                firstStakeBonusRepository.recordBonus(userId, pool.eventId, bonus)
            }
        }

        return withContext(Dispatchers.IO) {
            stakeRepository.addToStake(pool.id, userId, totalAmount)
        }
    }

    suspend fun getPayoutPreview(eventId: Uuid, marketPoolId: Uuid, hypotheticalAmount: Long): Long {
        val pools = withContext(Dispatchers.IO) {
            marketPoolRepository.getByEventId(eventId)
        }
        val targetPool = pools.find { it.id == marketPoolId }
            ?: error("Pool $marketPoolId not found in event $eventId")

        val grandTotal = pools.sumOf { it.amount }
        val multiplier = eventRepository.getEventMultiplier(eventId)

        return calculatePreviewPayout(hypotheticalAmount, targetPool.amount, grandTotal, multiplier)
    }

    suspend fun getCurrentPayout(userId: Uuid, marketPoolId: Uuid): Long {
        val pool = withContext(Dispatchers.IO) {
            marketPoolRepository.getById(marketPoolId)
        } ?: error("Pool $marketPoolId not found")

        val stake = withContext(Dispatchers.IO) {
            stakeRepository.findByUserAndPool(userId, marketPoolId)
        } ?: return 0L

        val pools      = withContext(Dispatchers.IO) { marketPoolRepository.getByEventId(pool.eventId) }
        val grandTotal = pools.sumOf { it.amount }
        val multiplier = eventRepository.getEventMultiplier(pool.eventId)

        return calculatePayout(stake.amount, pool.amount, grandTotal, multiplier)
    }

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
        val multiplier = eventRepository.getEventMultiplier(eventId)

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
                        status         = EventStatus.resolved,
                        resolutionRule = eventWithBookmark.resolutionRule,
                        logoPath       = eventWithBookmark.logoPath,
                        pandaScoreId   = eventWithBookmark.pandaScoreId,
                        multiplier     = eventWithBookmark.multiplier,
                        alias          = eventWithBookmark.alias
                    )
                )
            }

            if (winningPool.amount > 0L && grandTotal > 0L) {
                winningStakes.forEach { stake: Stake ->
                    if (stake.amount > 0L) {
                        launch(Dispatchers.IO) {
                            val payout = calculatePayout(stake.amount, winningPool.amount, grandTotal, multiplier)
                            userRepository.addBalance(stake.userId, payout)
                            val updatedUser = userRepository.findById(stake.userId)
                            if (updatedUser != null) {
                                balanceTransactionRepository.record(
                                    userId       = stake.userId,
                                    amount       = payout,
                                    balanceAfter = updatedUser.balance,
                                    type         = BalanceTransactionType.EVENT_PAYOUT,
                                    referenceId  = eventId
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}