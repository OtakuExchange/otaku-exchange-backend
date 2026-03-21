package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.rank.WalletRankEntry
import com.otakuexchange.domain.repositories.IRankRepository
import com.otakuexchange.infra.tables.UserTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class NeonRankRepository : IRankRepository {

    override suspend fun getWalletLeaderboard(limit: Int): List<WalletRankEntry> = transaction {
        UserTable.selectAll()
            .orderBy(UserTable.balance, SortOrder.DESC)
            .limit(limit)
            .mapIndexed { index, row ->
                WalletRankEntry(
                    rank = index + 1,
                    userId = row[UserTable.id],
                    username = row[UserTable.username],
                    balance = row[UserTable.balance]
                )
            }
    }
}