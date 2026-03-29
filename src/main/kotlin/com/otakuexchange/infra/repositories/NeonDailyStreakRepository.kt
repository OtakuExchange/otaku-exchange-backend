package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.repositories.IDailyStreakRepository
import com.otakuexchange.domain.StreakStatus
import com.otakuexchange.infra.tables.DailyStreakTable
import com.otakuexchange.infra.tables.UserTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.plus

import kotlin.uuid.Uuid

class NeonDailyStreakRepository : IDailyStreakRepository {

    private fun rewardForStreak(streak: Int): Long = when {
        streak <= 0 -> 2000L
        streak == 1 -> 4000L
        streak == 2 -> 6000L
        streak == 3 -> 8000L
        streak == 4 -> 10000L
        else        -> 10000L
    }

    override suspend fun getStatus(userId: Uuid): StreakStatus = transaction {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        val row = DailyStreakTable.selectAll()
            .where { DailyStreakTable.userId eq userId }
            .singleOrNull()

        if (row == null) {
            return@transaction StreakStatus(streak = 0, rewardCents = 2000L, canClaim = true)
        }

        val lastClaim = row[DailyStreakTable.lastClaim]
        val streak = row[DailyStreakTable.streak]
        val canClaim = lastClaim < today
        val currentStreak = if (lastClaim < yesterday) 0 else streak

        StreakStatus(
            streak = currentStreak,
            rewardCents = rewardForStreak(currentStreak),
            canClaim = canClaim
        )
    }

    override suspend fun claim(userId: Uuid): StreakStatus = transaction {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        val row = DailyStreakTable.selectAll()
            .where { DailyStreakTable.userId eq userId }
            .singleOrNull()

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

        UserTable.update({ UserTable.id eq userId }) {
            it[UserTable.balance] = UserTable.balance + reward
        }

        StreakStatus(streak = newStreak, rewardCents = reward, canClaim = false)
    }
}