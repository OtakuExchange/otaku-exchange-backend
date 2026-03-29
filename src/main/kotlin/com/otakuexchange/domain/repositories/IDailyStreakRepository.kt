package com.otakuexchange.domain.repositories

import kotlin.uuid.Uuid
import com.otakuexchange.domain.StreakStatus

interface IDailyStreakRepository {
    suspend fun getStatus(userId: Uuid): StreakStatus
    suspend fun claim(userId: Uuid): StreakStatus
}