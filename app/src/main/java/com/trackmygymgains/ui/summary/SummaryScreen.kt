package com.trackmygymgains.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trackmygymgains.R
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.ui.common.formatDuration
import com.trackmygymgains.ui.workout.SetInput

object SummaryTags {
    const val HOME = "summary_home"
    const val DURATION = "summary_duration"
    const val SETS_TOTAL = "summary_sets_total"
    const val EXERCISE_CARD = "summary_exercise_card"
}

/** Stateful wrapper around [SummaryScreen]. The session id comes from the navigation argument. */
@Composable
fun SummaryRoute(
    onHome: () -> Unit,
    fromHistory: Boolean = false,
    viewModel: SummaryViewModel = viewModel(factory = SummaryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SummaryScreen(state = state, onHome = onHome, fromHistory = fromHistory)
}

/**
 * [onHome] is the button action: "Torna alla home" after a workout, or "Indietro" (back to the history)
 * when [fromHistory] is true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    state: SummaryUiState,
    onHome: () -> Unit,
    fromHistory: Boolean = false,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text((state as? SummaryUiState.Ready)?.dayName.orEmpty()) })
        },
    ) { padding ->
        when (state) {
            is SummaryUiState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.summary_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.summary_duration, formatDuration(state.durationMillis)),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.testTag(SummaryTags.DURATION),
                            )
                            Text(
                                stringResource(R.string.summary_sets_done, state.doneSets, state.plannedSets),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.testTag(SummaryTags.SETS_TOTAL),
                            )
                        }
                    }
                }
                items(state.exercises, key = { it.exercise.id }) { ex ->
                    ExerciseSummaryCard(ex)
                }
                item(key = "home") {
                    Button(
                        onClick = onHome,
                        modifier = Modifier.fillMaxWidth().testTag(SummaryTags.HOME),
                    ) {
                        Text(
                            stringResource(if (fromHistory) R.string.summary_back else R.string.summary_home),
                        )
                    }
                }
            }
            SummaryUiState.NotFound -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.summary_not_found))
            }
            SummaryUiState.Loading -> Unit
        }
    }
}

@Composable
private fun ExerciseSummaryCard(ex: SummaryExercise) {
    Card(Modifier.fillMaxWidth().testTag(SummaryTags.EXERCISE_CARD)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    ex.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.workout_progress, ex.loggedSets.size, ex.exercise.sets),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (ex.loggedSets.isEmpty()) {
                Text(
                    stringResource(R.string.summary_no_sets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            for (set in ex.loggedSets) {
                Text(setLine(set), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun setLine(set: LoggedSet): String {
    val load = SetInput.formatLoad(set.loadKg)
    return if (set.outcome == SetOutcome.BUFFER) {
        stringResource(R.string.summary_set_buffer, load, set.reps, set.rir ?: 0)
    } else {
        stringResource(R.string.summary_set_failure, load, set.reps)
    }
}
