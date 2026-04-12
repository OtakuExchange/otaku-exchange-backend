package com.otakuexchange.domain.repositories

import kotlin.uuid.Uuid
import com.otakuexchange.domain.BalanceTransaction
import com.otakuexchange.domain.BalanceTransactionType

interface IBalanceTransactionRepository {
    suspend fun record(
        userId: Uuid,
        amount: Long,
        balanceAfter: Long,
        type: BalanceTransactionType,
        referenceId: Uuid? = null,
    ): BalanceTransaction

    suspend fun getByUserId(userId: Uuid): List<BalanceTransaction>
}