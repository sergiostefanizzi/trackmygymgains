package com.trackmygymgains.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.plan.ParsedDay
import com.trackmygymgains.data.plan.ParsedExercise
import com.trackmygymgains.data.plan.ParsedHistoryExercise
import com.trackmygymgains.data.plan.ParsedHistorySession
import com.trackmygymgains.data.plan.ParsedHistorySet
import com.trackmygymgains.data.plan.ParsedPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class HistoryImportTest {

    private val rome = ZoneId.of("Europe/Rome")
    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        // 2026-11-24 10:00Z = 11:00 in Rome.
        val clock = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), rome)
        plans = PlanRepository(db, clock, rome)
        sessions = SessionRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val days = listOf(
        ParsedDay(
            "FULL BODY 1",
            listOf(
                ParsedExercise("PULL UP", 3, "4-6", "1-2", 180, null),
                ParsedExercise("SQUAT", 3, "5", "2", 120, null),
            ),
        ),
        ParsedDay("FULL BODY 2", listOf(ParsedExercise("PULL UP", 3, "4-6", "1-2", 180, null))),
    )

    private fun hist(dayIndex: Int, date: String, vararg ex: ParsedHistoryExercise) =
        ParsedHistorySession(dayIndex, 1, LocalDate.parse(date), ex.toList())

    private val pullUpSets = listOf(
        ParsedHistorySet(40.0, 6, SetOutcome.BUFFER, 2),
        ParsedHistorySet(40.0, 5, SetOutcome.BUFFER, null),
        ParsedHistorySet(40.0, 4, SetOutcome.FAILURE, null),
    )

    private fun plan(history: List<ParsedHistorySession>) = ParsedPlan("SHEET", days, emptyList(), history)

    private fun sampleHistory() = listOf(
        hist(
            0, "2026-11-10",
            ParsedHistoryExercise(0, pullUpSets, "ok"),
            ParsedHistoryExercise(1, emptyList(), "saltato"),
        ),
        hist(1, "2026-11-12", ParsedHistoryExercise(0, listOf(ParsedHistorySet(30.0, 8, SetOutcome.BUFFER, 1)), null)),
    )

    @Test
    fun importsSessionsSetsNotesAndFlag() = runTest {
        val result = plans.importPlan(plan(sampleHistory()), "a.xlsx")
        assertEquals(2, result.importedSessions)
        assertEquals(0, result.skippedDuplicates)

        val finished = sessions.observeFinishedSessions().first()
        assertEquals(2, finished.size)
        val active = plans.getActivePlan()!!
        val s = finished.last() // oldest = 2026-11-10
        assertTrue(s.imported)
        assertEquals(result.planId, s.planId)
        assertEquals(active.days[0].id, s.dayId)
        val noon = LocalDate.parse("2026-11-10").atTime(12, 0).atZone(rome).toInstant().toEpochMilli()
        assertEquals(noon, s.startedAt)
        assertEquals(noon, s.endedAt)
        val pullUp = active.days[0].exercises[0]
        val squat = active.days[0].exercises[1]
        assertEquals(listOf(1, 2, 3), s.sets.map { it.setNumber })
        assertTrue(s.sets.all { it.plannedExerciseId == pullUp.id && it.completedAt == noon })
        assertEquals(listOf<Int?>(2, null, null), s.sets.map { it.rir })
        assertEquals(SetOutcome.FAILURE, s.sets[2].outcome)
        assertEquals(mapOf(pullUp.id to "ok", squat.id to "saltato"), s.notes)

        val other = finished.first()
        assertEquals(active.days[1].id, other.dayId)
        assertTrue(other.notes.isEmpty())
    }

    @Test
    fun reimportingSameParsedPlanSkipsEverything() = runTest {
        plans.importPlan(plan(sampleHistory()), "a.xlsx")
        val again = plans.importPlan(plan(sampleHistory()), "a.xlsx")
        assertEquals(0, again.importedSessions)
        assertEquals(2, again.skippedDuplicates)
        assertEquals(2, sessions.observeFinishedSessions().first().size)
    }

    @Test
    fun duplicatesInsideTheSameFileAreSkipped() = runTest {
        val h = hist(0, "2026-11-10", ParsedHistoryExercise(0, pullUpSets, null))
        val result = plans.importPlan(plan(listOf(h, h.copy(week = 2))), null)
        assertEquals(1, result.importedSessions)
        assertEquals(1, result.skippedDuplicates)
    }

    @Test
    fun appLoggedFinishedSessionOnSameDayAndDateIsSkipped() = runTest {
        plans.importPlan(plan(emptyList()), null)
        val first = plans.getActivePlan()!!
        val id = sessions.startSession(first.days[0].id) // 2026-11-24 in Rome, "FULL BODY 1"
        sessions.finishSession(id)

        val result = plans.importPlan(
            plan(listOf(hist(0, "2026-11-24", ParsedHistoryExercise(0, pullUpSets, null)))),
            null,
        )
        assertEquals(0, result.importedSessions)
        assertEquals(1, result.skippedDuplicates)
        // Name match is trimmed and case-insensitive.
        val renamed = ParsedPlan(
            "S2",
            listOf(days[0].copy(name = " full body 1 ")),
            emptyList(),
            listOf(hist(0, "2026-11-24", ParsedHistoryExercise(0, pullUpSets, null))),
        )
        assertEquals(1, plans.importPlan(renamed, null).skippedDuplicates)
    }

    @Test
    fun differentDayNameOnSameDateIsImported() = runTest {
        plans.importPlan(plan(emptyList()), null)
        val id = sessions.startSession(plans.getActivePlan()!!.days[0].id)
        sessions.finishSession(id)

        val result = plans.importPlan(
            plan(listOf(hist(1, "2026-11-24", ParsedHistoryExercise(0, pullUpSets, null)))),
            null,
        )
        assertEquals(1, result.importedSessions)
        assertEquals(0, result.skippedDuplicates)
    }

    @Test
    fun openSessionIsNotADuplicate() = runTest {
        plans.importPlan(plan(emptyList()), null)
        sessions.startSession(plans.getActivePlan()!!.days[0].id) // left open

        val result = plans.importPlan(
            plan(listOf(hist(0, "2026-11-24", ParsedHistoryExercise(0, pullUpSets, null)))),
            null,
        )
        assertEquals(1, result.importedSessions)
    }

    @Test
    fun localDateIsComparedInTheConfiguredZone() = runTest {
        // App session at 2026-11-24T23:30Z is already 2026-11-25 in Rome.
        val lateClock = Clock.fixed(Instant.parse("2026-11-24T23:30:00Z"), rome)
        val p = PlanRepository(db, lateClock, rome)
        val s = SessionRepository(db, lateClock)
        p.importPlan(plan(emptyList()), null)
        s.finishSession(s.startSession(p.getActivePlan()!!.days[0].id))

        val r25 = p.importPlan(plan(listOf(hist(0, "2026-11-25", ParsedHistoryExercise(0, pullUpSets, null)))), null)
        assertEquals(1, r25.skippedDuplicates)
        val r24 = p.importPlan(plan(listOf(hist(0, "2026-11-24", ParsedHistoryExercise(0, pullUpSets, null)))), null)
        assertEquals(1, r24.importedSessions)
    }

    @Test
    fun planWithoutHistoryImportsAsBefore() = runTest {
        val first = plans.importPlan(plan(emptyList()), null)
        val second = plans.importPlan(plan(sampleHistory()), null)
        assertEquals(0, first.importedSessions)
        assertEquals(0, first.skippedDuplicates)
        assertEquals(second.planId, plans.getActivePlan()!!.id)
        assertEquals(2, countRows("workout_plan"))
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM workout_plan WHERE isActive = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun importedHistoryFeedsLastPerformanceAndNewerAppSessionWins() = runTest {
        plans.importPlan(plan(sampleHistory()), null)
        val imported = sessions.lastPerformance("pull up")
        // Most recent imported pull up is FB2 on 2026-11-12: one set of 30 kg.
        assertEquals(1, imported.size)
        assertEquals(30.0, imported[0].loadKg, 0.0)

        val active = plans.getActivePlan()!!
        val id = sessions.startSession(active.days[0].id)
        sessions.logSet(id, active.days[0].exercises[0].id, 1, 50.0, 5, SetOutcome.BUFFER, 2)
        sessions.finishSession(id)
        val after = sessions.lastPerformance("PULL UP")
        assertEquals(listOf(50.0), after.map { it.loadKg })
    }

    @Test
    fun olderAppSessionDoesNotBeatNewerImportedHistory() = runTest {
        plans.importPlan(plan(emptyList()), null)
        val p = plans.getActivePlan()!!
        val id = sessions.startSession(p.days[0].id) // 2026-11-24
        sessions.logSet(id, p.days[0].exercises[0].id, 1, 50.0, 5, SetOutcome.BUFFER, 2)
        sessions.finishSession(id)
        plans.importPlan(plan(sampleHistory()), null) // imported dates are older: app session stays most recent
        assertEquals(listOf(50.0), sessions.lastPerformance("pull up").map { it.loadKg })
    }

    @Test
    fun deletingFinishedSessionRemovesItsNotes() = runTest {
        plans.importPlan(plan(sampleHistory()), null)
        assertEquals(2, countRows("exercise_note"))
        val target = sessions.observeFinishedSessions().first().last()
        sessions.deleteFinishedSession(target.id)
        assertEquals(0, countRows("exercise_note"))
        assertEquals(1, sessions.observeFinishedSessions().first().size)
        assertFalse(sessions.observeFinishedSessions().first().any { it.id == target.id })
        assertNull(sessions.observeSession(target.id).first())
    }

    private fun countRows(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
