package com.otakuexchange.domain.services

import com.otakuexchange.domain.market.Order
import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.market.OrderType
import com.otakuexchange.domain.repositories.IOrderBookRepository
import com.otakuexchange.domain.repositories.ITradeHistoryRepository
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

class MarketSeederService(
    private val orderBookRepository: IOrderBookRepository,
    private val tradeHistoryRepository: ITradeHistoryRepository
) {
    private val logger = LoggerFactory.getLogger(MarketSeederService::class.java)

    private val SEEDER_USER_ID = Uuid.parse(
        System.getenv("SEEDER_USER_ID") ?: "00000000-0000-0000-0000-000000000001"
    )

    companion object {
        const val DEFAULT_LEVELS = 10
        const val DEFAULT_MIDPOINT = 50
        const val MIN_PRICE = 1
        const val MAX_PRICE = 99

        // Normal distribution quantities per level (index 0 = closest to midpoint)
        // Total per side = 105 contracts across 10 levels
        val DEFAULT_QUANTITIES = intArrayOf(25, 23, 19, 14, 10, 7, 4, 2, 1, 1)
    }

    /**
     * Seed a market with YES and NO limit orders centered around a midpoint.
     *
     * YES orders are placed below the midpoint (buyers hoping event resolves YES)
     * NO orders are placed above the midpoint (buyers hoping event resolves NO)
     *
     * Example with midpoint=50:
     *   YES @ 49¢, 48¢, 47¢... (quantity decreasing by distance)
     *   NO  @ 49¢, 48¢, 47¢... (NO price = 100 - YES price implied)
     *
     * @param midpoint    YES price center in cents (1-99), defaults to 50
     * @param levels      price levels per side (5-7 = light top-up, 10 = standard, 12-15 = heavy)
     * @param quantities  contracts per level from closest to furthest
     * @param useLastPrice center around last traded price instead of midpoint
     */
    suspend fun seed(
        marketId: Uuid,
        midpoint: Int = DEFAULT_MIDPOINT,
        levels: Int = DEFAULT_LEVELS,
        quantities: IntArray = DEFAULT_QUANTITIES,
        useLastPrice: Boolean = false
    ) {
        val center = if (useLastPrice) {
            tradeHistoryRepository.getLastTradedPrice(marketId) ?: midpoint
        } else {
            midpoint
        }.coerceIn(MIN_PRICE + levels, MAX_PRICE - levels)

        logger.info("Seeding market $marketId around ${center}¢ YES price with $levels levels per side")

        val orders = mutableListOf<Order>()

        for (level in 0 until levels) {
            val qty = if (level < quantities.size) quantities[level] else 1

            // YES orders: price steps down from (center - 1)
            // These are buyers who want to buy YES contracts
            val yesPrice = center - 1 - level

            // NO orders: stored by their NO price = 100 - YES price, steps down from (100 - center - 1)
            // These are buyers who want to buy NO contracts
            val noPrice = (100 - center) - 1 - level

            if (yesPrice >= MIN_PRICE) {
                orders.add(Order(
                    userId = SEEDER_USER_ID,
                    marketId = marketId,
                    side = OrderSide.YES,
                    price = yesPrice,
                    quantity = qty,
                    lockedAmount = 0L, // seeder bypasses balance checks
                    orderType = OrderType.LIMIT
                ))
            }

            if (noPrice >= MIN_PRICE) {
                orders.add(Order(
                    userId = SEEDER_USER_ID,
                    marketId = marketId,
                    side = OrderSide.NO,
                    price = noPrice,
                    quantity = qty,
                    lockedAmount = 0L,
                    orderType = OrderType.LIMIT
                ))
            }
        }

        orders.forEach { orderBookRepository.insertOrder(it) }
        logger.info("Seeded ${orders.size} orders into market $marketId")
    }
}