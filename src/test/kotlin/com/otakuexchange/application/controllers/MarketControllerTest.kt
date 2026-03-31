package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.Entity
import com.otakuexchange.domain.market.Market
import com.otakuexchange.domain.market.MarketWithEntity
import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.repositories.IEntityRepository
import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.repositories.IOrderBookRepository
import com.otakuexchange.domain.repositories.ITradeHistoryRepository
import com.otakuexchange.testutil.createTestJwt
import com.otakuexchange.testutil.testApp
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarketControllerTest {

    private val marketRepo = mockk<IMarketRepository>()
    private val entityRepo = mockk<IEntityRepository>()
    private val orderBookRepo = mockk<IOrderBookRepository>()
    private val tradeHistoryRepo = mockk<ITradeHistoryRepository>()

    private val token = createTestJwt("clerk_market_user")
    private val eventId = Uuid.parse("00000000-0000-0000-0000-000000000010")
    private val marketId = Uuid.parse("00000000-0000-0000-0000-000000000020")
    private val entityId = Uuid.parse("00000000-0000-0000-0000-000000000030")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun marketWithEntity() = MarketWithEntity(
        id = marketId, eventId = eventId, label = "Will X win?",
        createdAt = now, status = "OPEN"
    )

    private fun controller() = MarketController(marketRepo, entityRepo, orderBookRepo, tradeHistoryRepo)

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        coEvery { orderBookRepo.getBestOrders(any(), any(), any()) } returns emptyList()
        coEvery { orderBookRepo.getWorstOrder(any(), any()) } returns null
        coEvery { tradeHistoryRepo.getLastTradedPrice(any()) } returns null
    }

    // ── GET /events/{eventId}/markets ───────────────────────────────────────

    @Test
    fun getMarkets_returnsList() {
        coEvery { marketRepo.getMarketsByEventId(eventId) } returns listOf(marketWithEntity())
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/$eventId/markets")
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"label\":\"Will X win?\""))
        }
    }

    @Test
    fun getMarkets_invalidUuid_returns400() {
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/bad-uuid/markets")
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    // ── GET /markets/{id} ───────────────────────────────────────────────────

    @Test
    fun getMarketById_found() {
        coEvery { marketRepo.getById(marketId) } returns marketWithEntity()
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/markets/$marketId")
            assertEquals(HttpStatusCode.OK, res.status)
        }
    }

    @Test
    fun getMarketById_notFound_returns404() {
        coEvery { marketRepo.getById(marketId) } returns null
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/markets/$marketId")
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }

    // ── GET /markets/{id}/forecast ──────────────────────────────────────────

    @Test
    fun getForecast_found() {
        coEvery { marketRepo.getById(marketId) } returns marketWithEntity()
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/markets/$marketId/forecast")
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"forecast\""))
        }
    }

    @Test
    fun getForecast_notFound_returns404() {
        coEvery { marketRepo.getById(marketId) } returns null
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/markets/$marketId/forecast")
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }

    // ── GET /entities ───────────────────────────────────────────────────────

    @Test
    fun getEntities_returnsList() {
        coEvery { entityRepo.getAll() } returns listOf(
            Entity(id = entityId, name = "Team A", logoPath = "/a.png", createdAt = now)
        )
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/entities")
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"name\":\"Team A\""))
        }
    }

    // ── GET /entities/{id} ──────────────────────────────────────────────────

    @Test
    fun getEntityById_notFound_returns404() {
        coEvery { entityRepo.getById(entityId) } returns null
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/entities/$entityId")
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }

    // ── POST /markets (protected) ───────────────────────────────────────────

    @Test
    fun postMarket_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/markets") {
                contentType(ContentType.Application.Json)
                setBody("""{"eventId":"$eventId","label":"M","id":"$marketId","status":"OPEN"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun postMarket_authenticated_returns201() {
        val market = Market(id = marketId, eventId = eventId, label = "M")
        coEvery { marketRepo.save(any()) } returns market
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/markets") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"eventId":"$eventId","label":"M","id":"$marketId","status":"OPEN"}""")
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }
    }

    // ── DELETE /markets/{id} (protected) ────────────────────────────────────

    @Test
    fun deleteMarket_found_returns204() {
        coEvery { marketRepo.delete(marketId) } returns true
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/markets/$marketId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NoContent, res.status)
        }
    }

    @Test
    fun deleteMarket_notFound_returns404() {
        coEvery { marketRepo.delete(marketId) } returns false
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/markets/$marketId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }
}
