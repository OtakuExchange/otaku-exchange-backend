package com.otakuexchange.application.controllers

import com.otakuexchange.domain.market.Topic
import com.otakuexchange.domain.market.TopicWithSubtopics
import com.otakuexchange.domain.repositories.ITopicRepository
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
import kotlin.uuid.Uuid

class TopicControllerTest {

    private val topicRepo = mockk<ITopicRepository>()
    private val token = createTestJwt("clerk_user")

    private val topicId = Uuid.parse("00000000-0000-0000-0000-000000000001")

    private fun controller() = TopicController(topicRepo)

    @BeforeEach
    fun setUp() = clearAllMocks()

    // ── GET /topics ─────────────────────────────────────────────────────────

    @Test
    fun getTopics_returnsList() {
        coEvery { topicRepo.getTopics() } returns listOf(
            TopicWithSubtopics(id = topicId, topic = "Anime", subtopics = emptyList())
        )
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/topics")
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"topic\":\"Anime\""))
        }
    }

    // ── GET /topics/{id} ────────────────────────────────────────────────────

    @Test
    fun getTopicById_found() {
        coEvery { topicRepo.getById(topicId) } returns TopicWithSubtopics(id = topicId, topic = "Anime", subtopics = emptyList())
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/topics/$topicId")
            assertEquals(HttpStatusCode.OK, res.status)
        }
    }

    @Test
    fun getTopicById_notFound_returns404() {
        coEvery { topicRepo.getById(topicId) } returns null
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/topics/$topicId")
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }

    @Test
    fun getTopicById_invalidUuid_returns400() {
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/topics/not-a-uuid")
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
    }

    // ── POST /topics (protected) ────────────────────────────────────────────

    @Test
    fun postTopic_noAuth_returns401() {
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/topics") {
                contentType(ContentType.Application.Json)
                setBody("""{"topic":"New","id":"$topicId"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
    }

    @Test
    fun postTopic_authenticated_returns201() {
        val topic = Topic(id = topicId, topic = "New")
        coEvery { topicRepo.save(any()) } returns topic
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.post("/topics") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"topic":"New","id":"$topicId"}""")
            }
            assertEquals(HttpStatusCode.Created, res.status)
        }
    }

    // ── DELETE /topics/{id} (protected) ─────────────────────────────────────

    @Test
    fun deleteTopic_found_returns204() {
        coEvery { topicRepo.delete(topicId) } returns true
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/topics/$topicId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NoContent, res.status)
        }
    }

    @Test
    fun deleteTopic_notFound_returns404() {
        coEvery { topicRepo.delete(topicId) } returns false
        val c = controller()
        testApp(protectedRoutes = { c.registerProtectedRoutes(this) }) { client ->
            val res = client.delete("/topics/$topicId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
    }
}
