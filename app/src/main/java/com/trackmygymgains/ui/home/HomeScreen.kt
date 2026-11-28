package com.trackmygymgains.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trackmygymgains.R
import com.trackmygymgains.data.repo.WorkoutDay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object HomeTags {
    const val MENU = "home_menu"
    const val IMPORT_NEW = "home_import_new"
    const val HISTORY = "home_history"
    const val LOADING = "home_loading"
    const val OPEN_SESSION = "home_open_session"
    const val RESUME = "home_resume"
    const val CANCEL_SESSION = "home_cancel_session"
    const val CANCEL_CONFIRM = "home_cancel_confirm"
    const val CANCEL_DISMISS = "home_cancel_dismiss"
    const val START_DISABLED_HINT = "home_start_disabled_hint"

    fun startTag(dayId: Long) = "home_start_$dayId"
}

/** Stateful wrapper around [HomeScreen]. */
@Composable
fun HomeRoute(
    onImportNew: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenWorkout: (sessionId: Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.sessionStarted.collect { onOpenWorkout(it) }
    }
    HomeScreen(
        state = state,
        onImportNew = onImportNew,
        onOpenHistory = onOpenHistory,
        onStartDay = viewModel::startWorkout,
        onResumeSession = onOpenWorkout,
        onCancelSession = viewModel::cancelSession,
    )
}

/** Clock time (HH:mm) in the device zone, used for "iniziato alle HH:mm". */
internal fun formatStartTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** Stateless home: plan name, overflow menu, open-session card and one card per day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onImportNew: () -> Unit,
    onOpenHistory: () -> Unit,
    onStartDay: (dayId: Long) -> Unit,
    onResumeSession: (sessionId: Long) -> Unit,
    onCancelSession: (sessionId: Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? HomeUiState.Content)?.plan?.name.orEmpty()) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(HomeTags.MENU)) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.home_menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_import_new)) },
                                onClick = {
                                    menuOpen = false
                                    onImportNew()
                                },
                                modifier = Modifier.testTag(HomeTags.IMPORT_NEW),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_history)) },
                                onClick = {
                                    menuOpen = false
                                    onOpenHistory()
                                },
                                modifier = Modifier.testTag(HomeTags.HISTORY),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is HomeUiState.Content -> {
                    val open = state.openSession
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (open != null) {
                            item(key = "open_session") {
                                OpenSessionCard(
                                    open = open,
                                    onResume = { onResumeSession(open.sessionId) },
                                    onCancel = { onCancelSession(open.sessionId) },
                                )
                            }
                            item(key = "start_disabled_hint") {
                                Text(
                                    stringResource(R.string.home_start_disabled_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag(HomeTags.START_DISABLED_HINT),
                                )
                            }
                        }
                        items(state.plan.days, key = { it.id }) { day ->
                            DayCard(day = day, startEnabled = open == null, onStart = { onStartDay(day.id) })
                        }
                    }
                }
                else -> Box(
                    Modifier.fillMaxSize().testTag(HomeTags.LOADING),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state is HomeUiState.Loading) CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun DayCard(day: WorkoutDay, startEnabled: Boolean, onStart: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(
                    R.string.home_day_summary,
                    day.name,
                    pluralStringResource(R.plurals.exercises_count, day.exercises.size, day.exercises.size),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = onStart,
                enabled = startEnabled,
                modifier = Modifier.fillMaxWidth().testTag(HomeTags.startTag(day.id)),
            ) {
                Text(stringResource(R.string.home_start_workout))
            }
        }
    }
}

@Composable
private fun OpenSessionCard(open: OpenSessionInfo, onResume: () -> Unit, onCancel: () -> Unit) {
    var confirmCancel by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().testTag(HomeTags.OPEN_SESSION),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.home_open_session_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(
                    R.string.home_open_session_summary,
                    open.dayName,
                    formatStartTime(open.startedAt),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth().testTag(HomeTags.RESUME),
            ) {
                Text(stringResource(R.string.home_resume))
            }
            OutlinedButton(
                onClick = { confirmCancel = true },
                modifier = Modifier.fillMaxWidth().testTag(HomeTags.CANCEL_SESSION),
            ) {
                Text(stringResource(R.string.home_cancel_session))
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.home_cancel_dialog_title)) },
            text = { Text(stringResource(R.string.home_cancel_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCancel = false
                        onCancel()
                    },
                    modifier = Modifier.testTag(HomeTags.CANCEL_CONFIRM),
                ) {
                    Text(stringResource(R.string.home_cancel_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmCancel = false },
                    modifier = Modifier.testTag(HomeTags.CANCEL_DISMISS),
                ) {
                    Text(stringResource(R.string.home_cancel_dismiss))
                }
            },
        )
    }
}
