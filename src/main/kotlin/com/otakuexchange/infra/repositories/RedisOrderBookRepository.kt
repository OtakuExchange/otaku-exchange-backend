package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.market.Order
import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.repositories.IOrderBookRepository
import com.otakuexchange.infra.RedisFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

class RedisOrderBookRepository : IOrderBookRepository {

    // Key format: orderbook:YES:{marketId} or orderbook:NO:{marketId}
    private fun bookKey(marketId: Uuid, side: OrderSide) =
        "orderbook:${side.name}:$marketId"

    private fun orderKey(orderId: Uuid) = "order:$orderId"

    override suspend fun insertOrder(order: Order): Order = withContext(Dispatchers.IO) {
        RedisFactory.pool.getResource().use { jedis ->
            jedis.set(orderKey(order.id), Json.encodeToString(order))
            // Both YES and NO stored by their own price, descending = best
            // YES: highest price = most confident YES buyer
            // NO: highest price = most confident NO buyer (lowest implied YES price)
            jedis.zadd(bookKey(order.marketId, order.side), order.price.toDouble(), order.id.toString())
        }
        order
    }

    override suspend fun getBestOrders(marketId: Uuid, side: OrderSide, limit: Int): List<Order> =
        withContext(Dispatchers.IO) {
            RedisFactory.pool.getResource().use { jedis ->
                val key = bookKey(marketId, side)
                // Both YES and NO: highest price = best match (most willing to pay)
                val ids: List<String> = jedis.zrevrange(key, 0, limit.toLong() - 1).toList()
                ids.mapNotNull { id ->
                    jedis.get(orderKey(Uuid.parse(id)))?.let {
                        Json.decodeFromString<Order>(it)
                    }
                }
            }
        }

    override suspend fun removeOrder(order: Order): Unit = withContext(Dispatchers.IO) {
        RedisFactory.pool.getResource().use { jedis ->
            jedis.zrem(bookKey(order.marketId, order.side), order.id.toString())
            jedis.del(orderKey(order.id))
        }
    }

    override suspend fun updateRemaining(order: Order): Unit = withContext(Dispatchers.IO) {
        RedisFactory.pool.getResource().use { jedis ->
            jedis.set(orderKey(order.id), Json.encodeToString(order))
        }
    }

    override suspend fun getOrder(orderId: Uuid): Order? = withContext(Dispatchers.IO) {
        RedisFactory.pool.getResource().use { jedis ->
            jedis.get(orderKey(orderId))?.let {
                Json.decodeFromString<Order>(it)
            }
        }
    }
}