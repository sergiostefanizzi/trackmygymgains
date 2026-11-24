package com.trackmygymgains.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

object HomeTags {
    const val MENU = "home_menu"
    const val IMPORT_NEW = "home_import_new"
    const val LOADING = "home_loading"
}

/** Stateful wrapper around [HomeScreen]. */
@Composable
fun HomeRoute(
    onImportNew: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, onImportNew = onImportNew)
}

/** Placeholder home (M2.2 builds the real one): plan name, overflow menu and the list of days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeUiState, onImportNew: () -> Unit) {
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
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is HomeUiState.Content -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.plan.days, key = { it.id }) { day ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(
                                        R.string.home_day_summary,
                                        day.name,
                                        pluralStringResource(
                                            R.plurals.exercises_count,
                                            day.exercises.size,
                                            day.exercises.size,
                                        ),
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
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
