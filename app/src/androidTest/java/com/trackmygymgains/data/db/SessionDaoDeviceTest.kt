package com.trackmygymgains.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.SessionRepository
import com.trackmygymgains.testing.REFERENCE_SHEET_NAME
import com.trackmygymgains.testing.referencePlanExpected
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionDaoDeviceTest {

    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        plans = PlanRepository(db)
        sessions = SessionRepository(db)
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
    fun logReadAndCascadeDeleteOfSessionSets() = runBlocking {
        plans.importPlan(ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList()), "scheda.xlsx")
        val plan = plans.getActivePlan()!!
        val pullUp = plan.days[0].exercises[0]

        val sessionId = sessions.startSession(plan.days[0].id)
        sessions.logSet(sessionId, pullUp.id, 1, 37.5, 6, SetOutcome.BUFFER, 2)
        sessions.logSet(sessionId, pullUp.id, 2, 40.0, 0, SetOutcome.FAILURE, null)

        val read = sessions.getOpenSession()!!
        assertEquals(sessionId, read.id)
        assertEquals(listOf(1, 2), read.sets.map { it.setNumber })
        assertEquals(SetOutcome.BUFFER, read.sets[0].outcome)
        assertEquals(2, read.sets[0].rir)
        assertNull(read.sets[1].rir)
        assertEquals(2, countRows("set_log"))

        // Deleting the session row removes its sets via the foreign key cascade.
        db.sessionDao().deleteSession(sessionId)
        assertEquals(0, countRows("workout_session"))
        assertEquals(0, countRows("set_log"))

        // Deleting the plan cascades through sessions to their sets.
        val secondSession = sessions.startSession(plan.days[0].id)
        sessions.logSet(secondSession, pullUp.id, 1, 30.0, 8, SetOutcome.BUFFER, 3)
        sessions.finishSession(secondSession)
        db.planDao().deletePlan(plan.id)
        assertEquals(0, countRows("workout_session"))
        assertEquals(0, countRows("set_log"))
    }
}
