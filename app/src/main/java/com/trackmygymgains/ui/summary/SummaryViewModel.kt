package com.trackmygymgains.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.PlannedExercise
import com.trackmygymgains.data.repo.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** One planned exercise with the sets logged for it in the finished session. */
data class SummaryExercise(
    val exercise: PlannedExercise,
    val loggedSets: List<LoggedSet>,
)

sealed interface SummaryUiState {
    data object Loading : SummaryUiState

    data object NotFound : SummaryUiState

    /**
     * [durationMillis] is endedAt - startedAt. [plannedSets] is the sum of the day's exercises,
     * [doneSets] the sets logged in the session.
     */
    data class Ready(
        val dayName: String,
        val durationMillis: Long,
        val plannedSets: Int,
        val doneSets: Int,
        val exercises: List<SummaryExercise>,
    ) : SummaryUiState
}

class SummaryViewModel(
    private val sessionId: Long,
    private val sessionRepository: SessionRepository,
    private val planRepository: PlanRepository,
) : ViewModel() {

    val state: StateFlow<SummaryUiState> = flow {
        val session = sessionRepository.observeSession(sessionId).first()
        val endedAt = session?.endedAt
        if (session == null || endedAt == null) {
            emit(SummaryUiState.NotFound)
            return@flow
        }
        val day = planRepository.getDay(session.dayId)
        val exercises = day?.exercises.orEmpty()
        emit(
            SummaryUiState.Ready(
                dayName = day?.name.orEmpty(),
                durationMillis = endedAt - session.startedAt,
                plannedSets = exercises.sumOf { it.sets },
                doneSets = session.sets.size,
                exercises = exercises.map { ex ->
                    SummaryExercise(ex, session.sets.filter { it.plannedExerciseId == ex.id }.sortedBy { it.setNumber })
                },
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState.Loading)

    companion object {
        /** Navigation argument holding the session id. */
        const val SESSION_ID_ARG = "sessionId"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TrackMyGymGainsApp).container
                val sessionId = checkNotNull(createSavedStateHandle().get<Long>(SESSION_ID_ARG)) {
                    "$SESSION_ID_ARG navigation argument missing"
                }
                SummaryViewModel(sessionId, container.sessionRepository, container.planRepository)
            }
        }
    }
}
