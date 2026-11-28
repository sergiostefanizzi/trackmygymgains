package com.trackmygymgains.ui.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.SessionRepository
import com.trackmygymgains.testing.REFERENCE_SHEET_NAME
import com.trackmygymgains.testing.referencePlanExpected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private class TestClock(var millis: Long) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(millis)
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository
    private lateinit var clock: TestClock
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        plans = PlanRepository(db)
        plans.importPlan(ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList()), "scheda.xlsx")
        clock = TestClock(1_000_000L)
        sessions = SessionRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun content(vm: HistoryViewModel): HistoryUiState.Content =
        vm.state.first { it is HistoryUiState.Content } as HistoryUiState.Content

    @Test
    fun listsFinishedSessionsNewestFirstWithDayDurationAndSets() = runTest {
        val plan = plans.getActivePlan()!!
        val day1 = plan.days[0]
        val day2 = plan.days[1]
        val planned1 = day1.exercises.sumOf { it.sets }
        val planned2 = day2.exercises.sumOf { it.sets }

        val first = sessions.startSession(day1.id)
        sessions.logSet(first, day1.exercises[0].id, 1, 80.0, 8, SetOutcome.BUFFER, 1)
        sessions.logSet(first, day1.exercises[0].id, 2, 80.0, 7, SetOutcome.FAILURE, null)
        clock.millis += 48 * 60_000L + 30_000L
        sessions.finishSession(first)

        clock.millis += 60_000L
        val second = sessions.startSession(day2.id)
        sessions.logSet(second, day2.exercises[0].id, 1, 40.0, 8, SetOutcome.BUFFER, 2)
        clock.millis += 65 * 60_000L
        sessions.finishSession(second)

        clock.millis += 60_000L
        sessions.startSession(day1.id) // open: not in the history

        val items = content(HistoryViewModel(plans, sessions)).items

        assertEquals(listOf(second, first), items.map { it.sessionId })
        val secondItem = items[0]
        assertEquals("FULL BODY 2", secondItem.dayName)
        assertEquals(65 * 60_000L, secondItem.durationMillis)
        assertEquals(1, secondItem.doneSets)
        assertEquals(planned2, secondItem.plannedSets)
        val firstItem = items[1]
        assertEquals("FULL BODY 1", firstItem.dayName)
        assertEquals(48 * 60_000L + 30_000L, firstItem.durationMillis)
        assertEquals(2, firstItem.doneSets)
        assertEquals(planned1, firstItem.plannedSets)
    }

    @Test
    fun emptyWhenNothingFinished() = runTest {
        sessions.startSession(plans.getActivePlan()!!.days[0].id)

        assertTrue(content(HistoryViewModel(plans, sessions)).items.isEmpty())
    }

    @Test
    fun deleteRemovesTheItemFromTheList() = runTest {
        val day = plans.getActivePlan()!!.days[0]
        val kept = sessions.startSession(day.id)
        sessions.finishSession(kept)
        clock.millis += 60_000L
        val deleted = sessions.startSession(day.id)
        sessions.finishSession(deleted)

        val vm = HistoryViewModel(plans, sessions)
        assertEquals(2, content(vm).items.size)

        vm.deleteSession(deleted)

        val after = vm.state.first { it is HistoryUiState.Content && it.items.size == 1 } as HistoryUiState.Content
        assertEquals(listOf(kept), after.items.map { it.sessionId })
    }
}
