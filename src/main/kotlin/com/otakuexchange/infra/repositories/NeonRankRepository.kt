class NeonRankRepository : IRankRepository {
    override suspend fun getWalletLeaderboard(limit: Int): List<WalletRankEntry> = transaction {
        val activeStakesByUser = StakeTable
            .join(MarketPoolTable, JoinType.INNER, StakeTable.marketPoolId, MarketPoolTable.id)
            .join(EventTable, JoinType.INNER, MarketPoolTable.eventId, EventTable.id)
            .select(StakeTable.userId, StakeTable.amount.sum())
            .where { EventTable.status neq "resolved" }
            .groupBy(StakeTable.userId)
            .associate { it[StakeTable.userId] to (it[StakeTable.amount.sum()]?.toLong() ?: 0L) }

        val pinnedUsernames = listOf("flayedon", "redakted")

        UserTable
            .selectAll()
            .where { (UserTable.isAdmin eq false) or (UserTable.username inList pinnedUsernames) }
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