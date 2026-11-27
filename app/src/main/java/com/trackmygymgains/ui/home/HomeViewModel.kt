package com.trackmygymgains.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.SessionRepository
import com.trackmygymgains.data.repo.WorkoutPlan
import com.trackmygymgains.timer.RestTimer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The session currently open, with the name of its day (looked up by dayId, so it survives a re-import). */
data class OpenSessionInfo(
    val sessionId: Long,
    val dayName: String,
    val startedAt: Long,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data object NoPlan : HomeUiState

    data class Content(val plan: WorkoutPlan, val openSession: OpenSessionInfo? = null) : HomeUiState
}

class HomeViewModel(
    private val planRepository: PlanRepository,
    private val sessionRepository: SessionRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

    private val started = Channel<Long>(Channel.BUFFERED)

    /** Ids of sessions started from this screen; the route navigates to each one once. */
    val sessionStarted: Flow<Long> = started.receiveAsFlow()

    /** Guards against double taps while a start is in flight. Only touched on the main thread. */
    private var starting = false

    private val openSession: Flow<OpenSessionInfo?> = sessionRepository.observeOpenSession().map { session ->
        session?.let {
            OpenSessionInfo(
                sessionId = it.id,
                dayName = planRepository.getDay(it.dayId)?.name.orEmpty(),
                startedAt = it.startedAt,
            )
        }
    }

    val state: StateFlow<HomeUiState> = combine(planRepository.observeActivePlan(), openSession) { plan, open ->
        if (plan == null) HomeUiState.NoPlan else HomeUiState.Content(plan, open)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** Starts a session for [dayId]. Ignored while another start is running or a session is already open. */
    fun startWorkout(dayId: Long) {
        if (starting) return
        starting = true
        viewModelScope.launch {
            try {
                started.send(sessionRepository.startSession(dayId))
            } catch (e: IllegalStateException) {
                // A session was opened meanwhile (e.g. from another screen): the open-session card shows it.
            } finally {
                starting = false
            }
        }
    }

    /** "Annulla allenamento": deletes the open session and its sets. */
    fun cancelSession(sessionId: Long) {
        restTimer.skip()
        viewModelScope.launch { sessionRepository.discardSession(sessionId) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TrackMyGymGainsApp).container
                HomeViewModel(container.planRepository, container.sessionRepository, container.restTimer)
            }
        }
    }
}
