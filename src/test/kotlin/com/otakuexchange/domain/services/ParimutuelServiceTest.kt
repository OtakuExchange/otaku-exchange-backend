package com.otakuexchange.domain.services

import com.otakuexchange.domain.event.EventStatus
import com.otakuexchange.domain.event.EventWithBookmark
import com.otakuexchange.domain.parimutuel.MarketPool
import com.otakuexchange.domain.parimutuel.Stake
import com.otakuexchange.domain.repositories.IEventRepository
import com.otakuexchange.domain.repositories.IUserRepository
import com.otakuexchange.domain.repositories.parimutuel.IFirstStakeBonusRepository
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

    private val stakeRepo           = mockk<IStakeRepository>()
    private val poolRepo            = mockk<IMarketPoolRepository>()
    private val eventRepo           = mockk<IEventRepository>()
    private val userRepo            = mockk<IUserRepository>()
    private val firstStakeBonusRepo = mockk<IFirstStakeBonusRepository>()
    private lateinit var service: ParimutuelService

    private val userId  = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val eventId = Uuid.parse("00000000-0000-0000-0000-000000000010")
    private val poolAId = Uuid.parse("00000000-0000-0000-0000-000000000100")
    private val poolBId = Uuid.parse("00000000-0000-0000-0000-000000000101")
    private val now     = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val poolA = MarketPool(id = poolAId, eventId = eventId, label = "A", amount = 200, createdAt = now, updatedAt = now)
    private val poolB = MarketPool(id = poolBId, eventId = eventId, label = "B", amount = 300, createdAt = now, updatedAt = now)

    private fun openEvent() = EventWithBookmark(
        id = eventId, topicId = Uuid.parse("00000000-0000-0000-0000-000000000020"),
        format = "single", name = "E", description = "d", closeTime = now,
        status = EventStatus.open, resolutionRule = "r", bookmarked = false
    )

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        service = ParimutuelService(stakeRepo, poolRepo, eventRepo, userRepo, firstStakeBonusRepo)
        coEvery { firstStakeBonusRepo.hasBonus(any(), any()) } returns true  // no bonus by default
        coEvery { firstStakeBonusRepo.recordBonus(any(), any(), any()) } just Runs
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
    fun placeStake_firstStakeBonus_doublesUpToCap() = runTest {
        coEvery { firstStakeBonusRepo.hasBonus(userId, eventId) } returns false
        coEvery { poolRepo.getById(poolAId) } returns poolA
        coEvery { eventRepo.getById(eventId, null) } returns openEvent()
        coEvery { userRepo.hasBalance(userId, 100L) } returns true
        coEvery { userRepo.subtractBalance(userId, 100L) } returns mockk()
        val expected = Stake(userId = userId, marketPoolId = poolAId, amount = 200)
        coEvery { stakeRepo.addToStake(poolAId, userId, 200) } returns expected

        val result = service.placeStake(userId, poolAId, 100)
        assertEquals(200, result.amount)
        coVerify { firstStakeBonusRepo.recordBonus(userId, eventId, 100) }
        coVerify { stakeRepo.addToStake(poolAId, userId, 200) }
    }

    @Test
    fun placeStake_firstStakeBonus_cappedAt50000() = runTest {
        coEvery { firstStakeBonusRepo.hasBonus(userId, eventId) } returns false
        coEvery { poolRepo.getById(poolAId) } returns poolA
        coEvery { eventRepo.getById(eventId, null) } returns openEvent()
        coEvery { userRepo.hasBalance(userId, 100000L) } returns true
        coEvery { userRepo.subtractBalance(userId, 100000L) } returns mockk()
        val expected = Stake(userId = userId, marketPoolId = poolAId, amount = 150000)
        coEvery { stakeRepo.addToStake(poolAId, userId, 150000) } returns expected

        val result = service.placeStake(userId, poolAId, 100000)
        assertEquals(150000, result.amount)
        coVerify { firstStakeBonusRepo.recordBonus(userId, eventId, 50000) }
    }

    @Test
    fun placeStake_notFirstStake_noBonus() = runTest {
        coEvery { firstStakeBonusRepo.hasBonus(userId, eventId) } returns true
        coEvery { poolRepo.getById(poolAId) } returns poolA
        coEvery { eventRepo.getById(eventId, null) } returns openEvent()
        coEvery { userRepo.hasBalance(userId, 100L) } returns true
        coEvery { userRepo.subtractBalance(userId, 100L) } returns mockk()
        val expected = Stake(userId = userId, marketPoolId = poolAId, amount = 100)
        coEvery { stakeRepo.addToStake(poolAId, userId, 100) } returns expected

        val result = service.placeStake(userId, poolAId, 100)
        assertEquals(100, result.amount)
        coVerify(exactly = 0) { firstStakeBonusRepo.recordBonus(any(), any(), any()) }
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
        val closedEvent = openEvent().copy(status = EventStatus.staking_closed)
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
        coEvery { eventRepo.getEventMultiplier(eventId) } returns 1

        // hypothetical 100 into poolA (200): newPoolTotal=300, newGrandTotal=600
        // payout = (100/300)*600 = 200, profit=100, return 100 + 100*1 = 200
        val payout = service.getPayoutPreview(eventId, poolAId, 100)
        assertEquals(200, payout)
    }

    @Test
    fun getPayoutPreview_multiplier2_doublesProfit() = runTest {
        coEvery { poolRepo.getByEventId(eventId) } returns listOf(poolA, poolB)
        coEvery { eventRepo.getEventMultiplier(eventId) } returns 2

        // payout = (100/300)*600 = 200, profit=100, return 100 + 100*2 = 300
        val payout = service.getPayoutPreview(eventId, poolAId, 100)
        assertEquals(300, payout)
    }

    @Test
    fun getPayoutPreview_emptyPool_returnsBasePayout() = runTest {
        val emptyPool = poolA.copy(amount = 0)
        coEvery { poolRepo.getByEventId(eventId) } returns listOf(emptyPool, poolB)
        coEvery { eventRepo.getEventMultiplier(eventId) } returns 1

        // newPoolTotal=100, newGrandTotal=400
        // payout = (100/100)*400 = 400, profit=300, return 100 + 300*1 = 400
        val payout = service.getPayoutPreview(eventId, poolAId, 100)
        assertEquals(400, payout)
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
    fun getCurrentPayout_zeroPoolTotal_returnsZero() = runTest {
        val zeroPool = poolA.copy(amount = 0)
        coEvery { poolRepo.getById(poolAId) } returns zeroPool
        coEvery { stakeRepo.findByUserAndPool(userId, poolAId) } returns Stake(userId = userId, marketPoolId = poolAId, amount = 50)
        coEvery { poolRepo.getByEventId(eventId) } returns listOf(zeroPool, poolB)
        coEvery { eventRepo.getEventMultiplier(eventId) } returns 1

        assertEquals(0, service.getCurrentPayout(userId, poolAId))
    }

    @Test
    fun getCurrentPayout_formulaCheck() = runTest {
        coEvery { poolRepo.getById(poolAId) } returns poolA
        coEvery { stakeRepo.findByUserAndPool(userId, poolAId) } returns Stake(userId = userId, marketPoolId = poolAId, amount = 100)
        coEvery { poolRepo.getByEventId(eventId) } returns listOf(poolA, poolB)
        coEvery { eventRepo.getEventMultiplier(eventId) } returns 1

        // stake=100, pool=200, grandTotal=500 → (100/200)*500=250, profit=150, return 100+150=250
        assertEquals(250, service.getCurrentPayout(userId, poolAId))
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
        coEvery { eventRepo.getEventMultiplier(eventId) } returns 1
        coEvery { userRepo.addBalance(any(), any()) } returns mockk()

        service.resolveEvent(eventId, poolAId)

        // grandTotal=500, poolA=200, each stake=100
        // payout=(100/200)*500=250, profit=150, return 100+150*1=250
        coVerify { poolRepo.markWinner(poolAId) }
        coVerify { userRepo.addBalance(user1, 250L) }
        coVerify { userRepo.addBalance(user2, 250L) }
    }
}