package com.trackmygymgains.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.PlannedExercise
import com.trackmygymgains.data.repo.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
)

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState

    data object NotFound : WorkoutUiState

    /** [dayName] is the name of the session's day (empty if the day no longer exists). */
    data class Ready(val dayName: String, val exercises: List<ExerciseUi>) : WorkoutUiState
}

class WorkoutViewModel(
    private val sessionId: Long,
    private val sessionRepository: SessionRepository,
    planRepository: PlanRepository,
) : ViewModel() {

    /** exerciseId -> set number open for editing. */
    private val editing = MutableStateFlow<Map<Long, Int>>(emptyMap())

    val state: StateFlow<WorkoutUiState> = flow {
        val first = sessionRepository.observeSession(sessionId).first()
        if (first == null) {
            emit(WorkoutUiState.NotFound)
            return@flow
        }
        val day = planRepository.getDay(first.dayId)
        val exercises = day?.exercises.orEmpty()
        // Loaded once per exercise when the screen opens, not on every session update.
        val history = exercises.associate { it.id to sessionRepository.lastPerformance(it.name, sessionId) }
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
                            ExerciseUi(
                                exercise = ex,
                                loggedSets = logged,
                                currentSet = current,
                                currentForm = current?.let {
                                    SetInput.prefill(ex, it, logged, history[ex.id].orEmpty())
                                },
                                editingSet = editMap[ex.id]?.takeIf { n -> logged.any { it.setNumber == n } },
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
    ) {
        viewModelScope.launch {
            sessionRepository.logSet(
                sessionId, plannedExerciseId, setNumber, loadKg, reps, outcome,
                if (outcome == SetOutcome.BUFFER) rir else null,
            )
            editing.update { it - plannedExerciseId }
        }
    }

    fun startEdit(plannedExerciseId: Long, setNumber: Int) {
        editing.update { it + (plannedExerciseId to setNumber) }
    }

    fun cancelEdit(plannedExerciseId: Long) {
        editing.update { it - plannedExerciseId }
    }

    companion object {
        /** Navigation argument holding the session id. */
        const val SESSION_ID_ARG = "sessionId"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TrackMyGymGainsApp).container
                val sessionId = checkNotNull(createSavedStateHandle().get<Long>(SESSION_ID_ARG)) {
                    "$SESSION_ID_ARG navigation argument missing"
                }
                WorkoutViewModel(sessionId, container.sessionRepository, container.planRepository)
            }
        }
    }
}
