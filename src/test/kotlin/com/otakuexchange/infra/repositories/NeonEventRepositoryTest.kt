package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.event.Event
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid
import com.otakuexchange.domain.event.EventStatus

@Testcontainers(disabledWithoutDocker = true)
class NeonEventRepositoryTest {
    private val repo = NeonEventRepository()

    companion object {
        class KPostgres(imageName: DockerImageName) : PostgreSQLContainer<KPostgres>(imageName)

        @Container
        @JvmField
        val postgres = KPostgres(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("otaku_exchange_test")
            .withUsername("test")
            .withPassword("test")

        private lateinit var db: Database

        private val topicId = Uuid.parse("00000000-0000-0000-0000-000000030000")
        private val userId = Uuid.parse("00000000-0000-0000-0000-000000030010")

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
        Seed.user(db, id = userId, username = "user", email = "user@example.com")
    }

    @Test
    fun save_getById_includesBookmark() = runTest {
        val eventId = Uuid.parse("00000000-0000-0000-0000-000000030100")
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val event = Event(
            id = eventId,
            topicId = topicId,
            format = "single",
            name = "E1",
            description = "desc",
            closeTime = now,
            status = EventStatus.open,
            resolutionRule = "rule",
            createdAt = now
        )
        repo.save(event)

        Seed.bookmark(
            db,
            id = Uuid.parse("00000000-0000-0000-0000-000000030400"),
            userId = userId,
            eventId = eventId
        )

        val loaded = repo.getById(eventId, currentUserId = userId)
        assertNotNull(loaded)
        assertEquals(true, loaded.bookmarked)
        assertEquals(emptyList(), loaded.subtopicIds)
    }

    @Test
    fun getEventsByTopicId_returnsEvents_andBookmarkFlag() = runTest {
        val e1 = Uuid.parse("00000000-0000-0000-0000-000000031000")
        val e2 = Uuid.parse("00000000-0000-0000-0000-000000031001")
        Seed.event(db, id = e1, topicId = topicId)
        Seed.event(db, id = e2, topicId = topicId)

        Seed.bookmark(
            db,
            id = Uuid.parse("00000000-0000-0000-0000-000000031400"),
            userId = userId,
            eventId = e1
        )

        val events = repo.getEventsByTopicId(topicId, currentUserId = userId)
        assertEquals(2, events.size)
        assertEquals(setOf(e1, e2), events.map { it.id }.toSet())
        assertEquals(true, events.single { it.id == e1 }.bookmarked)
        assertEquals(false, events.single { it.id == e2 }.bookmarked)
        assertEquals(emptyList(), events.single { it.id == e1 }.subtopicIds)
        assertEquals(emptyList(), events.single { it.id == e2 }.subtopicIds)
    }

    @Test
    fun updateStatus_closeStaking_update_delete_work() = runTest {
        val eventId = Uuid.parse("00000000-0000-0000-0000-000000032000")
        Seed.event(db, id = eventId, topicId = topicId, status = "open")

        assertEquals(true, repo.closeStaking(eventId))
        assertEquals(EventStatus.staking_closed, repo.getById(eventId, null)!!.status)

        val updated = Event(
            id = eventId,
            topicId = topicId,
            format = "double",
            name = "Updated",
            description = "Updated desc",
            closeTime = Seed.fixedInstant,
            status = EventStatus.resolved,
            resolutionRule = "new rule"
        )
        repo.update(updated)
        val loaded = repo.getById(eventId, null)
        assertNotNull(loaded)
        assertEquals("Updated", loaded.name)
        assertEquals("double", loaded.format)

        assertEquals(true, repo.delete(eventId))
        assertNull(repo.getById(eventId, null))
    }

    @Test
    fun getRecentlyResolvedEvents_filtersByCloseTimeWindow() = runTest {
        val now = Clock.System.now()
        val recentId = Uuid.parse("00000000-0000-0000-0000-000000033000")
        val oldId = Uuid.parse("00000000-0000-0000-0000-000000033001")

        // Contract: repository filters by status == EventStatus.resolved.name ("resolved") AND closeTime within last 7 days.
        Seed.event(db, id = recentId, topicId = topicId, status = "resolved", closeTime = now - 2.days)
        Seed.event(db, id = oldId, topicId = topicId, status = "resolved", closeTime = now - 30.days)

        val resolved = repo.getRecentlyResolvedEvents(currentUserId = null)
        assertEquals(setOf(recentId), resolved.map { it.id }.toSet())
    }
}

