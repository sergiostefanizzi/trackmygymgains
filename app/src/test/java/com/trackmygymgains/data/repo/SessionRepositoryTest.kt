package com.trackmygymgains.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.testing.REFERENCE_SHEET_NAME
import com.trackmygymgains.testing.referencePlanExpected
import com.trackmygymgains.data.plan.ParsedPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** A clock whose instant the test moves forward explicitly. */
private class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
    fun advanceMillis(ms: Long) {
        now = now.plusMillis(ms)
    }
}

@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository
    private lateinit var sessions: SessionRepository
    private lateinit var clock: MutableClock

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableClock(Instant.parse("2026-11-24T10:00:00Z"))
        plans = PlanRepository(db, clock)
        sessions = SessionRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun referenceParsed() = ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList())

    /** Imports the reference plan and returns its (planId, first day id, exercise ids of day 0 by name). */
    private suspend fun importReference(): WorkoutPlan {
        plans.importPlan(referenceParsed(), "scheda.xlsx")
        return plans.getActivePlan()!!
    }

    private fun WorkoutPlan.exerciseId(name: String): Long =
        days.flatMap { it.exercises }.first { it.name == name }.id

    private fun countRows(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun startFinishLifecycle() = runTest {
        val plan = importReference()
        val dayId = plan.days[0].id

        assertNull(sessions.getOpenSession())
        val id = sessions.startSession(dayId)

        val open = sessions.getOpenSession()!!
        assertEquals(id, open.id)
        assertEquals(plan.id, open.planId)
        assertEquals(dayId, open.dayId)
        assertEquals(clock.now.toEpochMilli(), open.startedAt)
        assertNull(open.endedAt)
        assertEquals(id, sessions.observeOpenSession().first()!!.id)

        clock.advanceMillis(60_000)
        sessions.finishSession(id)
        assertNull(sessions.getOpenSession())
        assertNull(sessions.observeOpenSession().first())
        val finished = sessions.observeSession(id).first()!!
        assertEquals(clock.now.toEpochMilli(), finished.endedAt)

        // Finishing again keeps the original end time.
        clock.advanceMillis(60_000)
        sessions.finishSession(id)
        assertEquals(clock.now.toEpochMilli() - 60_000, sessions.observeSession(id).first()!!.endedAt)
    }

    @Test
    fun startingSecondSessionWhileOpenThrows() = runTest {
        val plan = importReference()
        sessions.startSession(plan.days[0].id)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { sessions.startSession(plan.days[1].id) }
        }
        assertEquals(1, countRows("workout_session"))
    }

    @Test
    fun startAfterFinishIsAllowed() = runTest {
        val plan = importReference()
        val first = sessions.startSession(plan.days[0].id)
        sessions.finishSession(first)
        val second = sessions.startSession(plan.days[1].id)
        assertTrue(second != first)
        assertEquals(second, sessions.getOpenSession()!!.id)
    }

    @Test
    fun startWithUnknownDayThrows() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { sessions.startSession(12345L) }
        }
    }

    @Test
    fun logSetRoundTripsBufferAndFailure() = runTest {
        val plan = importReference()
        val pullUp = plan.exerciseId("PULL UP")
        val dayPullUp = plan.days[0].id
        val sessionId = sessions.startSession(dayPullUp)

        sessions.logSet(sessionId, pullUp, 1, 37.5, 6, SetOutcome.BUFFER, 2)
        sessions.logSet(sessionId, pullUp, 2, 40.0, 0, SetOutcome.FAILURE, null)

        val session = sessions.getOpenSession()!!
        assertEquals(2, session.sets.size)
        val buffer = session.sets[0]
        assertEquals(pullUp, buffer.plannedExerciseId)
        assertEquals(1, buffer.setNumber)
        assertEquals(37.5, buffer.loadKg, 0.0)
        assertEquals(6, buffer.reps)
        assertEquals(SetOutcome.BUFFER, buffer.outcome)
        assertEquals(2, buffer.rir)
        assertEquals(clock.now.toEpochMilli(), buffer.completedAt)

        val failure = session.sets[1]
        assertEquals(2, failure.setNumber)
        assertEquals(SetOutcome.FAILURE, failure.outcome)
        assertNull(failure.rir)
        assertEquals(0, failure.reps)
    }

    @Test
    fun logSetRejectsInvalidValues() = runTest {
        val plan = importReference()
        val pullUp = plan.exerciseId("PULL UP")
        val sessionId = sessions.startSession(plan.days[0].id)

        fun expectRejected(block: suspend () -> Unit) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { block() } }
        }

        expectRejected { sessions.logSet(sessionId, pullUp, 1, -0.5, 5, SetOutcome.BUFFER, 1) }
        expectRejected { sessions.logSet(sessionId, pullUp, 1, Double.NaN, 5, SetOutcome.BUFFER, 1) }
        expectRejected { sessions.logSet(sessionId, pullUp, 1, 10.0, -1, SetOutcome.BUFFER, 1) }
        expectRejected { sessions.logSet(sessionId, pullUp, 0, 10.0, 5, SetOutcome.BUFFER, 1) }
        expectRejected { sessions.logSet(sessionId, pullUp, 1, 10.0, 5, SetOutcome.BUFFER, null) }
        expectRejected { sessions.logSet(sessionId, pullUp, 1, 10.0, 5, SetOutcome.BUFFER, -1) }
        expectRejected { sessions.logSet(sessionId, pullUp, 1, 10.0, 5, SetOutcome.BUFFER, 11) }
        expectRejected { sessions.logSet(sessionId, pullUp, 1, 10.0, 0, SetOutcome.FAILURE, 0) }

        assertEquals(0, countRows("set_log"))
    }

    @Test
    fun zeroLoadAndZeroRepsAreAccepted() = runTest {
        val plan = importReference()
        val sessionId = sessions.startSession(plan.days[0].id)
        sessions.logSet(sessionId, plan.exerciseId("PULL UP"), 1, 0.0, 0, SetOutcome.FAILURE, null)
        assertEquals(1, sessions.getOpenSession()!!.sets.size)
    }

    @Test
    fun logSetRequiresOpenSession() = runTest {
        val plan = importReference()
        val pullUp = plan.exerciseId("PULL UP")
        val sessionId = sessions.startSession(plan.days[0].id)
        sessions.finishSession(sessionId)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { sessions.logSet(sessionId, pullUp, 1, 10.0, 5, SetOutcome.BUFFER, 1) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { sessions.logSet(999L, pullUp, 1, 10.0, 5, SetOutcome.BUFFER, 1) }
        }
    }

    @Test
    fun logSameSetNumberReplacesAndKeepsId() = runTest {
        val plan = importReference()
        val pullUp = plan.exerciseId("PULL UP")
        val sessionId = sessions.startSession(plan.days[0].id)

        val firstId = sessions.logSet(sessionId, pullUp, 1, 30.0, 8, SetOutcome.BUFFER, 3)
        clock.advanceMillis(5_000)
        val secondId = sessions.logSet(sessionId, pullUp, 1, 32.5, 6, SetOutcome.FAILURE, null)

        assertEquals(firstId, secondId)
        val sets = sessions.getOpenSession()!!.sets
        assertEquals(1, sets.size)
        assertEquals(32.5, sets[0].loadKg, 0.0)
        assertEquals(6, sets[0].reps)
        assertEquals(SetOutcome.FAILURE, sets[0].outcome)
        assertNull(sets[0].rir)
        assertEquals(clock.now.toEpochMilli(), sets[0].completedAt)
    }

    @Test
    fun deleteSetRemovesOnlyThatSet() = runTest {
        val plan = importReference()
        val pullUp = plan.exerciseId("PULL UP")
        val sessionId = sessions.startSession(plan.days[0].id)
        val keep = sessions.logSet(sessionId, pullUp, 1, 30.0, 8, SetOutcome.BUFFER, 3)
        val drop = sessions.logSet(sessionId, pullUp, 2, 30.0, 7, SetOutcome.BUFFER, 2)

        sessions.deleteSet(drop)

        val sets = sessions.getOpenSession()!!.sets
        assertEquals(listOf(keep), sets.map { it.id })
    }

    @Test
    fun discardSessionRemovesSessionAndSets() = runTest {
        val plan = importReference()
        val pullUp = plan.exerciseId("PULL UP")
        val sessionId = sessions.startSession(plan.days[0].id)
        sessions.logSet(sessionId, pullUp, 1, 30.0, 8, SetOutcome.BUFFER, 3)
        sessions.logSet(sessionId, pullUp, 2, 30.0, 7, SetOutcome.FAILURE, null)

        sessions.discardSession(sessionId)

        assertNull(sessions.getOpenSession())
        assertEquals(0, countRows("workout_session"))
        assertEquals(0, countRows("set_log"))
        // Discarding an unknown session is a no-op.
        sessions.discardSession(sessionId)
    }

    @Test
    fun discardFinishedSessionThrows() = runTest {
        val plan = importReference()
        val sessionId = sessions.startSession(plan.days[0].id)
        sessions.finishSession(sessionId)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { sessions.discardSession(sessionId) }
        }
        assertEquals(1, countRows("workout_session"))
    }

    @Test
    fun observeFinishedSessionsListsNewestFirstWithoutOpenSession() = runTest {
        val plan = importReference()
        val firstDayPullUp = plan.exerciseId("PULL UP")
        val secondDayBanded = plan.exerciseId("BANDED PULL UP")
        val secondDayCurl = plan.exerciseId("CURL MANUBRI")

        val first = sessions.startSession(plan.days[0].id)
        sessions.logSet(first, firstDayPullUp, 1, 30.0, 8, SetOutcome.BUFFER, 3)
        clock.advanceMillis(60_000)
        sessions.finishSession(first)

        clock.advanceMillis(3_600_000)
        val second = sessions.startSession(plan.days[1].id)
        sessions.logSet(second, secondDayCurl, 2, 12.0, 6, SetOutcome.FAILURE, null)
        sessions.logSet(second, secondDayBanded, 1, 30.0, 8, SetOutcome.BUFFER, 3)
        sessions.logSet(second, secondDayCurl, 1, 12.0, 8, SetOutcome.BUFFER, 2)
        clock.advanceMillis(120_000)
        sessions.finishSession(second)

        clock.advanceMillis(3_600_000)
        sessions.startSession(plan.days[2].id) // stays open

        val finished = sessions.observeFinishedSessions().first()
        assertEquals(listOf(second, first), finished.map { it.id })
        assertEquals(listOf(plan.days[1].id, plan.days[0].id), finished.map { it.dayId })
        // Sets come back ordered by exercise, then set number.
        assertEquals(
            listOf(secondDayBanded to 1, secondDayCurl to 1, secondDayCurl to 2),
            finished[0].sets.map { it.plannedExerciseId to it.setNumber },
        )
        assertEquals(1, finished[1].sets.size)
    }

    @Test
    fun deleteFinishedSessionRemovesSessionAndSetsOnly() = runTest {
        val plan = importReference()
        val pullUp = plan.exerciseId("PULL UP")
        val finishedId = sessions.startSession(plan.days[0].id)
        sessions.logSet(finishedId, pullUp, 1, 30.0, 8, SetOutcome.BUFFER, 3)
        sessions.logSet(finishedId, pullUp, 2, 30.0, 7, SetOutcome.FAILURE, null)
        sessions.finishSession(finishedId)
        val keptId = sessions.startSession(plan.days[0].id) // open, must survive
        sessions.logSet(keptId, pullUp, 1, 30.0, 8, SetOutcome.BUFFER, 3)
        sessions.finishSession(keptId)
        val openId = sessions.startSession(plan.days[1].id)
        sessions.logSet(openId, pullUp, 1, 30.0, 8, SetOutcome.BUFFER, 3)

        sessions.deleteFinishedSession(finishedId)

        assertEquals(listOf(keptId), sessions.observeFinishedSessions().first().map { it.id })
        assertEquals(2, countRows("workout_session"))
        assertEquals(2, countRows("set_log"))
        assertEquals(openId, sessions.getOpenSession()!!.id)
        // Unknown id is a no-op.
        sessions.deleteFinishedSession(finishedId)
        assertEquals(2, countRows("workout_session"))
    }

    @Test
    fun deleteOpenSessionThrows() = runTest {
        val plan = importReference()
        val sessionId = sessions.startSession(plan.days[0].id)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { sessions.deleteFinishedSession(sessionId) }
        }
        assertEquals(1, countRows("workout_session"))
    }

    @Test
    fun lastPerformancePicksMostRecentFinishedSession() = runTest {
        val plan = importReference()
        val pullUp = plan.exerciseId("PULL UP")

        val older = sessions.startSession(plan.days[0].id)
        sessions.logSet(older, pullUp, 1, 30.0, 8, SetOutcome.BUFFER, 3)
        sessions.finishSession(older)

        clock.advanceMillis(86_400_000)
        val newer = sessions.startSession(plan.days[0].id)
        sessions.logSet(newer, pullUp, 1, 35.0, 6, SetOutcome.BUFFER, 1)
        sessions.logSet(newer, pullUp, 2, 35.0, 5, SetOutcome.FAILURE, null)
        sessions.finishSession(newer)

        val last = sessions.lastPerformance("PULL UP")
        assertEquals(listOf(1, 2), last.map { it.setNumber })
        assertEquals(listOf(35.0, 35.0), last.map { it.loadKg })
    }

    @Test
    fun lastPerformanceIgnoresOpenSessionAndExcludedSession() = runTest {
        val plan = importReference()
        val pullUp = plan.exerciseId("PULL UP")

        // s1: finished, PULL UP 30 kg.
        val s1 = sessions.startSession(plan.days[0].id)
        sessions.logSet(s1, pullUp, 1, 30.0, 8, SetOutcome.BUFFER, 3)
        sessions.finishSession(s1)

        // s2: finished later, PULL UP 99 kg.
        clock.advanceMillis(1_000)
        val s2 = sessions.startSession(plan.days[1].id)
        sessions.logSet(s2, pullUp, 1, 99.0, 1, SetOutcome.BUFFER, 0)
        sessions.finishSession(s2)

        // s3: still open with PULL UP 50 kg; must never be returned.
        clock.advanceMillis(1_000)
        val s3 = sessions.startSession(plan.days[2].id)
        sessions.logSet(s3, pullUp, 1, 50.0, 2, SetOutcome.BUFFER, 0)

        assertEquals(99.0, sessions.lastPerformance("PULL UP")[0].loadKg, 0.0)
        assertEquals(99.0, sessions.lastPerformance("PULL UP", excludeSessionId = s3)[0].loadKg, 0.0)
        assertEquals(30.0, sessions.lastPerformance("PULL UP", excludeSessionId = s2)[0].loadKg, 0.0)
        assertEquals(99.0, sessions.lastPerformance("PULL UP", excludeSessionId = s1)[0].loadKg, 0.0)
    }

    @Test
    fun lastPerformanceFindsSessionAfterPlanReimport() = runTest {
        val oldPlan = importReference()
        val oldPullUp = oldPlan.exerciseId("PULL UP")
        val sessionId = sessions.startSession(oldPlan.days[0].id)
        sessions.logSet(sessionId, oldPullUp, 1, 40.0, 5, SetOutcome.BUFFER, 2)
        sessions.finishSession(sessionId)

        // Re-importing the same sheet creates new plan/day/exercise rows; the old session stays linked to the old plan.
        plans.importPlan(referenceParsed(), "scheda.xlsx")
        val newPlan = plans.getActivePlan()!!
        assertTrue(newPlan.exerciseId("PULL UP") != oldPullUp)

        val last = sessions.lastPerformance("PULL UP")
        assertEquals(1, last.size)
        assertEquals(40.0, last[0].loadKg, 0.0)
        assertEquals(oldPullUp, last[0].plannedExerciseId)
    }

    @Test
    fun lastPerformanceIsCaseInsensitiveAndTrimmed() = runTest {
        val plan = importReference()
        val sessionId = sessions.startSession(plan.days[0].id)
        sessions.logSet(sessionId, plan.exerciseId("PULL UP"), 1, 40.0, 5, SetOutcome.BUFFER, 2)
        sessions.finishSession(sessionId)

        assertEquals(1, sessions.lastPerformance("pull up").size)
        assertEquals(1, sessions.lastPerformance("  Pull Up ").size)
        assertTrue(sessions.lastPerformance("SQUAT").isEmpty())
    }

    @Test
    fun lastPerformanceEmptyWhenNothingFinished() = runTest {
        val plan = importReference()
        val sessionId = sessions.startSession(plan.days[0].id)
        sessions.logSet(sessionId, plan.exerciseId("PULL UP"), 1, 40.0, 5, SetOutcome.BUFFER, 2)
        assertTrue(sessions.lastPerformance("PULL UP").isEmpty())
    }
}
