package com.trackmygymgains.ui.summary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.PlannedExercise
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

/** Clock whose time is set by the test. */
private class TestClock(var millis: Long) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(millis)
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SummaryViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository
    private lateinit var exercises: List<PlannedExercise>
    private var dayId = 0L

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        plans = PlanRepository(db)
        plans.importPlan(ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList()), "scheda.xlsx")
        val day = plans.getActivePlan()!!.days.first()
        dayId = day.id
        exercises = day.exercises
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun summaryShowsTotalsDurationAndLoggedSetsPerExercise() = runTest {
        val clock = TestClock(1_000_000L)
        val sessions = SessionRepository(db, clock)
        val sessionId = sessions.startSession(dayId)
        val first = exercises.first()
        sessions.logSet(sessionId, first.id, 1, 82.5, 6, SetOutcome.BUFFER, 2)
        sessions.logSet(sessionId, first.id, 2, 80.0, 5, SetOutcome.FAILURE, null)
        clock.millis += 48 * 60_000L + 30_000L
        sessions.finishSession(sessionId)

        val state = SummaryViewModel(sessionId, sessions, plans).state
            .first { it is SummaryUiState.Ready } as SummaryUiState.Ready

        assertEquals("FULL BODY 1", state.dayName)
        assertEquals(48 * 60_000L + 30_000L, state.durationMillis)
        assertEquals(exercises.sumOf { it.sets }, state.plannedSets)
        assertEquals(2, state.doneSets)
        assertEquals(exercises.map { it.name }, state.exercises.map { it.exercise.name })
        val firstSummary = state.exercises.first()
        assertEquals(listOf(1, 2), firstSummary.loggedSets.map { it.setNumber })
        assertEquals(Triple(82.5, 6, SetOutcome.BUFFER), firstSummary.loggedSets[0].let { Triple(it.loadKg, it.reps, it.outcome) })
        assertEquals(2, firstSummary.loggedSets[0].rir)
        assertEquals(SetOutcome.FAILURE, firstSummary.loggedSets[1].outcome)
        assertTrue(state.exercises.drop(1).all { it.loggedSets.isEmpty() })
    }

    @Test
    fun openOrUnknownSessionIsNotFound() = runTest {
        val open = SessionRepository(db).startSession(dayId)

        assertEquals(SummaryUiState.NotFound, SummaryViewModel(open, SessionRepository(db), plans).state.first { it !is SummaryUiState.Loading })
        assertEquals(SummaryUiState.NotFound, SummaryViewModel(999, SessionRepository(db), plans).state.first { it !is SummaryUiState.Loading })
    }
}
