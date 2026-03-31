package com.otakuexchange.infra.repositories

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
import kotlin.uuid.Uuid

@Testcontainers(disabledWithoutDocker = true)
class NeonRankRepositoryTest {
    private val repo = NeonRankRepository()

    companion object {
        class KPostgres(imageName: DockerImageName) : PostgreSQLContainer<KPostgres>(imageName)

        @Container
        @JvmField
        val postgres = KPostgres(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("otaku_exchange_test")
            .withUsername("test")
            .withPassword("test")

        private lateinit var db: Database

        private val topicId = Uuid.parse("00000000-0000-0000-0000-000000040000")
        private val eventId = Uuid.parse("00000000-0000-0000-0000-000000040001")
        private val entityId = Uuid.parse("00000000-0000-0000-0000-000000040002")
        private val poolId = Uuid.parse("00000000-0000-0000-0000-000000040010")

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
        Seed.entity(db, id = entityId, name = "Team A")
        Seed.marketPool(db, id = poolId, eventId = eventId, entityId = entityId, label = "Team A")
    }

    @Test
    fun getWalletLeaderboard_sumsStakeIntoBalance_sortsAndRanks_excludesAdmins() = runTest {
        val u1 = Uuid.parse("00000000-0000-0000-0000-000000040100") // 100 + staked 50 => 150
        val u2 = Uuid.parse("00000000-0000-0000-0000-000000040101") // 120 + staked 0  => 120
        val u3 = Uuid.parse("00000000-0000-0000-0000-000000040102") // 10  + staked 200 => 210
        val admin = Uuid.parse("00000000-0000-0000-0000-000000040103") // excluded

        Seed.user(db, id = u1, username = "u1", email = "u1@example.com", balance = 100, isAdmin = false)
        Seed.user(db, id = u2, username = "u2", email = "u2@example.com", balance = 120, isAdmin = false)
        Seed.user(db, id = u3, username = "u3", email = "u3@example.com", balance = 10, isAdmin = false)
        Seed.user(db, id = admin, username = "admin", email = "admin@example.com", balance = 9999, isAdmin = true)

        Seed.stakeRow(db, id = Uuid.parse("00000000-0000-0000-0000-000000040200"), userId = u1, marketPoolId = poolId, amount = 50)
        Seed.stakeRow(db, id = Uuid.parse("00000000-0000-0000-0000-000000040201"), userId = u3, marketPoolId = poolId, amount = 200)

        val leaderboard = repo.getWalletLeaderboard(limit = 10)
        assertEquals(listOf(u3, u1, u2), leaderboard.map { it.userId })
        assertEquals(listOf(1, 2, 3), leaderboard.map { it.rank })
        assertEquals(listOf(210L, 150L, 120L), leaderboard.map { it.balance })
    }
}

