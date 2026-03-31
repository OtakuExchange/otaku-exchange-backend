package com.otakuexchange.repositories.parimutuel

import com.otakuexchange.infra.repositories.parimutuel.NeonStakeRepository
import com.otakuexchange.infra.tables.parimutuel.MarketPoolTable
import com.otakuexchange.infra.tables.parimutuel.StakeTable
import com.otakuexchange.testutil.PostgresTestDb
import com.otakuexchange.testutil.Seed
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.and
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
import kotlin.uuid.Uuid

@Testcontainers(disabledWithoutDocker = true)
class NeonStakeRepositoryTest {
    private val repo = NeonStakeRepository()

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
        private val eventId = Uuid.parse("00000000-0000-0000-0000-000000000002")
        private val user1Id = Uuid.parse("00000000-0000-0000-0000-000000000010")
        private val user2Id = Uuid.parse("00000000-0000-0000-0000-000000000011")
        private val entityId = Uuid.parse("00000000-0000-0000-0000-000000000020")
        private val poolWithEntityId = Uuid.parse("00000000-0000-0000-0000-000000000100")
        private val poolNoEntityId = Uuid.parse("00000000-0000-0000-0000-000000000101")

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
        Seed.entity(db, id = entityId, name = "Team A")
        Seed.marketPool(db, id = poolWithEntityId, eventId = eventId, entityId = entityId, label = "Team A")
        Seed.marketPool(db, id = poolNoEntityId, eventId = eventId, entityId = null, label = "Other")
    }

    @Test
    fun addToStake_insertsAndIncrementsPoolAmount() = runTest {
        val stake = repo.addToStake(poolWithEntityId, user1Id, 100)
        assertEquals(100, stake.amount)

        val poolAmount = Seed.poolAmount(db, poolWithEntityId)
        assertEquals(100, poolAmount)
    }

    @Test
    fun addToStake_upsertsForSameUserAndPool() = runTest {
        val first = repo.addToStake(poolWithEntityId, user1Id, 100)
        val second = repo.addToStake(poolWithEntityId, user1Id, 50)

        assertEquals(first.id, second.id)
        assertEquals(150, second.amount)
        assertEquals(150, Seed.poolAmount(db, poolWithEntityId))

        val rows = transaction(db) {
            StakeTable.selectAll()
                .where { (StakeTable.userId eq user1Id) and (StakeTable.marketPoolId eq poolWithEntityId) }
                .toList()
        }
        assertEquals(1, rows.size)
    }

    @Test
    fun findByUserAndPool_returnsStakeOrNull() = runTest {
        assertNull(repo.findByUserAndPool(user1Id, poolWithEntityId))
        repo.addToStake(poolWithEntityId, user1Id, 10)
        assertNotNull(repo.findByUserAndPool(user1Id, poolWithEntityId))
    }

    @Test
    fun getByMarketPoolId_returnsAllStakesForPool() = runTest {
        repo.addToStake(poolWithEntityId, user1Id, 10)
        repo.addToStake(poolWithEntityId, user2Id, 20)

        val stakes = repo.getByMarketPoolId(poolWithEntityId)
        assertEquals(2, stakes.size)
        assertEquals(setOf(user1Id, user2Id), stakes.map { it.userId }.toSet())
    }

    @Test
    fun getByUserId_enrichesWithPoolLabelAndOptionalEntity() = runTest {
        repo.addToStake(poolWithEntityId, user1Id, 10)
        repo.addToStake(poolNoEntityId, user1Id, 5)

        val stakes = repo.getByUserId(user1Id)
        assertEquals(2, stakes.size)

        val withEntity = stakes.single { it.marketPoolId == poolWithEntityId }
        assertEquals("Team A", withEntity.label)
        assertNotNull(withEntity.entity)

        val withoutEntity = stakes.single { it.marketPoolId == poolNoEntityId }
        assertEquals("Other", withoutEntity.label)
        assertNull(withoutEntity.entity)
    }

    @Test
    fun delete_removesStakeRow() = runTest {
        val stake = repo.addToStake(poolWithEntityId, user1Id, 10)
        val deleted = repo.delete(stake.id)
        assertEquals(true, deleted)

        val stillThere = transaction(db) {
            StakeTable.selectAll().where { StakeTable.id eq stake.id }.singleOrNull()
        }
        assertNull(stillThere)

        val pool = transaction(db) {
            MarketPoolTable.selectAll().where { MarketPoolTable.id eq poolWithEntityId }.single()
        }
        // delete does not adjust pool totals (current repo behavior)
        assertEquals(10, pool[MarketPoolTable.amount])
    }
}

