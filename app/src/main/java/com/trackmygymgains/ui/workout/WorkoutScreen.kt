package com.trackmygymgains.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trackmygymgains.R

/** Stateful wrapper around [WorkoutScreen]. The session id comes from the navigation argument. */
@Composable
fun WorkoutRoute(
    onBack: () -> Unit,
    viewModel: WorkoutViewModel = viewModel(factory = WorkoutViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScreen(state = state, onBack = onBack)
}

/** Placeholder workout screen (M2.3 fills the body). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(state: WorkoutUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? WorkoutUiState.Ready)?.dayName.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.workout_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (state) {
                is WorkoutUiState.Ready -> Text(stringResource(R.string.workout_placeholder))
                WorkoutUiState.NotFound -> Text(stringResource(R.string.workout_not_found))
                WorkoutUiState.Loading -> Unit
            }
        }
    }
}
