package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.user.AuthProvider
import com.otakuexchange.domain.user.User
import com.otakuexchange.testutil.PostgresTestDb
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Testcontainers(disabledWithoutDocker = true)
class NeonUserRepositoryTest {
    private val repo = NeonUserRepository()

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
    fun save_andFindByIdEmailUsernameProvider() = runTest {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000010")
        val createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val user = User(
            id = id,
            username = "user1",
            email = "user1@example.com",
            authProvider = AuthProvider.CLERK,
            providerUserId = "clerk_123",
            balance = 1234L,
            avatarUrl = "https://example.com/a.png",
            createdAt = createdAt
        )
        repo.save(user)

        assertNotNull(repo.findById(id))
        assertNotNull(repo.findByEmail("user1@example.com"))
        assertNotNull(repo.findByUsername("user1"))
        assertNotNull(repo.findByProviderUserId("clerk_123", AuthProvider.CLERK))

        assertNull(repo.findById(Uuid.parse("00000000-0000-0000-0000-000000000011")))
        assertNull(repo.findByEmail("missing@example.com"))
        assertNull(repo.findByUsername("missing"))
        assertNull(repo.findByProviderUserId("missing", AuthProvider.CLERK))
    }

    @Test
    fun updateUsername_updatesRow() = runTest {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000020")
        repo.save(
            User(
                id = id,
                username = "old",
                email = "u2@example.com",
                authProvider = AuthProvider.CLERK
            )
        )

        val updated = repo.updateUsername(id, "new")
        assertEquals("new", updated.username)
        assertEquals("new", repo.findById(id)!!.username)
    }

    @Test
    fun updateAvatarUrl_updatesRow() = runTest {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000030")
        repo.save(
            User(
                id = id,
                username = "user3",
                email = "u3@example.com",
                authProvider = AuthProvider.CLERK
            )
        )

        repo.updateAvatarUrl(id, "https://example.com/avatar.png")
        assertEquals("https://example.com/avatar.png", repo.findById(id)!!.avatarUrl)
    }

    @Test
    fun addSubtractAndHasBalance_workAsExpected() = runTest {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000040")
        repo.save(
            User(
                id = id,
                username = "wallet",
                email = "wallet@example.com",
                authProvider = AuthProvider.CLERK,
                balance = 100L
            )
        )

        assertTrue(repo.hasBalance(id, 100L))
        assertFalse(repo.hasBalance(id, 101L))
        assertFalse(repo.hasBalance(Uuid.parse("00000000-0000-0000-0000-000000000041"), 1L))

        val added = repo.addBalance(id, 50L)
        assertEquals(150L, added.balance)

        val subtracted = repo.subtractBalance(id, 20L)
        assertEquals(130L, subtracted.balance)
        assertEquals(130L, repo.findById(id)!!.balance)
    }
}

