package com.otakuexchange.domain

import kotlin.uuid.Uuid

enum class BalanceTransactionType {
    EVENT_PAYOUT,
    EVENT_STAKE,
    DEPOSIT,
    REFUND,
    PARLAY_STAKE,
    PARLAY_PAYOUT,
    DAILY_REWARD
}

data class BalanceTransaction(
    val id: Uuid,
    val userId: Uuid,
    val amount: Long,
    val balance: Long,
    val type: BalanceTransactionType,
    val referenceId: Uuid? = null
)