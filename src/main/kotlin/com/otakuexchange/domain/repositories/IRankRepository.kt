package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.rank.WalletRankEntry

interface IRankRepository {
    suspend fun getWalletLeaderboard(limit: Int = 100): List<WalletRankEntry>
}