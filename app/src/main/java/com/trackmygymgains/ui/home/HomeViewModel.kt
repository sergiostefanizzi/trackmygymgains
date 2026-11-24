package com.trackmygymgains.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.WorkoutPlan
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data object NoPlan : HomeUiState

    data class Content(val plan: WorkoutPlan) : HomeUiState
}

class HomeViewModel(planRepository: PlanRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = planRepository.observeActivePlan()
        .map { if (it == null) HomeUiState.NoPlan else HomeUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TrackMyGymGainsApp).container
                HomeViewModel(container.planRepository)
            }
        }
    }
}
