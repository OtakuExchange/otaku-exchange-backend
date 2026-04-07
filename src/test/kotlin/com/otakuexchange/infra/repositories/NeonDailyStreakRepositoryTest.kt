package com.otakuexchange.repositories

import com.otakuexchange.infra.repositories.NeonDailyStreakRepository
import com.otakuexchange.infra.tables.DailyStreakTable
import com.otakuexchange.infra.tables.UserTable
import com.otakuexchange.testutil.PostgresTestDb
import com.otakuexchange.testutil.Seed
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Testcontainers(disabledWithoutDocker = true)
class NeonDailyStreakRepositoryTest {
    private val repo = NeonDailyStreakRepository()

    companion object {
        class KPostgres(imageName: DockerImageName) : PostgreSQLContainer<KPostgres>(imageName)

        @Container
        @JvmField
        val postgres = KPostgres(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("otaku_exchange_test")
            .withUsername("test")
            .withPassword("test")

        private lateinit var db: Database

        private val userId = Uuid.parse("00000000-0000-0000-0000-000000000001")

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
        Seed.user(db, userId, username = "user1", email = "user1@example.com")
    }

    @Test
    fun getStatus_whenMissingRow_returnsDefaultClaimableStatus() = runTest {
        val status = repo.getStatus(userId)
        assertEquals(0, status.streak)
        assertEquals(true, status.canClaim)
        assertEquals(10000L, status.rewardCents)
    }

    @Test
    fun getStatus_whenClaimedToday_isNotClaimable_andKeepsStreak() = runTest {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        Seed.dailyStreak(db, userId, streak = 3, lastClaim = today)

        val status = repo.getStatus(userId)
        assertEquals(3, status.streak)
        assertEquals(false, status.canClaim)
        assertEquals(40000L, status.rewardCents)
    }

    @Test
    fun getStatus_whenClaimedYesterday_isClaimable_andKeepsStreak() = runTest {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        Seed.dailyStreak(db, userId, streak = 2, lastClaim = yesterday)

        val status = repo.getStatus(userId)
        assertEquals(2, status.streak)
        assertEquals(true, status.canClaim)
        assertEquals(30000L, status.rewardCents)
    }

    @Test
    fun getStatus_whenLastClaimBeforeYesterday_resetsStreakToZero() = runTest {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val twoDaysAgo = today.minus(2, DateTimeUnit.DAY)
        Seed.dailyStreak(db, userId, streak = 5, lastClaim = twoDaysAgo)

        val status = repo.getStatus(userId)
        assertEquals(0, status.streak)
        assertEquals(true, status.canClaim)
        assertEquals(10000L, status.rewardCents)
    }

    @Test
    fun claim_whenMissingRow_createsRow_andCreditsUserBalance() = runTest {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val before = transaction(db) {
            UserTable.selectAll().where { UserTable.id eq userId }.single()[UserTable.balance]
        }

        val status = repo.claim(userId)
        assertEquals(1, status.streak)
        assertEquals(false, status.canClaim)
        assertEquals(10000L, status.rewardCents)

        val row = transaction(db) {
            DailyStreakTable.selectAll().where { DailyStreakTable.userId eq userId }.single()
        }
        assertEquals(1, row[DailyStreakTable.streak])
        assertEquals(today, row[DailyStreakTable.lastClaim])

        val after = transaction(db) {
            UserTable.selectAll().where { UserTable.id eq userId }.single()[UserTable.balance]
        }
        assertEquals(before + 10000L, after)
    }

    @Test
    fun claim_whenClaimedYesterday_incrementsStreak_andCreditsRewardForPreviousStreak() = runTest {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        Seed.dailyStreak(db, userId, streak = 2, lastClaim = yesterday)

        val before = transaction(db) {
            UserTable.selectAll().where { UserTable.id eq userId }.single()[UserTable.balance]
        }

        val status = repo.claim(userId)
        assertEquals(3, status.streak)
        assertEquals(false, status.canClaim)
        assertEquals(30000L, status.rewardCents)

        val row = transaction(db) {
            DailyStreakTable.selectAll().where { DailyStreakTable.userId eq userId }.single()
        }
        assertEquals(3, row[DailyStreakTable.streak])
        assertEquals(today, row[DailyStreakTable.lastClaim])

        val after = transaction(db) {
            UserTable.selectAll().where { UserTable.id eq userId }.single()[UserTable.balance]
        }
        assertEquals(before + 30000L, after)
    }

    @Test
    fun claim_whenAlreadyClaimedToday_throws_andDoesNotCreditBalance() = runTest {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        Seed.dailyStreak(db, userId, streak = 2, lastClaim = today)

        val before = transaction(db) {
            UserTable.selectAll().where { UserTable.id eq userId }.single()[UserTable.balance]
        }

        val err = runCatching { repo.claim(userId) }.exceptionOrNull()
        assertNotNull(err)
        assertTrue(err is IllegalStateException)

        val after = transaction(db) {
            UserTable.selectAll().where { UserTable.id eq userId }.single()[UserTable.balance]
        }
        assertEquals(before, after)
    }
}