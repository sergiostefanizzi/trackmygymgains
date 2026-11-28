package com.trackmygymgains.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-1-2-test"

    @Test
    fun migrateV1ToV2KeepsPlanAndAddsSessionTables() = runBlocking {
        // Version 1 database with one plan, one day and one exercise (layout of schemas/.../1.json).
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO workout_plan (id, name, sourceFileName, sheetName, importedAt, isActive) " +
                    "VALUES (1, 'Scheda', 'scheda.xlsx', 'FULL BODY SHEET', 1000, 1)",
            )
            execSQL("INSERT INTO workout_day (id, planId, name, position) VALUES (1, 1, 'FULL BODY 1', 0)")
            execSQL(
                "INSERT INTO planned_exercise (id, dayId, position, name, sets, targetReps, targetRir, restSeconds, notes) " +
                    "VALUES (1, 1, 0, 'PULL UP', 3, '4-6', '1-2', 180, NULL)",
            )
            close()
        }

        // Runs the v1 -> v2 auto-migration and validates the result against 2.json.
        helper.runMigrationsAndValidate(dbName, 2, true).close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        try {
            val plan = PlanRepository(db).getActivePlan()!!
            assertEquals("Scheda", plan.name)
            assertEquals(1, plan.days.size)
            val exercise = plan.days[0].exercises.single()
            assertEquals("PULL UP", exercise.name)
            assertEquals(180, exercise.restSeconds)

            // The new tables are usable on the migrated plan.
            val sessions = SessionRepository(db)
            val sessionId = sessions.startSession(plan.days[0].id)
            sessions.logSet(sessionId, exercise.id, 1, 37.5, 6, SetOutcome.BUFFER, 2)
            sessions.logSet(sessionId, exercise.id, 2, 40.0, 0, SetOutcome.FAILURE, null)
            sessions.finishSession(sessionId)

            val finished = sessions.getOpenSession()
            assertNull(finished)
            val stored = sessions.lastPerformance("pull up")
            assertEquals(listOf(1, 2), stored.map { it.setNumber })
            assertEquals(SetOutcome.FAILURE, stored[1].outcome)
            assertTrue(stored[0].rir == 2)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
