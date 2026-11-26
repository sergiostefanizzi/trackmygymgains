package com.trackmygymgains.ui.workout

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trackmygymgains.R
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.data.repo.PlannedExercise
import com.trackmygymgains.ui.common.formatRest

object WorkoutTags {
    const val LOAD_FIELD = "workout_load"
    const val REPS_FIELD = "workout_reps"
    const val OUTCOME_BUFFER = "workout_outcome_buffer"
    const val OUTCOME_FAILURE = "workout_outcome_failure"
    const val RIR_VALUE = "workout_rir_value"
    const val RIR_MINUS = "workout_rir_minus"
    const val RIR_PLUS = "workout_rir_plus"
    const val SUBMIT = "workout_submit"
    const val CANCEL_EDIT = "workout_cancel_edit"
    const val SET_SUMMARY = "workout_set_summary"
    const val EXERCISE_CARD = "workout_exercise_card"
}

/** Stateful wrapper around [WorkoutScreen]. The session id comes from the navigation argument. */
@Composable
fun WorkoutRoute(
    onBack: () -> Unit,
    viewModel: WorkoutViewModel = viewModel(factory = WorkoutViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScreen(
        state = state,
        onBack = onBack,
        onSubmitSet = viewModel::completeSet,
        onEditSet = viewModel::startEdit,
        onCancelEdit = viewModel::cancelEdit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    onBack: () -> Unit,
    onSubmitSet: (exerciseId: Long, setNumber: Int, loadKg: Double, reps: Int, outcome: SetOutcome, rir: Int?) -> Unit =
        { _, _, _, _, _, _ -> },
    onEditSet: (exerciseId: Long, setNumber: Int) -> Unit = { _, _ -> },
    onCancelEdit: (exerciseId: Long) -> Unit = {},
) {
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
        when (state) {
            is WorkoutUiState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.exercises, key = { it.exercise.id }) { ex ->
                    ExerciseCard(ex, onSubmitSet, onEditSet, onCancelEdit)
                }
            }
            WorkoutUiState.NotFound -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.workout_not_found))
            }
            WorkoutUiState.Loading -> Unit
        }
    }
}

