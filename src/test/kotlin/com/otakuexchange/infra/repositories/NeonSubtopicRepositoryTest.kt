package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.market.Subtopic
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

@Testcontainers(disabledWithoutDocker = true)
class NeonSubtopicRepositoryTest {
    private val repo = NeonSubtopicRepository()

    companion object {
        class KPostgres(imageName: DockerImageName) : PostgreSQLContainer<KPostgres>(imageName)

        @Container
        @JvmField
        val postgres = KPostgres(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("otaku_exchange_test")
            .withUsername("test")
            .withPassword("test")

        private lateinit var db: Database

        private val topicId = Uuid.parse("00000000-0000-0000-0000-000000020000")
        private val subtopicId = Uuid.parse("00000000-0000-0000-0000-000000020010")
        private val eventId = Uuid.parse("00000000-0000-0000-0000-000000020100")
        private val userId = Uuid.parse("00000000-0000-0000-0000-000000020200")

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
        Seed.user(db, id = userId, username = "user", email = "user@example.com")
    }

    @Test
    fun save_getById_getByTopicId_delete_roundTrip() = runTest {
        val sub = Subtopic(id = subtopicId, topicId = topicId, name = "S1")
        repo.save(sub)

        assertNotNull(repo.getById(subtopicId))
        assertEquals(setOf(subtopicId), repo.getByTopicId(topicId).map { it.id }.toSet())

        assertEquals(true, repo.delete(subtopicId))
        assertNull(repo.getById(subtopicId))
    }

    @Test
    fun addAndRemoveEventFromSubtopic_affectsGetEventsBySubtopicId_andBookmark() = runTest {
        repo.save(Subtopic(id = subtopicId, topicId = topicId, name = "S1"))

        repo.addEventToSubtopic(eventId, subtopicId)
        val noUser = repo.getEventsBySubtopicId(subtopicId, currentUserId = null)
        assertEquals(setOf(eventId), noUser.map { it.id }.toSet())
        assertEquals(false, noUser.single().bookmarked)

        Seed.bookmark(
            db,
            id = Uuid.parse("00000000-0000-0000-0000-000000020300"),
            userId = userId,
            eventId = eventId
        )
        val withUser = repo.getEventsBySubtopicId(subtopicId, currentUserId = userId)
        assertEquals(true, withUser.single().bookmarked)

        repo.removeEventFromSubtopic(eventId, subtopicId)
        val after = repo.getEventsBySubtopicId(subtopicId, currentUserId = userId)
        assertEquals(emptyList(), after)
    }
}

