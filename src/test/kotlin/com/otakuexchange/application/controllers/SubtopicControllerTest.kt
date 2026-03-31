package com.otakuexchange.application.controllers

import com.otakuexchange.domain.event.EventWithBookmark
import com.otakuexchange.domain.market.Subtopic
import com.otakuexchange.domain.repositories.ISubtopicRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.user.AuthProvider
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

class SubtopicControllerTest {

    private val subtopicRepo = mockk<ISubtopicRepository>()
    private val userRepo = mockk<IUserRepository>()

    private val token = createTestJwt("clerk_sub_user")
    private val subtopicId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val topicId = Uuid.parse("00000000-0000-0000-0000-000000000010")
    private val eventId = Uuid.parse("00000000-0000-0000-0000-000000000020")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun controller() = SubtopicController(subtopicRepo, userRepo)

    @BeforeEach
    fun setUp() = clearAllMocks()

    // ── GET /subtopics/{id}/events ──────────────────────────────────────────

    @Test
    fun getEventsBySubtopic_returnsList() {
        coEvery { userRepo.findByProviderUserId(any(), any()) } returns null
        coEvery { subtopicRepo.getEventsBySubtopicId(subtopicId, null) } returns emptyList()
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/subtopics/$subtopicId/events")
            assertEquals(HttpStatusCode.OK, res.status)
        }
    }

    @Test
    fun getEventsBySubtopic_invalidUuid_returns400() {
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/subtopics/bad-uuid/events")
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    // ── POST /topics/{topicId}/subtopics (protected) ────────────────────────

    @Test
    fun postSubtopic_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/topics/$topicId/subtopics") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Sub","topicId":"$topicId","id":"$subtopicId"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun postSubtopic_authenticated_returns201() {
        val subtopic = Subtopic(id = subtopicId, topicId = topicId, name = "Sub", createdAt = now)
        coEvery { subtopicRepo.save(any()) } returns subtopic
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/topics/$topicId/subtopics") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Sub","topicId":"$topicId","id":"$subtopicId"}""")
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }
    }

    // ── DELETE /subtopics/{id} (protected) ──────────────────────────────────

    @Test
    fun deleteSubtopic_found_returns204() {
        coEvery { subtopicRepo.delete(subtopicId) } returns true
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/subtopics/$subtopicId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NoContent, res.status)
        }
    }

    @Test
    fun deleteSubtopic_notFound_returns404() {
        coEvery { subtopicRepo.delete(subtopicId) } returns false
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/subtopics/$subtopicId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }

    // ── POST /events/{eventId}/subtopics/{subtopicId} (protected) ───────────

    @Test
    fun addEventToSubtopic_returns204() {
        coEvery { subtopicRepo.addEventToSubtopic(eventId, subtopicId) } just Runs
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/events/$eventId/subtopics/$subtopicId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NoContent, res.status)
        }
    }

    @Test
    fun addEventToSubtopic_invalidEventId_returns400() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/events/bad-uuid/subtopics/$subtopicId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    // ── DELETE /events/{eventId}/subtopics/{subtopicId} (protected) ─────────

    @Test
    fun removeEventFromSubtopic_returns204() {
        coEvery { subtopicRepo.removeEventFromSubtopic(eventId, subtopicId) } just Runs
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/events/$eventId/subtopics/$subtopicId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NoContent, res.status)
        }
    }
}
