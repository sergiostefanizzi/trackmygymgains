package com.trackmygymgains.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.testing.REFERENCE_SHEET_NAME
import com.trackmygymgains.testing.referencePlanExpected
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PlanDaoDeviceTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PlanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = PlanRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun countRows(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun importReferencePlanAndReadItBack() = runBlocking {
        val id = repo.importPlan(ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList()), "scheda.xlsx")

        val plan = repo.getActivePlan()!!
        assertEquals(id, plan.id)
        assertEquals(REFERENCE_SHEET_NAME, plan.sheetName)
        assertEquals(referencePlanExpected.map { it.name }, plan.days.map { it.name })
        assertEquals(
            referencePlanExpected.map { day -> day.exercises.map { it.name } },
            plan.days.map { day -> day.exercises.map { it.name } },
        )
        val firstExercise = plan.days.first().exercises.first()
        assertEquals("PULL UP", firstExercise.name)
        assertEquals(3, firstExercise.sets)
        assertEquals("4-6", firstExercise.targetReps)
        assertEquals("1-2", firstExercise.targetRir)
        assertEquals(180, firstExercise.restSeconds)
        assertNull(plan.days[0].exercises[2].notes)
        assertNull(plan.days[2].exercises[0].restSeconds)
    }

    @Test
    fun deletingPlanCascadesToDaysAndExercises() = runBlocking {
        val id = repo.importPlan(ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList()), null)
        assertEquals(3, countRows("workout_day"))
        assertEquals(referencePlanExpected.sumOf { it.exercises.size }, countRows("planned_exercise"))

        db.planDao().deletePlan(id)

        assertEquals(0, countRows("workout_plan"))
        assertEquals(0, countRows("workout_day"))
        assertEquals(0, countRows("planned_exercise"))
    }
}
