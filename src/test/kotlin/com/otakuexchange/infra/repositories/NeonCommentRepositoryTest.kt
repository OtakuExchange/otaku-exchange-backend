package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.market.Comment
import com.otakuexchange.testutil.PostgresTestDb
import com.otakuexchange.testutil.Seed
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@Testcontainers(disabledWithoutDocker = true)
class NeonCommentRepositoryTest {
    private val repo = NeonCommentRepository()

    companion object {
        class KPostgres(imageName: DockerImageName) : PostgreSQLContainer<KPostgres>(imageName)

        @Container
        @JvmField
        val postgres = KPostgres(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("otaku_exchange_test")
            .withUsername("test")
            .withPassword("test")

        private lateinit var db: Database

        private val topicId = Uuid.parse("00000000-0000-0000-0000-000000010000")
        private val eventId = Uuid.parse("00000000-0000-0000-0000-000000010001")
        private val user1Id = Uuid.parse("00000000-0000-0000-0000-000000010010")
        private val user2Id = Uuid.parse("00000000-0000-0000-0000-000000010011")

        @BeforeAll
        @JvmStatic
        fun setupDb() {
            db = PostgresTestDb.connect(postgres)
            PostgresTestDb.createSchema(db)
        }
    }

    @BeforeEach
    fun cleanDb() {
        PostgresTestDb.truncateAll(db)
        Seed.topic(db, id = topicId)
        Seed.event(db, id = eventId, topicId = topicId)
        Seed.user(db, id = user1Id, username = "user1", email = "user1@example.com")
        Seed.user(db, id = user2Id, username = "user2", email = "user2@example.com")
    }

    @Test
    fun save_andGetByEventId_returnsUserAndLikeCounts() = runTest {
        val c1 = Comment(eventId = eventId, userId = user1Id, content = "hello")
        val c2 = Comment(eventId = eventId, userId = user2Id, content = "world", parentId = c1.id)
        repo.save(c1)
        repo.save(c2)

        // user2 likes comment1
        assertTrue(repo.likeComment(c1.id, user2Id))
        assertFalse(repo.likeComment(c1.id, user2Id))

        val anon = repo.getByEventId(eventId, currentUserId = null)
        assertEquals(setOf(c1.id, c2.id), anon.map { it.id }.toSet())
        assertEquals(1L, anon.single { it.id == c1.id }.likes)
        assertEquals(false, anon.single { it.id == c1.id }.likedByUser)

        val asUser2 = repo.getByEventId(eventId, currentUserId = user2Id)
        assertEquals(1L, asUser2.single { it.id == c1.id }.likes)
        assertEquals(true, asUser2.single { it.id == c1.id }.likedByUser)
        assertEquals("user1", asUser2.single { it.id == c1.id }.user.username)
    }

    @Test
    fun unlikeComment_deletesLike() = runTest {
        val c1 = Comment(eventId = eventId, userId = user1Id, content = "hello")
        repo.save(c1)

        assertTrue(repo.likeComment(c1.id, user2Id))
        assertTrue(repo.unlikeComment(c1.id, user2Id))
        assertFalse(repo.unlikeComment(c1.id, user2Id))

        val comments = repo.getByEventId(eventId, currentUserId = user2Id)
        assertEquals(0L, comments.single { it.id == c1.id }.likes)
        assertEquals(false, comments.single { it.id == c1.id }.likedByUser)
    }
}

