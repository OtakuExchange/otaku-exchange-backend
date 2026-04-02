package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.rank.WalletRankEntry
import com.otakuexchange.domain.repositories.IRankRepository
import com.otakuexchange.infra.tables.EventTable
import com.otakuexchange.infra.tables.UserTable
import com.otakuexchange.infra.tables.parimutuel.MarketPoolTable
import com.otakuexchange.infra.tables.parimutuel.StakeTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class NeonRankRepository : IRankRepository {
    override suspend fun getWalletLeaderboard(limit: Int): List<WalletRankEntry> = transaction {
        // Sum only stakes on non-resolved events
        val activeStakesByUser = StakeTable
            .join(MarketPoolTable, JoinType.INNER, StakeTable.marketPoolId, MarketPoolTable.id)
            .join(EventTable, JoinType.INNER, MarketPoolTable.eventId, EventTable.id)
            .select(StakeTable.userId, StakeTable.amount.sum())
            .where { EventTable.status neq "resolved" }
            .groupBy(StakeTable.userId)
            .associate { it[StakeTable.userId] to (it[StakeTable.amount.sum()]?.toLong() ?: 0L) }

        UserTable
            .selectAll()
            .where { UserTable.isAdmin eq false }
            .map { row ->
                val activeStakes = activeStakesByUser[row[UserTable.id]] ?: 0L
                WalletRankEntry(
                    rank      = 0,
                    userId    = row[UserTable.id],
                    username  = row[UserTable.username],
                    balance   = row[UserTable.balance] + activeStakes,
                    avatarUrl = row[UserTable.avatarUrl]
                )
            }
            .sortedByDescending { it.balance }
            .take(limit)
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
    }
}