package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.OrderRecord
import kotlin.uuid.Uuid

interface IOrderRecordRepository {
    suspend fun save(record: OrderRecord): OrderRecord
    suspend fun update(record: OrderRecord): OrderRecord
    suspend fun findById(id: Uuid): OrderRecord?
    suspend fun findByUserId(userId: Uuid): List<OrderRecord>
    suspend fun findByMarketId(marketId: Uuid): List<OrderRecord>
}