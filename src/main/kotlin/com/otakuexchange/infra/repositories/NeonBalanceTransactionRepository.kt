package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.BalanceTransaction
import com.otakuexchange.domain.BalanceTransactionType
import com.otakuexchange.domain.repositories.IBalanceTransactionRepository
import com.otakuexchange.infra.tables.BalanceTransactionTable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class NeonBalanceTransactionRepository : IBalanceTransactionRepository {

    override suspend fun record(
        userId: Uuid,
        amount: Long,
        balanceAfter: Long,
        type: BalanceTransactionType,
        referenceId: Uuid?,
    ): BalanceTransaction = transaction {
        val row = BalanceTransactionTable.insertReturning {
            it[BalanceTransactionTable.id]          = Uuid.random()
            it[BalanceTransactionTable.userId]      = userId
            it[BalanceTransactionTable.amount]      = amount
            it[BalanceTransactionTable.balance]     = balanceAfter
            it[BalanceTransactionTable.type]        = type.name
            it[BalanceTransactionTable.referenceId] = referenceId
            it[BalanceTransactionTable.createdAt]   = Clock.System.now()
        }.single()
        row.toDomain()
    }

    override suspend fun getByUserId(userId: Uuid): List<BalanceTransaction> = transaction {
        BalanceTransactionTable
            .selectAll()
            .where { BalanceTransactionTable.userId eq userId }
            .orderBy(BalanceTransactionTable.createdAt, SortOrder.DESC)
            .map { it.toDomain() }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toDomain() = BalanceTransaction(
        id          = this[BalanceTransactionTable.id],
        userId      = this[BalanceTransactionTable.userId],
        amount      = this[BalanceTransactionTable.amount],
        balance     = this[BalanceTransactionTable.balance],
        type        = BalanceTransactionType.valueOf(this[BalanceTransactionTable.type]),
        referenceId = this[BalanceTransactionTable.referenceId],
    )
}