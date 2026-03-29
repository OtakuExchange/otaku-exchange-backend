package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.rank.WalletRankEntry
import com.otakuexchange.domain.repositories.IRankRepository
import com.otakuexchange.infra.tables.UserTable
import com.otakuexchange.infra.tables.parimutuel.StakeTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class NeonRankRepository : IRankRepository {
    override suspend fun getWalletLeaderboard(limit: Int): List<WalletRankEntry> = transaction {
        val stakeSum = StakeTable.amount.sum()

        UserTable
            .join(StakeTable, JoinType.LEFT, UserTable.id, StakeTable.userId)
            .select(UserTable.id, UserTable.username, UserTable.balance, UserTable.avatarUrl, stakeSum)
            .where { UserTable.isAdmin eq false }
            .groupBy(UserTable.id, UserTable.username, UserTable.balance, UserTable.avatarUrl)
            .limit(limit)
            .map { row ->
                WalletRankEntry(
                    rank = 0,
                    userId = row[UserTable.id],
                    username = row[UserTable.username],
                    balance = row[UserTable.balance] + (row[stakeSum]?.toLong() ?: 0L),
                    avatarUrl = row[UserTable.avatarUrl]
                )
            }
            .sortedByDescending { it.balance }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
    }
}