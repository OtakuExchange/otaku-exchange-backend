package com.otakuexchange.application.controllers

import com.otakuexchange.domain.rank.WalletRankEntry
import com.otakuexchange.domain.repositories.IRankRepository
import com.otakuexchange.testutil.testApp
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RankControllerTest {

    private val rankRepo = mockk<IRankRepository>()

    private fun controller() = RankController(rankRepo)

    @BeforeEach
    fun setUp() = clearAllMocks()

    @Test
    fun getWalletLeaderboard_defaultLimit() {
        coEvery { rankRepo.getWalletLeaderboard(100) } returns listOf(
            WalletRankEntry(rank = 1, userId = Uuid.parse("00000000-0000-0000-0000-000000000001"), username = "top", balance = 5000)
        )
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/rank/wallet")
            assertEquals(HttpStatusCode.OK, res.status)
            assertTrue(res.bodyAsText().contains("\"username\":\"top\""))
        }
    }

    @Test
    fun getWalletLeaderboard_customLimit() {
        coEvery { rankRepo.getWalletLeaderboard(10) } returns emptyList()
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/rank/wallet?limit=10")
            assertEquals(HttpStatusCode.OK, res.status)
        }
    }

    @Test
    fun getWalletLeaderboard_limitClamped() {
        coEvery { rankRepo.getWalletLeaderboard(500) } returns emptyList()
        val c = controller()
        testApp(publicRoutes = { c.registerRoutes(this) }) { client ->
            val res = client.get("/rank/wallet?limit=9999")
            assertEquals(HttpStatusCode.OK, res.status)
            coVerify { rankRepo.getWalletLeaderboard(500) }
        }
    }
}
