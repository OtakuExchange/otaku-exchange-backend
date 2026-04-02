package com.otakuexchange.domain.services

import com.otakuexchange.domain.event.EventWithBookmark
import com.otakuexchange.domain.parimutuel.MarketPool
import com.otakuexchange.domain.parimutuel.Stake
import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.repositories.parimutuel.IMarketPoolRepository
import com.otakuexchange.domain.repositories.parimutuel.IStakeRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ParimutuelServiceTest {

    private val stakeRepo = mockk<IStakeRepository>()
    private val poolRepo  = mockk<IMarketPoolRepository>()
    private val eventRepo = mockk<IEventRepository>()
    private val userRepo  = mockk<IUserRepository>()
    private lateinit var service: ParimutuelService

    private val userId  = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val eventId = Uuid.parse("00000000-0000-0000-0000-000000000010")
    private val poolAId = Uuid.parse("00000000-0000-0000-0000-000000000100")
    private val poolBId = Uuid.parse("00000000-0000-0000-0000-000000000101")
    private val now     = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val SEED = ParimutuelService.POOL_SEED_AMOUNT

    // Pool amounts must exceed SEED (50000) so formula tests don't hit the edge case
    private val poolA = MarketPool(id = poolAId, eventId = eventId, label = "A", amount = 80000, createdAt = now, updatedAt = now)
    private val poolB = MarketPool(id = poolBId, eventId = eventId, label = "B", amount = 70000, createdAt = now, updatedAt = now)

    private fun openEvent() = EventWithBookmark(
        id = eventId, topicId = Uuid.parse("00000000-0000-0000-0000-000000000020"),
        format = "single", name = "E", description = "d", closeTime = now,
        status = "open", resolutionRule = "r", bookmarked = false
    )

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        service = ParimutuelService(stakeRepo, poolRepo, eventRepo, userRepo)
    }

    // ── placeStake ──────────────────────────────────────────────────────────

    @Test
    fun placeStake_happyPath() = runTest {
        coEvery { poolRepo.getById(poolAId) } returns poolA
        coEvery { eventRepo.getById(eventId, null) } returns openEvent()
        coEvery { userRepo.hasBalance(userId, 100L) } returns true
        coEvery { userRepo.subtractBalance(userId, 100L) } returns mockk()
        val expected = Stake(userId = userId, marketPoolId = poolAId, amount = 100)
        coEvery { stakeRepo.addToStake(poolAId, userId, 100) } returns expected

        val result = service.placeStake(userId, poolAId, 100)
        assertEquals(100, result.amount)

        coVerify { userRepo.subtractBalance(userId, 100L) }
        coVerify { stakeRepo.addToStake(poolAId, userId, 100) }
    }

    @Test
    fun placeStake_insufficientBalance_throws() = runTest {
        coEvery { poolRepo.getById(poolAId) } returns poolA
        coEvery { eventRepo.getById(eventId, null) } returns openEvent()
        coEvery { userRepo.hasBalance(userId, 500L) } returns false

        val ex = assertFailsWith<IllegalStateException> {
            service.placeStake(userId, poolAId, 500)
        }
        assertEquals("Insufficient balance", ex.message)
        coVerify(exactly = 0) { userRepo.subtractBalance(any(), any()) }
        coVerify(exactly = 0) { stakeRepo.addToStake(any(), any(), any()) }
    }

    @Test
    fun placeStake_poolNotFound_throws() = runTest {
        coEvery { poolRepo.getById(poolAId) } returns null

        assertFailsWith<IllegalStateException> {
            service.placeStake(userId, poolAId, 100)
        }
    }

    @Test
    fun placeStake_eventNotOpen_throws() = runTest {
        val closedEvent = openEvent().copy(status = "staking_closed")
        coEvery { poolRepo.getById(poolAId) } returns poolA
        coEvery { eventRepo.getById(eventId, null) } returns closedEvent

        assertFailsWith<IllegalStateException> {
            service.placeStake(userId, poolAId, 100)
        }
    }

    // ── getPayoutPreview ────────────────────────────────────────────────────

    @Test
    fun getPayoutPreview_formulaCheck() = runTest {
        coEvery { poolRepo.getByEventId(eventId) } returns listOf(poolA, poolB)

        // hypothetical 100 into poolA (80000), poolB (70000)
        // newPoolTotal (user only) = (80000 + 100) - SEED
        // newGrandTotal (user only) = (80000 + 70000 + 100) - SEED
        val newPoolTotal  = (poolA.amount + 100) - SEED
        val newGrandTotal = (poolA.amount + poolB.amount + 100) - SEED
        val expected = (100.0 / newPoolTotal * newGrandTotal).toInt()

        val payout = service.getPayoutPreview(eventId, poolAId, 100)
        assertEquals(expected, payout)
    }

    @Test
    fun getPayoutPreview_emptyPool_returnsAmount() = runTest {
        val emptyPool = poolA.copy(amount = 0)
        coEvery { poolRepo.getByEventId(eventId) } returns listOf(emptyPool, poolB)

        // newPoolTotal = (0 + 100) - SEED < 0 → edge case returns hypotheticalAmount
        val payout = service.getPayoutPreview(eventId, poolAId, 100)
        assertEquals(100, payout)
    }

    @Test
    fun getPayoutPreview_poolNotInEvent_throws() = runTest {
        coEvery { poolRepo.getByEventId(eventId) } returns listOf(poolB)

        assertFailsWith<IllegalStateException> {
            service.getPayoutPreview(eventId, poolAId, 100)
        }
    }

    // ── getCurrentPayout ────────────────────────────────────────────────────

    @Test
    fun getCurrentPayout_noStake_returnsZero() = runTest {
        coEvery { poolRepo.getById(poolAId) } returns poolA
        coEvery { stakeRepo.findByUserAndPool(userId, poolAId) } returns null

        assertEquals(0, service.getCurrentPayout(userId, poolAId))
    }

    @Test
    fun getCurrentPayout_seedOnlyPool_returnsZero() = runTest {
        // Pool has exactly SEED amount — no real user stakes, userPoolTotal = 0
        val seedOnlyPool = poolA.copy(amount = SEED)
        coEvery { poolRepo.getById(poolAId) } returns seedOnlyPool
        coEvery { stakeRepo.findByUserAndPool(userId, poolAId) } returns Stake(userId = userId, marketPoolId = poolAId, amount = 50)
        coEvery { poolRepo.getByEventId(eventId) } returns listOf(seedOnlyPool, poolB)

        assertEquals(0, service.getCurrentPayout(userId, poolAId))
    }

    @Test
    fun getCurrentPayout_formulaCheck() = runTest {
        coEvery { poolRepo.getById(poolAId) } returns poolA
        coEvery { stakeRepo.findByUserAndPool(userId, poolAId) } returns Stake(userId = userId, marketPoolId = poolAId, amount = 100)
        coEvery { poolRepo.getByEventId(eventId) } returns listOf(poolA, poolB)

        // stake=100, poolA=80000, grandTotal=150000
        // share = 100 / (80000 - SEED), payout = share * (150000 - SEED)
        val grandTotal = poolA.amount + poolB.amount
        val expected = (100.0 / (poolA.amount - SEED) * (grandTotal - SEED)).toInt()
        assertEquals(expected, service.getCurrentPayout(userId, poolAId))
    }

    // ── resolveEvent ────────────────────────────────────────────────────────

    @Test
    fun resolveEvent_poolNotInEvent_throws() = runTest {
        coEvery { poolRepo.getByEventId(eventId) } returns listOf(poolB)

        assertFailsWith<IllegalArgumentException> {
            service.resolveEvent(eventId, poolAId)
        }
    }

    @Test
    fun resolveEvent_paysOutWinnersProportionally() = runTest {
        val user1 = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val user2 = Uuid.parse("00000000-0000-0000-0000-000000000002")
        val stakes = listOf(
            Stake(userId = user1, marketPoolId = poolAId, amount = 100),
            Stake(userId = user2, marketPoolId = poolAId, amount = 100)
        )

        coEvery { poolRepo.getByEventId(eventId) } returns listOf(poolA, poolB)
        coEvery { stakeRepo.getByMarketPoolId(poolAId) } returns stakes
        coEvery { poolRepo.markWinner(poolAId) } returns poolA.copy(isWinner = true)
        coEvery { eventRepo.getById(eventId, null) } returns openEvent()
        coEvery { eventRepo.update(any()) } returns mockk()
        coEvery { userRepo.addBalance(any(), any()) } returns mockk()

        service.resolveEvent(eventId, poolAId)

        // grandTotal=150000, poolA=80000, each stake=100
        // share = 100 / (80000 - SEED), payout = share * (150000 - SEED)
        val grandTotal = poolA.amount + poolB.amount
        val expectedPayout = (100.0 / (poolA.amount - SEED) * (grandTotal - SEED)).toLong()
        coVerify { poolRepo.markWinner(poolAId) }
        coVerify { userRepo.addBalance(user1, expectedPayout) }
        coVerify { userRepo.addBalance(user2, expectedPayout) }
    }
}