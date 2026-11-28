package com.trackmygymgains.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.LastPerformance
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.PlannedExercise
import com.trackmygymgains.data.repo.SessionRepository
import com.trackmygymgains.timer.RestTimer
import com.trackmygymgains.timer.RestTimerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One exercise card of the logging screen. */
data class ExerciseUi(
    val exercise: PlannedExercise,
    /** Logged sets of this exercise ordered by setNumber. */
    val loggedSets: List<LoggedSet>,
    /** First set not yet logged (1-based), null when all sets are done. */
    val currentSet: Int?,
    /** Initial form values for [currentSet]. */
    val currentForm: SetFormValues?,
    /** Logged set currently open for editing, if any. */
    val editingSet: Int?,
    /** Most recent finished performance of this exercise (any plan), null when there is no history. */
    val lastPerformance: LastPerformance? = null,
    /** True when the last performance hit the top of the rep range in every planned set. */
    val suggestIncrease: Boolean = false,
)

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState

    data object NotFound : WorkoutUiState

    /** [dayName] is the name of the session's day (empty if the day no longer exists). */
    data class Ready(val dayName: String, val exercises: List<ExerciseUi>) : WorkoutUiState {
        /** Sets planned by the day (sum over its exercises). */
        val plannedSets: Int get() = exercises.sumOf { it.exercise.sets }

        /** Sets logged so far in the session. */
        val doneSets: Int get() = exercises.sumOf { it.loggedSets.size }
    }
}

class WorkoutViewModel(
    private val sessionId: Long,
    private val sessionRepository: SessionRepository,
    private val planRepository: PlanRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

    /** App-wide rest timer state, shown as a banner by the screen. */
    val restState: StateFlow<RestTimerState> = restTimer.state

    /** exerciseId -> set number open for editing. */
    private val editing = MutableStateFlow<Map<Long, Int>>(emptyMap())

    private val finished = Channel<Long>(Channel.BUFFERED)

    /** Id of the session once it has been finished; the route navigates to the summary once per emission. */
    val sessionFinished: Flow<Long> = finished.receiveAsFlow()

    /** Set to true on the first finish request so a double tap does not emit twice. */
    private var finishRequested = false

    val state: StateFlow<WorkoutUiState> = flow {
        val first = sessionRepository.observeSession(sessionId).first()
        if (first == null) {
            emit(WorkoutUiState.NotFound)
            return@flow
        }
        val day = planRepository.getDay(first.dayId)
        val exercises = day?.exercises.orEmpty()
        // Loaded once per exercise when the screen opens, not on every session update.
        val history = exercises.associate { it.id to sessionRepository.lastPerformanceWithDate(it.name, sessionId) }
        emitAll(
            combine(sessionRepository.observeSession(sessionId), editing) { session, editMap ->
                if (session == null) {
                    WorkoutUiState.NotFound
                } else {
                    WorkoutUiState.Ready(
                        dayName = day?.name.orEmpty(),
                        exercises = exercises.map { ex ->
                            val logged = session.sets.filter { it.plannedExerciseId == ex.id }
                                .sortedBy { it.setNumber }
                            val current = (1..ex.sets).firstOrNull { n -> logged.none { it.setNumber == n } }
                            val last = history[ex.id]
                            val increase = SetInput.shouldSuggestIncrease(ex, last?.sets.orEmpty())
                            ExerciseUi(
                                exercise = ex,
                                loggedSets = logged,
                                currentSet = current,
                                currentForm = current?.let {
                                    SetInput.prefill(ex, it, logged, last?.sets.orEmpty())
                                },
                                editingSet = editMap[ex.id]?.takeIf { n -> logged.any { it.setNumber == n } },
                                lastPerformance = last,
                                suggestIncrease = increase,
                            )
                        },
                    )
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState.Loading)

    /** Logs (or, for an already logged [setNumber], updates) a set with already validated values. */
    fun completeSet(
        plannedExerciseId: Long,
        setNumber: Int,
        loadKg: Double,
        reps: Int,
        outcome: SetOutcome,
        rir: Int?,
    ): Job {
        return viewModelScope.launch {
            val before = sessionRepository.observeSession(sessionId).first()
            val isNew = before != null &&
                before.sets.none { it.plannedExerciseId == plannedExerciseId && it.setNumber == setNumber }
            sessionRepository.logSet(
                sessionId, plannedExerciseId, setNumber, loadKg, reps, outcome,
                if (outcome == SetOutcome.BUFFER) rir else null,
            )
            editing.update { it - plannedExerciseId }
            if (isNew) startRestAfterNewSet(before.dayId, before.sets.size + 1, plannedExerciseId)
        }
    }

    /** Auto-starts the exercise's rest, unless it is a jump set or the whole day is now complete. */
    private suspend fun startRestAfterNewSet(dayId: Long, doneSets: Int, plannedExerciseId: Long) {
        val exercises = planRepository.getDay(dayId)?.exercises.orEmpty()
        val exercise = exercises.firstOrNull { it.id == plannedExerciseId } ?: return
        val rest = exercise.restSeconds ?: return
        if (doneSets >= exercises.sumOf { it.sets }) {
            restTimer.skip() // day complete: no rest, and drop the one left from the previous set
            return
        }
        restTimer.start(rest, exercise.name)
    }

    /** Manual rest for jump-set exercises (no rest defined by the plan). */
    fun startManualRest(label: String) = restTimer.start(MANUAL_REST_SECONDS, label)

    fun adjustRest(deltaSeconds: Int) = restTimer.adjust(deltaSeconds)

    fun skipRest() = restTimer.skip()

    fun acknowledgeRestFinished() = restTimer.acknowledgeFinished()

    fun startEdit(plannedExerciseId: Long, setNumber: Int) {
        editing.update { it + (plannedExerciseId to setNumber) }
    }

    fun cancelEdit(plannedExerciseId: Long) {
        editing.update { it - plannedExerciseId }
    }

    /** "Termina allenamento": marks the session as ended and emits its id for navigation. */
    fun finishWorkout() {
        if (finishRequested) return
        finishRequested = true
        restTimer.skip()
        viewModelScope.launch {
            sessionRepository.finishSession(sessionId)
            finished.send(sessionId)
        }
    }

    companion object {
        /** Navigation argument holding the session id. */
        const val SESSION_ID_ARG = "sessionId"

        /** Duration of the manually started rest (jump-set exercises). */
        const val MANUAL_REST_SECONDS = 60

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TrackMyGymGainsApp).container
                val sessionId = checkNotNull(createSavedStateHandle().get<Long>(SESSION_ID_ARG)) {
                    "$SESSION_ID_ARG navigation argument missing"
                }
                WorkoutViewModel(sessionId, container.sessionRepository, container.planRepository, container.restTimer)
            }
        }
    }
}
