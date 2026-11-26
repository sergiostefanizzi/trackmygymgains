package com.trackmygymgains.ui.workout

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WorkoutViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository
    private lateinit var sessions: SessionRepository
    private lateinit var exercises: List<PlannedExercise>
    private var dayId = 0L

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        plans = PlanRepository(db)
        sessions = SessionRepository(db)
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

    private suspend fun ready(vm: WorkoutViewModel, predicate: (WorkoutUiState.Ready) -> Boolean = { true }) =
        vm.state.first { it is WorkoutUiState.Ready && predicate(it) } as WorkoutUiState.Ready

    @Test
    fun readyShowsDayExercisesInOrder() = runTest {
        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans)

        val state = ready(vm)

        assertEquals("FULL BODY 1", state.dayName)
        assertEquals(exercises.map { it.name }, state.exercises.map { it.exercise.name })
        assertEquals(7, state.exercises.size)
        val first = state.exercises.first()
        assertEquals(1, first.currentSet)
        assertEquals(SetFormValues("", "", SetOutcome.BUFFER, 1), first.currentForm)
        assertNull(first.editingSet)
    }

    @Test
    fun unknownSessionIsNotFound() = runTest {
        val vm = WorkoutViewModel(999, sessions, plans)
        assertEquals(WorkoutUiState.NotFound, vm.state.first { it !is WorkoutUiState.Loading })
    }

    @Test
    fun completingSetsSavesBufferAndFailure() = runTest {
        val sessionId = sessions.startSession(dayId)
        val vm = WorkoutViewModel(sessionId, sessions, plans)
        val ex = exercises.first()

        vm.completeSet(ex.id, 1, 82.5, 6, SetOutcome.BUFFER, 2)
        vm.completeSet(ex.id, 2, 80.0, 5, SetOutcome.FAILURE, 3) // rir must be dropped for FAILURE
        val state = ready(vm) { it.exercises.first().loggedSets.size == 2 }

        val sets = sessions.observeSession(sessionId).first()!!.sets
        assertEquals(2, sets.size)
        assertEquals(Triple(82.5, 6, SetOutcome.BUFFER), Triple(sets[0].loadKg, sets[0].reps, sets[0].outcome))
        assertEquals(2, sets[0].rir)
        assertEquals(Triple(80.0, 5, SetOutcome.FAILURE), Triple(sets[1].loadKg, sets[1].reps, sets[1].outcome))
        assertNull(sets[1].rir)
        assertEquals(3, state.exercises.first().currentSet)
    }

    @Test
    fun editingCompletedSetUpdatesIt() = runTest {
        val sessionId = sessions.startSession(dayId)
        val vm = WorkoutViewModel(sessionId, sessions, plans)
        val ex = exercises.first()
        vm.completeSet(ex.id, 1, 80.0, 6, SetOutcome.BUFFER, 2)
        ready(vm) { it.exercises.first().loggedSets.size == 1 }
        val originalId = sessions.observeSession(sessionId).first()!!.sets.single().id

        vm.startEdit(ex.id, 1)
        assertEquals(1, ready(vm) { it.exercises.first().editingSet == 1 }.exercises.first().editingSet)
        vm.completeSet(ex.id, 1, 85.0, 7, SetOutcome.FAILURE, null)
        val state = ready(vm) { it.exercises.first().loggedSets.singleOrNull()?.reps == 7 }

        val set = state.exercises.first().loggedSets.single()
        assertEquals(originalId, set.id)
        assertEquals(85.0, set.loadKg, 0.0)
        assertEquals(SetOutcome.FAILURE, set.outcome)
        assertNull(set.rir)
        assertNull(state.exercises.first().editingSet)
    }

    @Test
    fun prefillFromPreviousSetInSession() = runTest {
        val sessionId = sessions.startSession(dayId)
        val vm = WorkoutViewModel(sessionId, sessions, plans)
        val ex = exercises.first()

        vm.completeSet(ex.id, 1, 82.5, 6, SetOutcome.BUFFER, 2)
        val state = ready(vm) { it.exercises.first().currentSet == 2 }

        assertEquals(SetFormValues("82,5", "6", SetOutcome.BUFFER, 2), state.exercises.first().currentForm)
        // other exercises are untouched
        assertEquals(SetFormValues("", "", SetOutcome.BUFFER, 1), state.exercises[1].currentForm)
    }

    @Test
    fun prefillFromLastFinishedPerformanceMatchedByName() = runTest {
        val ex = exercises.first()
        val old = sessions.startSession(dayId)
        sessions.logSet(old, ex.id, 1, 10.0, 6, SetOutcome.BUFFER, 1)
        sessions.logSet(old, ex.id, 2, 12.5, 5, SetOutcome.FAILURE, null)
        sessions.finishSession(old)

        // Re-import: exercises get new ids, so the match must be by name.
        plans.importPlan(ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList()), "scheda.xlsx", "Nuova")
        val newDay = plans.getActivePlan()!!.days.first()
        val newEx = newDay.exercises.first()
        val vm = WorkoutViewModel(sessions.startSession(newDay.id), sessions, plans)

        val state = ready(vm)
        assertEquals(SetFormValues("10", "6", SetOutcome.BUFFER, 1), state.exercises.first().currentForm)

        vm.completeSet(newEx.id, 1, 11.0, 6, SetOutcome.BUFFER, 1)
        val next = ready(vm) { it.exercises.first().currentSet == 2 }
        // previous set in this session wins over last performance set 2
        assertEquals(SetFormValues("11", "6", SetOutcome.BUFFER, 1), next.exercises.first().currentForm)
    }

    @Test
    fun finishingClosesSessionAndEmitsItsId() = runTest {
        val sessionId = sessions.startSession(dayId)
        val vm = WorkoutViewModel(sessionId, sessions, plans)

        vm.finishWorkout()

        assertEquals(sessionId, vm.sessionFinished.first())
        assertNull(sessions.getOpenSession())
        assertNotNull(sessions.observeSession(sessionId).first()!!.endedAt)
    }

    @Test
    fun unfinishedSessionIsNotUsedAsHistory() = runTest {
        val ex = exercises.first()
        val other = sessions.startSession(dayId)
        sessions.logSet(other, ex.id, 1, 10.0, 6, SetOutcome.BUFFER, 1)
        // other session stays open: cannot start a second one, so check via a discarded-then-new flow
        sessions.discardSession(other)
        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans)

        assertEquals(SetFormValues("", "", SetOutcome.BUFFER, 1), ready(vm).exercises.first().currentForm)
    }
}
