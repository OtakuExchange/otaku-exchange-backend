package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.MarketWithEntity
import com.otakuexchange.domain.market.OrderSide
import com.otakuexchange.domain.market.Position
import com.otakuexchange.domain.market.MarketStatus
import com.otakuexchange.domain.repositories.IMarketRepository
import com.otakuexchange.domain.repositories.IPositionRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
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

class AdminControllerTest {
    private val marketRepo = mockk<IMarketRepository>()
    private val positionRepo = mockk<IPositionRepository>()
    private val userRepo = mockk<IUserRepository>()

    private val clerkSub = "clerk_admin"
    private val userId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val marketId = Uuid.parse("00000000-0000-0000-0000-000000000020")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val adminUser = User(id = userId, username = "admin", email = "a@e.com", authProvider = AuthProvider.CLERK, providerUserId = clerkSub, isAdmin = true, createdAt = now)
    private val normalUser = adminUser.copy(isAdmin = false)
    private val token = createTestJwt(clerkSub)

    private fun controller() = AdminController(marketRepo, positionRepo, userRepo)

    @BeforeEach
    fun setUp() = clearAllMocks()

    // ── POST /admin/markets/{id}/resolve ────────────────────────────────────

    @Test
    fun resolve_nonAdmin_returns403() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns normalUser
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/admin/markets/$marketId/resolve") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"resolution":"YES"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, res.status)
        }
    }

    @Test
    fun resolve_invalidUuid_returns400() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns adminUser
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/admin/markets/bad-uuid/resolve") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"resolution":"YES"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    @Test
    fun resolve_invalidResolution_returns400() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns adminUser
        coEvery { marketRepo.getById(marketId) } returns MarketWithEntity(
            id = marketId, eventId = Uuid.random(), label = "M", status = "OPEN", createdAt = now
        )
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/admin/markets/$marketId/resolve") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"resolution":"MAYBE"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    @Test
    fun resolve_marketNotFound_returns404() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns adminUser
        coEvery { marketRepo.getById(marketId) } returns null
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/admin/markets/$marketId/resolve") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"resolution":"YES"}""")
            }
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }

    @Test
    fun resolve_alreadyResolved_returns409() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns adminUser
        coEvery { marketRepo.getById(marketId) } returns MarketWithEntity(
            id = marketId, eventId = Uuid.random(), label = "M", status = "RESOLVED_YES", createdAt = now
        )
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/admin/markets/$marketId/resolve") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"resolution":"YES"}""")
            }
            assertEquals(HttpStatusCode.Conflict, res.status)
        }
    }

    @Test
    fun resolve_happyPath_returns200() {
        val eventIdLocal = Uuid.random()
        val user2 = Uuid.parse("00000000-0000-0000-0000-000000000002")
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns adminUser
        coEvery { marketRepo.getById(marketId) } returns MarketWithEntity(
            id = marketId, eventId = eventIdLocal, label = "M", status = "OPEN", createdAt = now
        )
        coEvery { positionRepo.getPositionsByMarket(marketId) } returns listOf(
            Position(userId = user2, marketId = marketId, side = OrderSide.YES, quantity = 10, avgPrice = 50, lockedAmount = 500, createdAt = now, updatedAt = now)
        )
        coEvery { userRepo.addBalance(user2, 500L) } returns mockk()
        coEvery { positionRepo.deletePosition(user2, marketId, OrderSide.YES) } just Runs
        coEvery { marketRepo.updateStatus(marketId, MarketStatus.RESOLVED_YES) } just Runs
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/admin/markets/$marketId/resolve") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"resolution":"YES"}""")
            }
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("resolved YES"))
        }
    }
}
