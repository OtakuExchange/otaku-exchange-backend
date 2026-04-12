package com.otakuexchange.application.controllers

import com.otakuexchange.domain.StreakStatus
import com.otakuexchange.domain.repositories.IDailyStreakRepository
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
import com.otakuexchange.domain.repositories.IBalanceTransactionRepository

class DailyStreakControllerTest {

    private val streakRepo = mockk<IDailyStreakRepository>()
    private val userRepo = mockk<IUserRepository>()
    private val balanceTransactionRepo = mockk<IBalanceTransactionRepository>()

    private val clerkSub = "clerk_streak_user"
    private val userId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val user = User(id = userId, username = "u", email = "u@e.com", authProvider = AuthProvider.CLERK, providerUserId = clerkSub, createdAt = now)
    private val token = createTestJwt(clerkSub)

    private fun controller() = DailyStreakController(streakRepo, userRepo, balanceTransactionRepo)

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        coEvery { balanceTransactionRepo.record(any(), any(), any(), any(), anyNullable()) } returns mockk()
        coEvery { userRepo.findById(any()) } returns user
    }

    // ── GET /rewards/daily ──────────────────────────────────────────────────

    @Test
    fun getStatus_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.get("/rewards/daily")
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun getStatus_userNotFound_returns404() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns null
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.get("/rewards/daily") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }

    @Test
    fun getStatus_returnsStreakStatus() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        coEvery { streakRepo.getStatus(userId) } returns StreakStatus(streak = 3, rewardCents = 300, canClaim = true)
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.get("/rewards/daily") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, res.status)
            val body = res.bodyAsText()
            assertTrue(body.contains("\"streak\":3"))
            assertTrue(body.contains("\"canClaim\":true"))
        }
    }

    // ── POST /rewards/daily/claim ───────────────────────────────────────────

    @Test
    fun claim_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/rewards/daily/claim")
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun claim_success_returnsStatus() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        coEvery { streakRepo.claim(userId) } returns StreakStatus(streak = 4, rewardCents = 400, canClaim = false)
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/rewards/daily/claim") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"streak\":4"))
        }
    }

    @Test
    fun claim_alreadyClaimed_returns400() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        coEvery { streakRepo.claim(userId) } throws IllegalStateException("Already claimed today")
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/rewards/daily/claim") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }
}