@Composable
private fun targetsLine(ex: PlannedExercise): String {
    val parts = mutableListOf<String>()
    parts += if (ex.targetReps != null) {
        stringResource(R.string.workout_target_sets_reps, ex.sets, ex.targetReps)
    } else {
        stringResource(R.string.workout_target_sets_only, ex.sets)
    }
    ex.targetRir?.let { parts += stringResource(R.string.workout_target_rir, it) }
    parts += if (ex.restSeconds != null) {
        stringResource(R.string.workout_target_rest, formatRest(ex.restSeconds))
    } else {
        stringResource(R.string.workout_jump_set)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ExerciseCard(
    ui: ExerciseUi,
    onSubmitSet: (Long, Int, Double, Int, SetOutcome, Int?) -> Unit,
    onEditSet: (Long, Int) -> Unit,
    onCancelEdit: (Long) -> Unit,
) {
    val ex = ui.exercise
    Card(Modifier.fillMaxWidth().testTag(WorkoutTags.EXERCISE_CARD)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ex.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.workout_progress, ui.loggedSets.size, ex.sets),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(targetsLine(ex), style = MaterialTheme.typography.bodyMedium)
            ex.notes?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (n in 1..ex.sets) {
                val logged = ui.loggedSets.firstOrNull { it.setNumber == n }
                when {
                    logged != null && ui.editingSet == n -> key(ex.id, n, logged.id, logged.completedAt) {
                        SetForm(
                            initial = SetInput.formFrom(logged, SetInput.defaultRir(ex.targetRir)),
                            submitLabel = stringResource(R.string.workout_save_set),
                            onSubmit = { l, r, o, rir -> onSubmitSet(ex.id, n, l, r, o, rir) },
                            onCancel = { onCancelEdit(ex.id) },
                        )
                    }
                    logged != null -> Text(
                        setSummary(logged),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditSet(ex.id, n) }
                            .padding(vertical = 8.dp)
                            .testTag(WorkoutTags.SET_SUMMARY),
                    )
                    n == ui.currentSet && ui.currentForm != null && ui.editingSet == null ->
                        key(ex.id, n) {
                            SetForm(
                                initial = ui.currentForm,
                                submitLabel = stringResource(R.string.workout_complete_set),
                                onSubmit = { l, r, o, rir -> onSubmitSet(ex.id, n, l, r, o, rir) },
                                onCancel = null,
                            )
                        }
                    else -> Text(
                        stringResource(R.string.workout_set_pending, n),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun setSummary(set: LoggedSet): String {
    val load = SetInput.formatLoad(set.loadKg)
    return if (set.outcome == SetOutcome.BUFFER) {
        stringResource(R.string.workout_set_summary_buffer, set.setNumber, load, set.reps, set.rir ?: 0)
    } else {
        stringResource(R.string.workout_set_summary_failure, set.setNumber, load, set.reps)
    }
}

@Composable
private fun errorText(error: InputError?, loadField: Boolean): String? = when (error) {
    null, InputError.EMPTY -> null
    InputError.NOT_A_NUMBER -> stringResource(R.string.workout_error_not_a_number)
    InputError.TOO_MANY_DECIMALS -> stringResource(R.string.workout_error_decimals)
    InputError.OUT_OF_RANGE ->
        stringResource(if (loadField) R.string.workout_error_load_range else R.string.workout_error_reps_range)
}

@Composable
private fun SetForm(
    initial: SetFormValues,
    submitLabel: String,
    onSubmit: (Double, Int, SetOutcome, Int?) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var load by rememberSaveable(initial) { mutableStateOf(initial.loadText) }
    var reps by rememberSaveable(initial) { mutableStateOf(initial.repsText) }
    var isBuffer by rememberSaveable(initial) { mutableStateOf(initial.outcome == SetOutcome.BUFFER) }
    var rir by rememberSaveable(initial) { mutableIntStateOf(initial.rir) }

    val loadResult = SetInput.parseLoad(load)
    val repsResult = SetInput.parseReps(reps)
    val loadMessage = errorText((loadResult as? InputResult.Invalid)?.error, loadField = true)
    val repsMessage = errorText((repsResult as? InputResult.Invalid)?.error, loadField = false)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = load,
                onValueChange = { load = it },
                label = { Text(stringResource(R.string.workout_load_label)) },
                isError = loadMessage != null,
                supportingText = loadMessage?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag(WorkoutTags.LOAD_FIELD),
            )
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it },
                label = { Text(stringResource(R.string.workout_reps_label)) },
                isError = repsMessage != null,
                supportingText = repsMessage?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag(WorkoutTags.REPS_FIELD),
            )
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = isBuffer,
                onClick = { isBuffer = true },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                modifier = Modifier.testTag(WorkoutTags.OUTCOME_BUFFER),
            ) { Text(stringResource(R.string.workout_outcome_buffer)) }
            SegmentedButton(
                selected = !isBuffer,
                onClick = { isBuffer = false },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                modifier = Modifier.testTag(WorkoutTags.OUTCOME_FAILURE),
            ) { Text(stringResource(R.string.workout_outcome_failure)) }
        }
        if (isBuffer) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.workout_rir_label), style = MaterialTheme.typography.bodyLarge)
                TextButton(
                    onClick = { rir = (rir - 1).coerceAtLeast(0) },
                    enabled = rir > 0,
                    modifier = Modifier.testTag(WorkoutTags.RIR_MINUS),
                ) { Text("-") }
                Text(
                    rir.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag(WorkoutTags.RIR_VALUE),
                )
                TextButton(
                    onClick = { rir = (rir + 1).coerceAtMost(SetInput.MAX_RIR) },
                    enabled = rir < SetInput.MAX_RIR,
                    modifier = Modifier.testTag(WorkoutTags.RIR_PLUS),
                ) { Text("+") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val l = (loadResult as? InputResult.Valid)?.value
                    val r = (repsResult as? InputResult.Valid)?.value
                    if (l != null && r != null) {
                        val outcome = if (isBuffer) SetOutcome.BUFFER else SetOutcome.FAILURE
                        onSubmit(l, r, outcome, if (isBuffer) rir else null)
                    }
                },
                enabled = loadResult is InputResult.Valid && repsResult is InputResult.Valid,
                modifier = Modifier.testTag(WorkoutTags.SUBMIT),
            ) { Text(submitLabel) }
            if (onCancel != null) {
                TextButton(onClick = onCancel, modifier = Modifier.testTag(WorkoutTags.CANCEL_EDIT)) {
                    Text(stringResource(R.string.workout_cancel_edit))
                }
            }
        }
    }
}
