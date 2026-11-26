package com.trackmygymgains.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.SessionRepository
import com.trackmygymgains.data.repo.WorkoutSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState

    data object NotFound : WorkoutUiState

    /** [dayName] is the name of the session's day (empty if the day no longer exists). */
    data class Ready(val session: WorkoutSession, val dayName: String) : WorkoutUiState
}

class WorkoutViewModel(
    sessionId: Long,
    sessionRepository: SessionRepository,
    planRepository: PlanRepository,
) : ViewModel() {

    val state: StateFlow<WorkoutUiState> = sessionRepository.observeSession(sessionId)
        .map { session ->
            if (session == null) {
                WorkoutUiState.NotFound
            } else {
                WorkoutUiState.Ready(session, planRepository.getDay(session.dayId)?.name.orEmpty())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState.Loading)

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
