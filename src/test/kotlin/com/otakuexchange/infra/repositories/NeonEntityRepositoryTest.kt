package com.otakuexchange.infra.repositories

import com.otakuexchange.domain.market.Entity
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

@Testcontainers(disabledWithoutDocker = true)
class NeonEntityRepositoryTest {
    private val repo = NeonEntityRepository()

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
    fun save_getById_getAll_roundTrip() = runTest {
        val id1 = Uuid.parse("00000000-0000-0000-0000-000000000100")
        val id2 = Uuid.parse("00000000-0000-0000-0000-000000000101")

        repo.save(Entity(id = id1, name = "Team A", logoPath = "a.png", abbreviatedName = "A"))
        repo.save(Entity(id = id2, name = "Team B", logoPath = "b.png"))

        assertNotNull(repo.getById(id1))
        assertNull(repo.getById(Uuid.parse("00000000-0000-0000-0000-000000000102")))

        val all = repo.getAll()
        assertEquals(setOf(id1, id2), all.map { it.id }.toSet())
    }
}

