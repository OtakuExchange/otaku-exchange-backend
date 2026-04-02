package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.IDailyStreakRepository
import com.otakuexchange.domain.StreakStatus
import com.otakuexchange.infra.tables.DailyStreakTable
import com.otakuexchange.infra.tables.UserTable
import com.otakuexchange.infra.tables.parimutuel.StakeTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

private const val COMEBACK_RATE = 0.20  // 20% of the remaining gap after base reward

class NeonDailyStreakRepository : IDailyStreakRepository {

    private fun rewardForStreak(streak: Int): Long = when {
        streak <= 0 -> 10000L   // $100
        streak == 1 -> 20000L   // $200
        streak == 2 -> 30000L   // $300
        streak == 3 -> 40000L   // $400
        streak == 4 -> 50000L   // $500
        else        -> 50000L   // $500 every day after
    }

    /** Get the #1 player's total (balance + stakes), excluding admins */
    private fun getLeaderTotal(): Long {
        val stakeSum = StakeTable.amount.sum()
        return UserTable
            .join(StakeTable, JoinType.LEFT, UserTable.id, StakeTable.userId)
            .select(UserTable.balance, stakeSum)
            .where { UserTable.isAdmin eq false }
            .groupBy(UserTable.id, UserTable.balance)
            .map { row -> row[UserTable.balance] + (row[stakeSum]?.toLong() ?: 0L) }
            .maxOrNull() ?: 0L
    }

    /** Get a specific user's total (balance + stakes) */
    private fun getUserTotal(userId: Uuid): Long {
        val stakeSum = StakeTable.amount.sum()
        return UserTable
            .join(StakeTable, JoinType.LEFT, UserTable.id, StakeTable.userId)
            .select(UserTable.balance, stakeSum)
            .where { UserTable.id eq userId }
            .groupBy(UserTable.id, UserTable.balance)
            .map { row -> row[UserTable.balance] + (row[stakeSum]?.toLong() ?: 0L) }
            .firstOrNull() ?: 0L
    }

    /**
     * 20% of the gap remaining after the base streak reward is applied.
     * If the user is already at or above the leader, no bonus.
     */
    private fun comebackBonus(baseReward: Long, userTotal: Long, leaderTotal: Long): Long {
        val gapAfterReward = (leaderTotal - userTotal - baseReward).coerceAtLeast(0L)
        if (gapAfterReward == 0L) return 0L
        return (gapAfterReward * COMEBACK_RATE).toLong()
    }

    override suspend fun getStatus(userId: Uuid): StreakStatus = transaction {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        val row = DailyStreakTable.selectAll()
            .where { DailyStreakTable.userId eq userId }
            .singleOrNull()

        val leaderTotal = getLeaderTotal()
        val userTotal = getUserTotal(userId)

        if (row == null) {
            val base = rewardForStreak(0)
            val bonus = comebackBonus(base, userTotal, leaderTotal)
            return@transaction StreakStatus(
                streak = 0,
                rewardCents = base,
                canClaim = true,
                comebackBonusCents = bonus
            )
        }

        val lastClaim = row[DailyStreakTable.lastClaim]
        val streak = row[DailyStreakTable.streak]
        val canClaim = lastClaim < today
        val currentStreak = if (lastClaim < yesterday) 0 else streak
        val base = rewardForStreak(currentStreak)
        val bonus = comebackBonus(base, userTotal, leaderTotal)

        StreakStatus(
            streak = currentStreak,
            rewardCents = base,
            canClaim = canClaim,
            comebackBonusCents = bonus
        )
    }

    override suspend fun claim(userId: Uuid): StreakStatus = transaction {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        val row = DailyStreakTable.selectAll()
            .where { DailyStreakTable.userId eq userId }
            .singleOrNull()

        val leaderTotal = getLeaderTotal()
        val userTotal = getUserTotal(userId)

        val newStreak: Int
        val reward: Long

        if (row == null) {
            newStreak = 1
            reward = rewardForStreak(0)
            DailyStreakTable.insert {
                it[DailyStreakTable.userId] = userId
                it[DailyStreakTable.streak] = newStreak
                it[DailyStreakTable.lastClaim] = today
            }
        } else {
            val lastClaim = row[DailyStreakTable.lastClaim]
            check(lastClaim < today) { "Already claimed today" }

            val prevStreak = row[DailyStreakTable.streak]
            newStreak = if (lastClaim == yesterday) prevStreak + 1 else 1
            reward = rewardForStreak(prevStreak)

            DailyStreakTable.update({ DailyStreakTable.userId eq userId }) {
                it[DailyStreakTable.streak] = newStreak
                it[DailyStreakTable.lastClaim] = today
            }
        }

        val bonus = comebackBonus(reward, userTotal, leaderTotal)
        val totalReward = reward + bonus

        UserTable.update({ UserTable.id eq userId }) {
            it[UserTable.balance] = UserTable.balance + totalReward
        }

        StreakStatus(
            streak = newStreak,
            rewardCents = reward,
            canClaim = false,
            comebackBonusCents = bonus
        )
    }
}