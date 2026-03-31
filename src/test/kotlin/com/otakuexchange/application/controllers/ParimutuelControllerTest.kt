package com.otakuexchange.application.controllers

import com.otakuexchange.domain.parimutuel.MarketPoolWithEntity
import com.otakuexchange.domain.parimutuel.Stake
import com.otakuexchange.domain.parimutuel.StakeWithPool
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.repositories.parimutuel.IMarketPoolRepository
import com.otakuexchange.domain.repositories.parimutuel.IStakeRepository
import com.otakuexchange.domain.services.ParimutuelService
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

class ParimutuelControllerTest {

    private val parimutuelService = mockk<ParimutuelService>()
    private val poolRepo = mockk<IMarketPoolRepository>()
    private val stakeRepo = mockk<IStakeRepository>()
    private val userRepo = mockk<IUserRepository>()

    private val clerkSub = "clerk_user_123"
    private val userId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val eventId = Uuid.parse("00000000-0000-0000-0000-000000000010")
    private val poolId = Uuid.parse("00000000-0000-0000-0000-000000000100")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val normalUser = User(id = userId, username = "u", email = "u@e.com", authProvider = AuthProvider.CLERK, providerUserId = clerkSub, createdAt = now)
    private val adminUser = normalUser.copy(isAdmin = true)
    private val token = createTestJwt(clerkSub)

    private fun controller() = ParimutuelController(parimutuelService, poolRepo, stakeRepo, userRepo)

    @BeforeEach
    fun setUp() = clearAllMocks()

    // ── Public: GET /events/{eventId}/pools ──────────────────────────────────

    @Test
    fun getPools_returnsList() {
        coEvery { poolRepo.getByEventIdWithEntity(eventId) } returns listOf(
            MarketPoolWithEntity(id = poolId, eventId = eventId, label = "A", createdAt = now, updatedAt = now)
        )
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/$eventId/pools")
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"label\":\"A\""))
        }
    }

    @Test
    fun getPools_invalidUuid_returns400() {
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/not-a-uuid/pools")
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    // ── Public: GET /events/{eventId}/pools/{poolId}/preview ─────────────────

    @Test
    fun preview_validAmount_returns200() {
        coEvery { parimutuelService.getPayoutPreview(eventId, poolId, 200) } returns 400
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/$eventId/pools/$poolId/preview?amount=200")
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"projectedPayout\":400"))
        }
    }

    @Test
    fun preview_negativeAmount_returns400() {
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/$eventId/pools/$poolId/preview?amount=-1")
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    // ── Protected: POST /stakes ─────────────────────────────────────────────

    @Test
    fun postStake_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/stakes") {
                contentType(ContentType.Application.Json)
                setBody("""{"marketPoolId":"$poolId","amount":100}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun postStake_happyPath_returns201() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns normalUser
        val stake = Stake(userId = userId, marketPoolId = poolId, amount = 100, createdAt = now, updatedAt = now)
        coEvery { parimutuelService.placeStake(userId, poolId, 100) } returns stake
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/stakes") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"marketPoolId":"$poolId","amount":100}""")
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }
    }

    @Test
    fun postStake_amountZero_returns400() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns normalUser
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/stakes") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"marketPoolId":"$poolId","amount":0}""")
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    @Test
    fun postStake_insufficientBalance_returns402() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns normalUser
        coEvery { parimutuelService.placeStake(userId, poolId, 100) } throws IllegalStateException("Insufficient balance")
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/stakes") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"marketPoolId":"$poolId","amount":100}""")
            }
            assertEquals(HttpStatusCode.PaymentRequired, res.status)
        }
    }

    // ── Protected: POST /events/{eventId}/resolve ───────────────────────────

    @Test
    fun resolve_nonAdmin_returns403() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns normalUser
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/events/$eventId/resolve") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"winningPoolId":"$poolId"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, res.status)
        }
    }

    @Test
    fun resolve_admin_returns200() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns adminUser
        coEvery { parimutuelService.resolveEvent(eventId, poolId) } just Runs
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/events/$eventId/resolve") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"winningPoolId":"$poolId"}""")
            }
            assertEquals(HttpStatusCode.OK, res.status)
        }
    }

    // ── Protected: GET /stakes/me ───────────────────────────────────────────

    @Test
    fun getMyStakes_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.get("/stakes/me")
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun getMyStakes_authenticated_returns200() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns normalUser
        coEvery { stakeRepo.getByUserId(userId) } returns emptyList()
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.get("/stakes/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, res.status)
        }
    }
}
