package com.otakuexchange.application.controllers

import com.otakuexchange.domain.event.Event
import com.otakuexchange.domain.event.EventWithBookmark
import com.otakuexchange.domain.market.Comment
import com.otakuexchange.domain.market.CommentWithLikes
import com.otakuexchange.domain.market.CommentUser
import com.otakuexchange.domain.repositories.IBookmarkRepository
import com.otakuexchange.domain.repositories.ICommentRepository
import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.services.EventSchedulerService
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
import com.otakuexchange.domain.event.EventStatus

class EventControllerTest {

    private val eventRepo      = mockk<IEventRepository>()
    private val commentRepo    = mockk<ICommentRepository>()
    private val userRepo       = mockk<IUserRepository>()
    private val bookmarkRepo   = mockk<IBookmarkRepository>()
    private val eventScheduler = mockk<EventSchedulerService>()

    private val clerkSub  = "clerk_event_user"
    private val userId    = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val topicId   = Uuid.parse("00000000-0000-0000-0000-000000000010")
    private val eventId   = Uuid.parse("00000000-0000-0000-0000-000000000020")
    private val commentId = Uuid.parse("00000000-0000-0000-0000-000000000030")
    private val now       = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val user      = User(id = userId, username = "u", email = "u@e.com", authProvider = AuthProvider.CLERK, providerUserId = clerkSub, createdAt = now)
    private val adminUser = user.copy(isAdmin = true)
    private val token     = createTestJwt(clerkSub)

    private fun event() = EventWithBookmark(
        id = eventId, topicId = topicId, format = "single", name = "E", description = "d",
        closeTime = now, status = EventStatus.open, resolutionRule = "r", bookmarked = false
    )

    private fun controller() = EventController(eventRepo, commentRepo, userRepo, bookmarkRepo, eventScheduler)

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        // blanket scheduler mocks so tests that don't care about scheduling don't fail
        every { eventScheduler.schedule(any()) } just Runs
        every { eventScheduler.cancel(any()) } just Runs
    }

    // ── GET /topics/{topicId}/events ─────────────────────────────────────────

    @Test
    fun getEventsByTopic_returnsList() {
        coEvery { userRepo.findByProviderUserId(any(), any()) } returns null
        coEvery { eventRepo.getEventsByTopicId(topicId, null) } returns listOf(event())
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/topics/$topicId/events")
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"name\":\"E\""))
        }
    }

    @Test
    fun getEventsByTopic_invalidUuid_returns400() {
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/topics/bad-uuid/events")
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    // ── GET /events/{id} ────────────────────────────────────────────────────

    @Test
    fun getEventById_found() {
        coEvery { userRepo.findByProviderUserId(any(), any()) } returns null
        coEvery { eventRepo.getById(eventId, null) } returns event()
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/$eventId")
            assertEquals(HttpStatusCode.OK, res.status)
        }
    }

    @Test
    fun getEventById_notFound_returns404() {
        coEvery { userRepo.findByProviderUserId(any(), any()) } returns null
        coEvery { eventRepo.getById(eventId, null) } returns null
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/$eventId")
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }

    // ── GET /events/{id}/comments ───────────────────────────────────────────

    @Test
    fun getComments_returnsList() {
        coEvery { userRepo.findByProviderUserId(any(), any()) } returns null
        coEvery { commentRepo.getByEventId(eventId, null) } returns emptyList()
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/$eventId/comments")
            assertEquals(HttpStatusCode.OK, res.status)
        }
    }

    // ── GET /events/open ────────────────────────────────────────────────────

    @Test
    fun getOpenEvents_returnsList() {
        coEvery { userRepo.findByProviderUserId(any(), any()) } returns null
        coEvery { eventRepo.getEventsByStatus("open", null) } returns emptyList()
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/open")
            assertEquals(HttpStatusCode.OK, res.status)
        }
    }

    // ── GET /events/resolved/recent ─────────────────────────────────────────

    @Test
    fun getRecentlyResolved_returnsList() {
        coEvery { userRepo.findByProviderUserId(any(), any()) } returns null
        coEvery { eventRepo.getRecentlyResolvedEvents(null) } returns emptyList()
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/events/resolved/recent")
            assertEquals(HttpStatusCode.OK, res.status)
        }
    }

    // ── POST /events/{id}/comments (protected) ─────────────────────────────

    @Test
    fun postComment_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/events/$eventId/comments") {
                contentType(ContentType.Application.Json)
                setBody("""{"content":"hello"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun postComment_authenticated_returns201() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        val comment = Comment(eventId = eventId, userId = userId, content = "hello")
        coEvery { commentRepo.save(any()) } returns comment
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/events/$eventId/comments") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"content":"hello"}""")
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }
    }

    // ── POST /events/{id}/bookmark (protected) ─────────────────────────────

    @Test
    fun addBookmark_returns204() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        coEvery { bookmarkRepo.addBookmark(userId, eventId) } returns true
        coEvery { eventRepo.getById(eventId, userId) } returns event().copy(bookmarked = true)
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/events/$eventId/bookmark") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"bookmarked\":true"))
        }
    }

    @Test
    fun addBookmark_alreadyBookmarked_returns409() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        coEvery { bookmarkRepo.addBookmark(userId, eventId) } returns false
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/events/$eventId/bookmark") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.Conflict, res.status)
        }
    }

    // ── DELETE /events/{id}/bookmark (protected) ────────────────────────────

    @Test
    fun removeBookmark_returns204() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        coEvery { bookmarkRepo.removeBookmark(userId, eventId) } returns true
        coEvery { eventRepo.getById(eventId, userId) } returns event().copy(bookmarked = false)
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/events/$eventId/bookmark") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"bookmarked\":false"))
        }
    }

    @Test
    fun removeBookmark_notFound_returns404() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        coEvery { bookmarkRepo.removeBookmark(userId, eventId) } returns false
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/events/$eventId/bookmark") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }

    // ── PATCH /events/{id}/status (protected, admin) ────────────────────────

    @Test
    fun updateStatus_nonAdmin_returns403() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns user
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.patch("/events/$eventId/status") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"status":"open"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, res.status)
        }
    }

    @Test
    fun updateStatus_admin_invalidStatus_returns400() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns adminUser
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.patch("/events/$eventId/status") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"status":"invalid_status"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    @Test
    fun updateStatus_admin_validStatus_returns204() {
        coEvery { userRepo.findByProviderUserId(clerkSub, AuthProvider.CLERK) } returns adminUser
        coEvery { eventRepo.updateStatus(eventId, "open") } returns true
        coEvery { eventRepo.getById(eventId, null) } returns event()
        coEvery { eventRepo.getById(eventId, userId) } returns event().copy(status = EventStatus.open)
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.patch("/events/$eventId/status") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"status":"open"}""")
            }
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"status\":\"open\""))
        }
    }

    // ── DELETE /events/{id} (protected) ─────────────────────────────────────

    @Test
    fun deleteEvent_found_returns204() {
        coEvery { eventRepo.delete(eventId) } returns true
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/events/$eventId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NoContent, res.status)
        }
    }

    @Test
    fun deleteEvent_notFound_returns404() {
        coEvery { eventRepo.delete(eventId) } returns false
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/events/$eventId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }
}