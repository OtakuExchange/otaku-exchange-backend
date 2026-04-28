package com.otakuexchange.infra.repositories.parimutuel

import com.otakuexchange.domain.repositories.parimutuel.IFirstStakeBonusRepository
import com.otakuexchange.infra.tables.parimutuel.FirstStakeBonusTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class NeonFirstStakeBonusRepository : IFirstStakeBonusRepository {

    override suspend fun hasBonus(userId: Uuid, eventId: Uuid): Boolean = transaction {
        FirstStakeBonusTable.selectAll()
            .where { (FirstStakeBonusTable.userId eq userId) and (FirstStakeBonusTable.eventId eq eventId) }
            .singleOrNull() != null
    }

    override suspend fun recordBonus(userId: Uuid, eventId: Uuid, bonus: Long): Unit = transaction {
        FirstStakeBonusTable.insert {
            it[FirstStakeBonusTable.userId]  = userId
            it[FirstStakeBonusTable.eventId] = eventId
            it[FirstStakeBonusTable.bonus]   = bonus
        }
    }

    override suspend fun getBonusAmount(userId: Uuid, eventId: Uuid): Long? = transaction {
        FirstStakeBonusTable.selectAll()
            .where { (FirstStakeBonusTable.userId eq userId) and (FirstStakeBonusTable.eventId eq eventId) }
            .singleOrNull()
            ?.get(FirstStakeBonusTable.bonus)
    }

    override suspend fun getBonusEventIds(userId: Uuid): List<Uuid> = transaction {
        FirstStakeBonusTable.selectAll()
            .where { FirstStakeBonusTable.userId eq userId }
            .map { it[FirstStakeBonusTable.eventId] }
    }
}