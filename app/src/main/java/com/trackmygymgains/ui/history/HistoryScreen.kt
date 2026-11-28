package com.trackmygymgains.ui.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trackmygymgains.R
import com.trackmygymgains.ui.common.formatDuration
import com.trackmygymgains.ui.common.formatSessionDate
import com.trackmygymgains.ui.common.formatSessionDateTime
import java.time.ZoneId
import java.util.Locale

object HistoryTags {
    const val LIST = "history_list"
    const val EMPTY = "history_empty"
    const val CARD = "history_card"
    const val DELETE = "history_delete"
    const val IMPORTED = "history_imported"
    const val DURATION = "history_duration"
    const val DELETE_CONFIRM = "history_delete_confirm"
    const val DELETE_DISMISS = "history_delete_dismiss"
}

/** Stateful wrapper around [HistoryScreen]. */
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onOpenSummary: (sessionId: Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onBack = onBack,
        onOpenSummary = onOpenSummary,
        onDelete = viewModel::deleteSession,
    )
}

/**
 * Stateless list of finished workouts, newest first. Dates are shown in [zone] and [locale]
 * (device zone and Italian by default).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onOpenSummary: (sessionId: Long) -> Unit,
    onDelete: (sessionId: Long) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.ITALIAN,
) {
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.history_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            HistoryUiState.Loading -> Unit
            is HistoryUiState.Content -> if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text(
                        stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(HistoryTags.EMPTY),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).testTag(HistoryTags.LIST),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.sessionId }) { item ->
                        HistoryCard(
                            item = item,
                            dateText = if (item.imported) {
                                formatSessionDate(item.startedAt, zone, locale)
                            } else {
                                formatSessionDateTime(item.startedAt, zone, locale)
                            },
                            onOpen = { onOpenSummary(item.sessionId) },
                            onDelete = { pendingDelete = item.sessionId },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(sessionId)
                    },
                    modifier = Modifier.testTag(HistoryTags.DELETE_CONFIRM),
                ) {
                    Text(stringResource(R.string.history_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.testTag(HistoryTags.DELETE_DISMISS),
                ) {
                    Text(stringResource(R.string.history_delete_dismiss))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryCard(
    item: HistoryItem,
    dateText: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag(HistoryTags.CARD),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    dateText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(item.dayName, style = MaterialTheme.typography.titleMedium)
                if (item.imported) {
                    Text(
                        stringResource(R.string.history_imported),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .testTag(HistoryTags.IMPORTED)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.summary_duration, formatDuration(item.durationMillis)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag(HistoryTags.DURATION),
                    )
                }
                Text(setsText(item), style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag(HistoryTags.DELETE)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_delete))
            }
        }
    }
}

@Composable
private fun setsText(item: HistoryItem): String {
    val planned = item.plannedSets
    return if (planned != null) {
        stringResource(R.string.summary_sets_done, item.doneSets, planned)
    } else {
        stringResource(R.string.history_sets_only, item.doneSets)
    }
}
