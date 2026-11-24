package com.trackmygymgains.ui.importplan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trackmygymgains.R
import com.trackmygymgains.data.plan.ParsedDay
import com.trackmygymgains.data.plan.ParsedExercise
import com.trackmygymgains.ui.common.formatRest

object ImportTags {
    const val PICK_FILE = "import_pick_file"
    const val PICK_OTHER_FILE = "import_pick_other_file"
    const val LOADING = "import_loading"
    const val ERROR = "import_error"
    const val SHEET_ERROR = "import_sheet_error"
    const val SHEET_SELECTOR = "import_sheet_selector"
    const val SHEET_OPTION_PREFIX = "import_sheet_option_"
    const val WARNINGS = "import_warnings"
    const val CONFIRM = "import_confirm"
    const val REPLACE_HINT = "import_replace_hint"
}

private val XLSX_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    // Some document providers report generic binary content for xlsx files; content is validated anyway.
    "application/octet-stream",
)

/** Stateful wrapper: wires the ViewModel, the SAF picker and navigation callbacks. */
@Composable
fun ImportRoute(
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onImported: () -> Unit,
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val replaces by viewModel.hasActivePlan.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFilePicked(uri.toString())
    }
    LaunchedEffect(state) {
        if (state is ImportUiState.Imported) onImported()
    }
    ImportScreen(
        state = state,
        replacesExisting = replaces,
        canNavigateBack = canNavigateBack,
        onPickFile = { launcher.launch(XLSX_MIME_TYPES) },
        onSelectSheet = viewModel::selectSheet,
        onConfirm = viewModel::confirm,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    state: ImportUiState,
    replacesExisting: Boolean,
    canNavigateBack: Boolean,
    onPickFile: () -> Unit,
    onSelectSheet: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.import_back),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state is ImportUiState.Preview) {
                PreviewActions(state, replacesExisting, onConfirm, onPickFile)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ImportUiState.Idle -> IdleContent(onPickFile)
                ImportUiState.Loading, ImportUiState.Imported -> Box(
                    Modifier.fillMaxSize().testTag(ImportTags.LOADING),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is ImportUiState.Error -> ErrorContent(state.message, onPickFile)
                is ImportUiState.Preview -> PreviewContent(state, onSelectSheet)
            }
        }
    }
}

@Composable
private fun IdleContent(onPickFile: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.import_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onPickFile, modifier = Modifier.testTag(ImportTags.PICK_FILE)) {
            Text(stringResource(R.string.import_pick_file))
        }
    }
}

@Composable
private fun ErrorContent(message: String, onPickFile: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(ImportTags.ERROR),
        )
        Button(onClick = onPickFile, modifier = Modifier.testTag(ImportTags.PICK_OTHER_FILE)) {
            Text(stringResource(R.string.import_pick_other_file))
        }
    }
}

@Composable
private fun PreviewActions(
    state: ImportUiState.Preview,
    replacesExisting: Boolean,
    onConfirm: () -> Unit,
    onPickFile: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (replacesExisting) {
                Text(
                    stringResource(R.string.import_replace_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(ImportTags.REPLACE_HINT),
                )
            }
            Button(
                onClick = onConfirm,
                enabled = state.canConfirm,
                modifier = Modifier.fillMaxWidth().testTag(ImportTags.CONFIRM),
            ) {
                Text(
                    stringResource(
                        when {
                            state.importing -> R.string.import_importing
                            replacesExisting -> R.string.import_confirm_replace
                            else -> R.string.import_confirm
                        },
                    ),
                )
            }
            OutlinedButton(
                onClick = onPickFile,
                enabled = !state.importing,
                modifier = Modifier.fillMaxWidth().testTag(ImportTags.PICK_OTHER_FILE),
            ) {
                Text(stringResource(R.string.import_pick_other_file))
            }
        }
    }
}

@Composable
private fun PreviewContent(state: ImportUiState.Preview, onSelectSheet: (Int) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.fileName?.let {
            Text(stringResource(R.string.import_file_name, it), style = MaterialTheme.typography.titleMedium)
        }
        if (state.sheetNames.size > 1) {
            SheetSelector(state.sheetNames, state.selectedSheet, onSelectSheet)
        }
        if (state.sheetError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().testTag(ImportTags.SHEET_ERROR),
            ) {
                Text(
                    state.sheetError,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (state.warnings.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth().testTag(ImportTags.WARNINGS),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.import_warnings_title, state.warnings.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    state.warnings.forEach {
                        Text(
                            stringResource(R.string.import_warning_row, it.row, it.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
        state.plan?.days?.forEach { DayPreview(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetSelector(sheetNames: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = sheetNames.getOrElse(selected) { "" },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.import_sheet_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(ImportTags.SHEET_SELECTOR),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sheetNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                    modifier = Modifier.testTag(ImportTags.SHEET_OPTION_PREFIX + index),
                )
            }
        }
    }
}

@Composable
private fun DayPreview(day: ParsedDay) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(day.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            day.exercises.forEachIndexed { index, exercise ->
                if (index > 0) HorizontalDivider()
                ExerciseRow(exercise)
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: ParsedExercise) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
        val setsReps = exercise.targetReps
            ?.let { stringResource(R.string.exercise_sets_reps, exercise.sets, it) }
            ?: stringResource(R.string.exercise_sets_only, exercise.sets)
        val details = listOfNotNull(
            setsReps,
            exercise.targetRir?.let { stringResource(R.string.exercise_rir, it) },
            exercise.restSeconds?.let { stringResource(R.string.exercise_rest, formatRest(it)) }
                ?: stringResource(R.string.exercise_no_rest),
        )
        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        exercise.notes?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
