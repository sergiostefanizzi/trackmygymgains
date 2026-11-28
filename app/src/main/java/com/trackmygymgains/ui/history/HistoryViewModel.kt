package com.trackmygymgains.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One finished workout in the history. [dayName] is empty if its day is no longer in any plan.
 * [plannedSets] is null in that same case, because the planned total is unknown.
 */
data class HistoryItem(
    val sessionId: Long,
    val dayName: String,
    val startedAt: Long,
    val durationMillis: Long,
    val doneSets: Int,
    val plannedSets: Int?,
    /** True for workouts imported from the plan file: [startedAt] is only a date and there is no real duration. */
    val imported: Boolean = false,
)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    /** Newest first. An empty list shows the empty state. */
    data class Content(val items: List<HistoryItem>) : HistoryUiState
}

class HistoryViewModel(
    private val planRepository: PlanRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val state: StateFlow<HistoryUiState> = sessionRepository.observeFinishedSessions().map { sessions ->
        // One lookup per distinct day: several sessions usually share a day.
        val days = sessions.map { it.dayId }.distinct().associateWith { planRepository.getDay(it) }
        HistoryUiState.Content(
            sessions.map { session ->
                val day = days[session.dayId]
                HistoryItem(
                    sessionId = session.id,
                    dayName = day?.name.orEmpty(),
                    startedAt = session.startedAt,
                    durationMillis = (session.endedAt ?: session.startedAt) - session.startedAt,
                    doneSets = session.sets.size,
                    plannedSets = day?.exercises?.sumOf { it.sets },
                    imported = session.imported,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    /** "Elimina" from a history card. The screen asks for confirmation first. */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                sessionRepository.deleteFinishedSession(sessionId)
            } catch (e: IllegalStateException) {
                // Not finished (cannot happen for a listed item): nothing to delete from the history.
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TrackMyGymGainsApp).container
                HistoryViewModel(container.planRepository, container.sessionRepository)
            }
        }
    }
}
