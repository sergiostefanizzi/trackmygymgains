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
import com.trackmygymgains.timer.InMemoryRestTimerStore
import com.trackmygymgains.timer.RestTimer
import com.trackmygymgains.timer.RestTimerState
import java.time.Clock
import kotlinx.coroutines.test.TestScope
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

    private fun TestScope.newTimer() = RestTimer(backgroundScope, InMemoryRestTimerStore(), Clock.systemUTC())

    private suspend fun ready(vm: WorkoutViewModel, predicate: (WorkoutUiState.Ready) -> Boolean = { true }) =
        vm.state.first { it is WorkoutUiState.Ready && predicate(it) } as WorkoutUiState.Ready

    @Test
    fun readyShowsDayExercisesInOrder() = runTest {
        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans, newTimer())

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
        val vm = WorkoutViewModel(999, sessions, plans, newTimer())
        assertEquals(WorkoutUiState.NotFound, vm.state.first { it !is WorkoutUiState.Loading })
    }

    @Test
    fun completingSetsSavesBufferAndFailure() = runTest {
        val sessionId = sessions.startSession(dayId)
        val vm = WorkoutViewModel(sessionId, sessions, plans, newTimer())
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
        val vm = WorkoutViewModel(sessionId, sessions, plans, newTimer())
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
        val vm = WorkoutViewModel(sessionId, sessions, plans, newTimer())
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
        val vm = WorkoutViewModel(sessions.startSession(newDay.id), sessions, plans, newTimer())

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
        val vm = WorkoutViewModel(sessionId, sessions, plans, newTimer())

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
        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans, newTimer())

        assertEquals(SetFormValues("", "", SetOutcome.BUFFER, 1), ready(vm).exercises.first().currentForm)
    }

    private fun RestTimer.running() = state.value as RestTimerState.Running

    @Test
    fun newSetStartsRestWithExerciseRestAndLabel() = runTest {
        val timer = newTimer()
        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans, timer)
        val ex = exercises.first() // PULL UP, 3'

        vm.completeSet(ex.id, 1, 80.0, 6, SetOutcome.BUFFER, 1).join()

        val s = timer.running()
        assertEquals(180, s.totalSeconds)
        assertEquals(ex.name, s.label)
    }

    @Test
    fun editingSetDoesNotRestartRest() = runTest {
        val timer = newTimer()
        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans, timer)
        val ex = exercises.first()
        vm.completeSet(ex.id, 1, 80.0, 6, SetOutcome.BUFFER, 1).join()
        timer.skip()

        vm.completeSet(ex.id, 1, 85.0, 7, SetOutcome.BUFFER, 1).join()

        assertEquals(RestTimerState.Idle, timer.state.value)
    }

    @Test
    fun jumpSetDoesNotAutoStartAndManualStartUsesSixtySeconds() = runTest {
        val timer = newTimer()
        val day3 = plans.getActivePlan()!!.days[2]
        val jump = day3.exercises.first { it.restSeconds == null }
        val vm = WorkoutViewModel(sessions.startSession(day3.id), sessions, plans, timer)

        vm.completeSet(jump.id, 1, 10.0, 8, SetOutcome.BUFFER, 1).join()
        assertEquals(RestTimerState.Idle, timer.state.value)

        vm.startManualRest(jump.name)
        val s = timer.running()
        assertEquals(60, s.totalSeconds)
        assertEquals(jump.name, s.label)
    }

    @Test
    fun lastSetOfTheDayDoesNotStartRest() = runTest {
        val timer = newTimer()
        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans, timer)
        val all = exercises.flatMap { ex -> (1..ex.sets).map { ex to it } }

        all.dropLast(1).forEach { (ex, n) -> vm.completeSet(ex.id, n, 50.0, 8, SetOutcome.BUFFER, 1).join() }
        assertEquals(exercises[exercises.size - 1].name, timer.running().label)
        val (lastEx, lastN) = all.last()
        vm.completeSet(lastEx.id, lastN, 50.0, 8, SetOutcome.BUFFER, 1).join()

        assertEquals(RestTimerState.Idle, timer.state.value)
    }

    @Test
    fun finishingWorkoutSkipsRest() = runTest {
        val timer = newTimer()
        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans, timer)
        vm.completeSet(exercises.first().id, 1, 80.0, 6, SetOutcome.BUFFER, 1).join()
        timer.running()

        vm.finishWorkout()

        assertEquals(RestTimerState.Idle, timer.state.value)
    }

    private suspend fun finishedSession(ex: PlannedExercise, reps: List<Int>, load: Double) {
        val id = sessions.startSession(dayId)
        reps.forEachIndexed { i, r -> sessions.logSet(id, ex.id, i + 1, load, r, SetOutcome.BUFFER, 1) }
        sessions.finishSession(id)
    }

    @Test
    fun exerciseCarriesLastPerformanceAndIncreaseSuggestion() = runTest {
        val ex = exercises.first { SetInput.repsUpperBound(it.targetReps) != null }
        val bound = SetInput.repsUpperBound(ex.targetReps)!!
        finishedSession(ex, List(ex.sets) { bound }, 40.0)

        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans, newTimer())
        val state = ready(vm)
        val ui = state.exercises.first { it.exercise.id == ex.id }

        assertNotNull(ui.lastPerformance)
        assertEquals(ex.sets, ui.lastPerformance!!.sets.size)
        assertEquals(true, ui.suggestIncrease)
        assertEquals("42,5", ui.currentForm!!.loadText)
        val other = state.exercises.first { it.exercise.name.trim().lowercase() != ex.name.trim().lowercase() }
        assertNull(other.lastPerformance)
        assertEquals(false, other.suggestIncrease)
    }

    @Test
    fun noSuggestionWhenAnySetBelowMax() = runTest {
        val ex = exercises.first { SetInput.repsUpperBound(it.targetReps) != null }
        val bound = SetInput.repsUpperBound(ex.targetReps)!!
        finishedSession(ex, List(ex.sets) { if (it == 0) bound - 1 else bound }, 40.0)

        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans, newTimer())
        val ui = ready(vm).exercises.first { it.exercise.id == ex.id }

        assertNotNull(ui.lastPerformance)
        assertEquals(false, ui.suggestIncrease)
        assertEquals("40", ui.currentForm!!.loadText)
    }

    @Test
    fun loggedSetInSessionKeepsItsLoadEvenWithSuggestion() = runTest {
        val ex = exercises.first { SetInput.repsUpperBound(it.targetReps) != null && it.sets >= 2 }
        val bound = SetInput.repsUpperBound(ex.targetReps)!!
        finishedSession(ex, List(ex.sets) { bound }, 40.0)
        val vm = WorkoutViewModel(sessions.startSession(dayId), sessions, plans, newTimer())

        vm.completeSet(ex.id, 1, 40.0, bound, SetOutcome.BUFFER, 1).join()
        val ui = ready(vm) { s -> s.exercises.first { it.exercise.id == ex.id }.loggedSets.size == 1 }
            .exercises.first { it.exercise.id == ex.id }

        assertEquals("40", ui.currentForm!!.loadText)
    }
}
