package com.trackmygymgains.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.data.db.AppDatabase
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: PlanRepository
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PlanRepository(db)
        sessions = SessionRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun importReference(name: String = REFERENCE_SHEET_NAME) {
        repo.importPlan(ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList()), "scheda.xlsx", name)
    }

    private suspend fun firstDayId(): Long = repo.getActivePlan()!!.days.first().id

    @Test
    fun emitsNoPlanThenContentAfterImport() = runTest {
        val vm = HomeViewModel(repo, sessions)
        assertEquals(HomeUiState.NoPlan, vm.state.first { it !is HomeUiState.Loading })

        importReference()
        val content = vm.state.first { it is HomeUiState.Content } as HomeUiState.Content
        assertEquals(REFERENCE_SHEET_NAME, content.plan.name)
        assertEquals(referencePlanExpected.map { it.name }, content.plan.days.map { it.name })
        assertNull(content.openSession)
    }

    @Test
    fun startCreatesSessionAndEmitsItsId() = runTest {
        importReference()
        val vm = HomeViewModel(repo, sessions)
        val dayId = firstDayId()

        vm.startWorkout(dayId)
        val sessionId = vm.sessionStarted.first()

        val open = sessions.getOpenSession()!!
        assertEquals(sessionId, open.id)
        assertEquals(dayId, open.dayId)
    }

    @Test
    fun openSessionShowsDayNameAndSurvivesReimport() = runTest {
        importReference()
        val vm = HomeViewModel(repo, sessions)
        vm.startWorkout(firstDayId())
        val sessionId = vm.sessionStarted.first()

        importReference(name = "Scheda nuova")

        val content = vm.state.first { it is HomeUiState.Content && it.openSession != null } as HomeUiState.Content
        assertEquals("Scheda nuova", content.plan.name)
        assertEquals(sessionId, content.openSession!!.sessionId)
        assertEquals(referencePlanExpected.first().name, content.openSession!!.dayName)
    }

    @Test
    fun discardRemovesOpenSession() = runTest {
        importReference()
        val vm = HomeViewModel(repo, sessions)
        vm.startWorkout(firstDayId())
        val sessionId = vm.sessionStarted.first()
        vm.state.first { it is HomeUiState.Content && it.openSession != null }

        vm.cancelSession(sessionId)

        val content = vm.state.first { it is HomeUiState.Content && it.openSession == null } as HomeUiState.Content
        assertNull(content.openSession)
        assertNull(sessions.getOpenSession())
    }

    @Test
    fun startIsRejectedWhileSessionIsOpen() = runTest {
        importReference()
        val vm = HomeViewModel(repo, sessions)
        val days = repo.getActivePlan()!!.days
        vm.startWorkout(days[0].id)
        val firstId = vm.sessionStarted.first()
        vm.state.first { it is HomeUiState.Content && it.openSession != null }

        vm.startWorkout(days[1].id)

        assertEquals(firstId, sessions.getOpenSession()!!.id)
        assertEquals(days[0].id, sessions.getOpenSession()!!.dayId)
    }
}
