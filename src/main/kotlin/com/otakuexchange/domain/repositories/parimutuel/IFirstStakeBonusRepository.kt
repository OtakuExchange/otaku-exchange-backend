package com.otakuexchange.domain.repositories.parimutuel

import kotlin.uuid.Uuid

interface IFirstStakeBonusRepository {
    suspend fun hasBonus(userId: Uuid, eventId: Uuid): Boolean
    suspend fun recordBonus(userId: Uuid, eventId: Uuid, bonus: Int)
    suspend fun getBonusAmount(userId: Uuid, eventId: Uuid): Int?
    suspend fun getBonusEventIds(userId: Uuid): List<Uuid>
}