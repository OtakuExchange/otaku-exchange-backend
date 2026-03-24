package com.otakuexchange.domain.repositories

import com.otakuexchange.domain.market.OrderRecord
import com.otakuexchange.domain.market.OrderStatus
import com.otakuexchange.domain.market.OrderType
import kotlin.uuid.Uuid

interface IOrderRecordRepository {
    suspend fun save(record: OrderRecord): OrderRecord
    suspend fun update(record: OrderRecord): OrderRecord
    suspend fun findById(id: Uuid): OrderRecord?
    suspend fun findByUserId(userId: Uuid, status: OrderStatus? = null, orderType: OrderType? = null): List<OrderRecord>
    suspend fun findByMarketId(marketId: Uuid): List<OrderRecord>
}