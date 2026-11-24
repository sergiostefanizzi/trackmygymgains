package com.trackmygymgains.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.plan.ParsedDay
import com.trackmygymgains.data.plan.ParsedExercise
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.testing.REFERENCE_SHEET_NAME
import com.trackmygymgains.testing.referencePlanExpected
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class PlanRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PlanRepository
    private val fixedInstant = Instant.parse("2026-11-24T10:15:30Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PlanRepository(db, Clock.fixed(fixedInstant, ZoneOffset.UTC))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val otherPlan = ParsedPlan(
        "OTHER SHEET",
        listOf(ParsedDay("UPPER", listOf(ParsedExercise("SQUAT", 5, "5", "2", null, null)))),
        emptyList(),
    )

    private fun referenceParsed() = ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList())

    private fun WorkoutPlan.asParsedDays(): List<ParsedDay> = days.map { d ->
        ParsedDay(
            d.name,
            d.exercises.map { e ->
                ParsedExercise(e.name, e.sets, e.targetReps, e.targetRir, e.restSeconds, e.notes)
            },
        )
    }

    private fun countRows(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun importRoundTripsReferencePlanExactly() = runTest {
        val id = repo.importPlan(referenceParsed(), sourceFileName = "scheda.xlsx")

        val plan = repo.getActivePlan()!!
        assertEquals(id, plan.id)
        assertEquals(REFERENCE_SHEET_NAME, plan.name)
        assertEquals(REFERENCE_SHEET_NAME, plan.sheetName)
        assertEquals("scheda.xlsx", plan.sourceFileName)
        assertEquals(fixedInstant.toEpochMilli(), plan.importedAt)
        assertEquals(referencePlanExpected, plan.asParsedDays())
        assertEquals(listOf(0, 1, 2), plan.days.map { it.position })
        plan.days.forEach { day ->
            assertEquals(day.exercises.indices.toList(), day.exercises.map { it.position })
        }
        // Null rest (jump set) and null notes survive the round trip.
        assertNull(plan.days[2].exercises[0].restSeconds)
        assertNull(plan.days[0].exercises[2].notes)
    }

    @Test
    fun importPlanDefaultsNameToSheetName() = runTest {
        repo.importPlan(otherPlan, sourceFileName = null)
        assertEquals("OTHER SHEET", repo.getActivePlan()!!.name)
        assertNull(repo.getActivePlan()!!.sourceFileName)
    }

    @Test
    fun secondImportBecomesOnlyActiveAndFirstIsKept() = runTest {
        val firstId = repo.importPlan(referenceParsed(), sourceFileName = "a.xlsx")
        val secondId = repo.importPlan(otherPlan, sourceFileName = "b.xlsx", planName = "Scheda nuova")

        val active = repo.getActivePlan()!!
        assertEquals(secondId, active.id)
        assertEquals("Scheda nuova", active.name)
        assertEquals(listOf(ParsedDay("UPPER", listOf(ParsedExercise("SQUAT", 5, "5", "2", null, null)))), active.asParsedDays())

        // The first plan still exists, deactivated, with its days and exercises.
        assertEquals(2, countRows("workout_plan"))
        val isActiveFirst = db.openHelper.readableDatabase
            .query("SELECT isActive FROM workout_plan WHERE id = $firstId").use {
                it.moveToFirst()
                it.getInt(0)
            }
        assertEquals(0, isActiveFirst)
        assertEquals(3 + 1, countRows("workout_day"))
        assertEquals(referencePlanExpected.sumOf { it.exercises.size } + 1, countRows("planned_exercise"))
    }

    @Test
    fun observeActivePlanEmitsNullThenPlan() = runTest {
        assertNull(repo.observeActivePlan().first())

        repo.importPlan(referenceParsed(), sourceFileName = null)
        val emitted = repo.observeActivePlan().first { it != null }
        assertEquals(referencePlanExpected, emitted!!.asParsedDays())
    }

    @Test
    fun observeHasActivePlanReflectsImport() = runTest {
        assertFalse(repo.observeHasActivePlan().first())

        repo.importPlan(referenceParsed(), sourceFileName = null)
        assertTrue(repo.observeHasActivePlan().first { it })
    }

    @Test
    fun failedImportRollsBackAndKeepsPreviousPlanActive() = runTest {
        val firstId = repo.importPlan(referenceParsed(), sourceFileName = "a.xlsx")

        // A trigger that aborts the insert of one specific exercise, i.e. midway through the import
        // (after the new plan row and its first day were already written).
        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_on_boom BEFORE INSERT ON planned_exercise
            WHEN NEW.name = 'BOOM'
            BEGIN SELECT RAISE(ABORT, 'boom'); END
            """.trimIndent(),
        )
        val broken = ParsedPlan(
            "BROKEN",
            listOf(
                ParsedDay("DAY A", listOf(ParsedExercise("SQUAT", 5, "5", "2", 180, null))),
                ParsedDay("DAY B", listOf(ParsedExercise("BOOM", 3, "8", null, null, null))),
            ),
            emptyList(),
        )

        assertTrue(runCatching { repo.importPlan(broken, sourceFileName = "broken.xlsx") }.isFailure)

        val active = repo.getActivePlan()!!
        assertEquals(firstId, active.id)
        assertEquals(referencePlanExpected, active.asParsedDays())
        assertEquals(1, countRows("workout_plan"))
        assertEquals(referencePlanExpected.size, countRows("workout_day"))
        assertEquals(referencePlanExpected.sumOf { it.exercises.size }, countRows("planned_exercise"))
    }
}
