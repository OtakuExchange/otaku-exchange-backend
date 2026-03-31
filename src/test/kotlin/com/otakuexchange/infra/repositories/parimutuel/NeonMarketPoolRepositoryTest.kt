package com.otakuexchange.repositories.parimutuel

import com.otakuexchange.domain.parimutuel.MarketPool
import com.otakuexchange.infra.repositories.parimutuel.NeonMarketPoolRepository
import com.otakuexchange.infra.tables.parimutuel.MarketPoolTable
import com.otakuexchange.testutil.PostgresTestDb
import com.otakuexchange.testutil.Seed
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Testcontainers(disabledWithoutDocker = true)
class NeonMarketPoolRepositoryTest {
    private val repo = NeonMarketPoolRepository()

    companion object {
        class KPostgres(imageName: DockerImageName) : PostgreSQLContainer<KPostgres>(imageName)

        @Container
        @JvmField
        val postgres = KPostgres(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("otaku_exchange_test")
            .withUsername("test")
            .withPassword("test")

        private lateinit var db: Database

        private val topicId = Uuid.parse("00000000-0000-0000-0000-000000000001")
        private val event1Id = Uuid.parse("00000000-0000-0000-0000-000000000002")
        private val event2Id = Uuid.parse("00000000-0000-0000-0000-000000000003")
        private val user1Id = Uuid.parse("00000000-0000-0000-0000-000000000010")
        private val user2Id = Uuid.parse("00000000-0000-0000-0000-000000000011")
        private val entityId = Uuid.parse("00000000-0000-0000-0000-000000000020")
        private val poolAId = Uuid.parse("00000000-0000-0000-0000-000000000100")
        private val poolBId = Uuid.parse("00000000-0000-0000-0000-000000000101")

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
        Seed.event(db, id = event1Id, topicId = topicId)
        Seed.event(db, id = event2Id, topicId = topicId)
        Seed.user(db, id = user1Id, username = "user1", email = "user1@example.com")
        Seed.user(db, id = user2Id, username = "user2", email = "user2@example.com")
        Seed.entity(db, id = entityId, name = "Team A")
        Seed.marketPool(db, id = poolAId, eventId = event1Id, entityId = entityId, label = "Team A")
        Seed.marketPool(db, id = poolBId, eventId = event1Id, entityId = null, label = "Other")
    }

    @Test
    fun createAndGetById_roundTrip() = runTest {
        val poolId = Uuid.parse("00000000-0000-0000-0000-000000000200")
        val now = Instant.fromEpochMilliseconds(1_700_000_000_123)
        val created = repo.create(
            MarketPool(
                id = poolId,
                eventId = event2Id,
                entityId = null,
                label = "New Pool",
                isWinner = false,
                amount = 0,
                createdAt = now,
                updatedAt = now
            )
        )

        assertEquals(poolId, created.id)
        val loaded = repo.getById(poolId)
        assertNotNull(loaded)
        assertEquals("New Pool", loaded.label)
        assertEquals(event2Id, loaded.eventId)
    }

    @Test
    fun getByEventId_filtersPools() = runTest {
        val poolsEvent1 = repo.getByEventId(event1Id)
        assertEquals(setOf(poolAId, poolBId), poolsEvent1.map { it.id }.toSet())

        val poolIdEvent2 = Uuid.parse("00000000-0000-0000-0000-000000000201")
        Seed.marketPool(db, id = poolIdEvent2, eventId = event2Id, label = "E2")
        val poolsEvent2 = repo.getByEventId(event2Id)
        assertEquals(setOf(poolIdEvent2), poolsEvent2.map { it.id }.toSet())
    }

    @Test
    fun addToPool_incrementsAmount() = runTest {
        Seed.setPoolAmount(db, poolAId, 100)
        val updated = repo.addToPool(poolAId, 25)
        assertEquals(125, updated.amount)
    }

    @Test
    fun markWinner_setsIsWinner() = runTest {
        val updated = repo.markWinner(poolAId)
        assertEquals(true, updated.isWinner)
    }

    @Test
    fun getByEventIdWithEntity_hydratesEntityAndComputesVolume() = runTest {
        // Pool A: total staked 150
        Seed.stakeRow(
            db,
            id = Uuid.parse("00000000-0000-0000-0000-000000001000"),
            userId = user1Id,
            marketPoolId = poolAId,
            amount = 100
        )
        Seed.stakeRow(
            db,
            id = Uuid.parse("00000000-0000-0000-0000-000000001001"),
            userId = user2Id,
            marketPoolId = poolAId,
            amount = 50
        )
        Seed.setPoolAmount(db, poolAId, 150)

        // Pool B: total staked 25
        Seed.stakeRow(
            db,
            id = Uuid.parse("00000000-0000-0000-0000-000000001010"),
            userId = user1Id,
            marketPoolId = poolBId,
            amount = 25
        )
        Seed.setPoolAmount(db, poolBId, 25)

        val pools = repo.getByEventIdWithEntity(event1Id)
        assertEquals(2, pools.size)

        val poolA = pools.single { it.id == poolAId }
        assertNotNull(poolA.entity)
        assertEquals(150, poolA.volume)
        assertEquals(150, poolA.amount)

        val poolB = pools.single { it.id == poolBId }
        assertNull(poolB.entity)
        assertEquals(25, poolB.volume)
        assertEquals(25, poolB.amount)
    }

    @Test
    fun delete_removesPoolRow() = runTest {
        val deleted = repo.delete(poolAId)
        assertEquals(true, deleted)

        val row = transaction(db) {
            MarketPoolTable.selectAll().where { MarketPoolTable.id eq poolAId }.singleOrNull()
        }
        assertNull(row)
    }
}

