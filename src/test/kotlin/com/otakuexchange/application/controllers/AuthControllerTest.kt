package com.otakuexchange.application.controllers

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

class AuthControllerTest {

    private val userRepo = mockk<IUserRepository>()

    private val clerkSub = "clerk_auth_user"
    private val userId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val user = User(
        id = userId, username = "testuser", email = "t@e.com",
        authProvider = AuthProvider.CLERK, providerUserId = clerkSub,
        balance = 1000, lockedBalance = 200, createdAt = now
    )
    private val token = createTestJwt(clerkSub)

    private fun controller() = AuthController(userRepo)

    @BeforeEach
    fun setUp() = clearAllMocks()

    // AuthController routes are protected; we register them under protectedRoutes.

    // ── GET /users/me ───────────────────────────────────────────────────────

    @Test
    fun getMe_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.get("/users/me")
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun getMe_found_returnsUserResponse() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.get("/users/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, res.status)
            val body = res.bodyAsText()
            assertTrue(body.contains("\"username\":\"testuser\""))
            assertTrue(body.contains("\"balance\":1000"))
        }
    }

    // ── PATCH /users/me/username ────────────────────────────────────────────

    @Test
    fun updateUsername_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.patch("/users/me/username") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":"newname"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun updateUsername_conflict_returns409() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        coEvery { userRepo.findByUsername("taken") } returns mockk()
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.patch("/users/me/username") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"username":"taken"}""")
            }
            assertEquals(HttpStatusCode.Conflict, res.status)
        }
    }

    @Test
    fun updateUsername_success() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        coEvery { userRepo.findByUsername("newname") } returns null
        coEvery { userRepo.updateUsername(userId, "newname") } returns user.copy(username = "newname")
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.patch("/users/me/username") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"username":"newname"}""")
            }
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"username\":\"newname\""))
        }
    }
}
