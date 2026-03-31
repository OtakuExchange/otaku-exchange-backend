package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.Entity
import com.otakuexchange.domain.repositories.IEntityRepository
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

class EntityControllerTest {

    private val entityRepo = mockk<IEntityRepository>()
    private val userRepo = mockk<IUserRepository>()

    private val clerkSub = "clerk_entity_user"
    private val userId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val entityId = Uuid.parse("00000000-0000-0000-0000-000000000030")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val adminUser = User(id = userId, username = "admin", email = "a@e.com", authProvider = AuthProvider.CLERK, providerUserId = clerkSub, isAdmin = true, createdAt = now)
    private val normalUser = adminUser.copy(isAdmin = false)
    private val token = createTestJwt(clerkSub)

    private fun controller() = EntityController(entityRepo, userRepo)

    @BeforeEach
    fun setUp() = clearAllMocks()

    // ── GET /entities (public) ──────────────────────────────────────────────

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

    // ── POST /entities (protected, admin) ───────────────────────────────────

    @Test
    fun postEntity_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/entities") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"X","logoPath":"/x.png"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun postEntity_nonAdmin_returns403() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns normalUser
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/entities") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"X","logoPath":"/x.png"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, res.status)
        }
    }

    @Test
    fun postEntity_admin_returns201() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns adminUser
        coEvery { entityRepo.save(any()) } returns Entity(id = entityId, name = "X", logoPath = "/x.png", createdAt = now)
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/entities") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"X","logoPath":"/x.png"}""")
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }
    }
}
