package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.market.Topic
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
class NeonTopicRepositoryTest {
    private val repo = NeonTopicRepository()

    companion object {
        class KPostgres(imageName: DockerImageName) : PostgreSQLContainer<KPostgres>(imageName)

        @Container
        @JvmField
        val postgres = KPostgres(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("otaku_exchange_test")
            .withUsername("test")
            .withPassword("test")

        private lateinit var db: Database

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
    }

    @Test
    fun getTopics_groupsSubtopicsByTopic() = runTest {
        val topicA = Uuid.parse("00000000-0000-0000-0000-000000001000")
        val topicB = Uuid.parse("00000000-0000-0000-0000-000000001001")
        Seed.topic(db, id = topicA, topic = "A")
        Seed.topic(db, id = topicB, topic = "B")

        val subA1 = Uuid.parse("00000000-0000-0000-0000-000000002000")
        val subA2 = Uuid.parse("00000000-0000-0000-0000-000000002001")
        val subB1 = Uuid.parse("00000000-0000-0000-0000-000000002010")
        Seed.subtopic(db, id = subA1, topicId = topicA, name = "A1")
        Seed.subtopic(db, id = subA2, topicId = topicA, name = "A2")
        Seed.subtopic(db, id = subB1, topicId = topicB, name = "B1")

        val topics = repo.getTopics()
        assertEquals(setOf(topicA, topicB), topics.map { it.id }.toSet())

        val loadedA = topics.single { it.id == topicA }
        assertEquals(setOf(subA1, subA2), loadedA.subtopics.map { it.id }.toSet())

        val loadedB = topics.single { it.id == topicB }
        assertEquals(setOf(subB1), loadedB.subtopics.map { it.id }.toSet())
    }

    @Test
    fun getById_returnsTopicWithSubtopics_orNull() = runTest {
        val topicId = Uuid.parse("00000000-0000-0000-0000-000000001010")
        Seed.topic(db, id = topicId, topic = "T")

        val subId = Uuid.parse("00000000-0000-0000-0000-000000002020")
        Seed.subtopic(db, id = subId, topicId = topicId, name = "S")

        val loaded = repo.getById(topicId)
        assertNotNull(loaded)
        assertEquals(topicId, loaded.id)
        assertEquals(setOf(subId), loaded.subtopics.map { it.id }.toSet())

        assertNull(repo.getById(Uuid.parse("00000000-0000-0000-0000-000000001099")))
    }

    @Test
    fun save_update_delete_roundTrip() = runTest {
        val id = Uuid.parse("00000000-0000-0000-0000-000000001020")
        repo.save(Topic(id = id, topic = "Old", description = null, hidden = false))

        repo.update(Topic(id = id, topic = "New", description = "desc", hidden = true))
        val loaded = repo.getById(id)
        assertNotNull(loaded)
        assertEquals("New", loaded.topic)
        assertEquals("desc", loaded.description)
        assertEquals(true, loaded.hidden)

        assertEquals(true, repo.delete(id))
        assertNull(repo.getById(id))
    }
}

