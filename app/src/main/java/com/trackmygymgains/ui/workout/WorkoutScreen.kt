package com.trackmygymgains.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.trackmygymgains.timer.RestTimerState
import kotlinx.coroutines.delay
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton

/** Ask for POST_NOTIFICATIONS at most once per app launch (process); denial is never nagged. */
private var notificationPermissionAsked = false

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
    const val FINISH = "workout_finish"
    const val FINISH_DIALOG_TEXT = "workout_finish_dialog_text"
    const val FINISH_CONFIRM = "workout_finish_confirm"
    const val FINISH_DISMISS = "workout_finish_dismiss"
    const val REST_BANNER = "workout_rest_banner"
    const val REST_LABEL = "workout_rest_label"
    const val REST_TIME = "workout_rest_time"
    const val REST_MINUS = "workout_rest_minus"
    const val REST_PLUS = "workout_rest_plus"
    const val REST_SKIP = "workout_rest_skip"
    const val REST_FINISHED = "workout_rest_finished"
    const val REST_START_MANUAL = "workout_rest_start_manual"
}

/** Stateful wrapper around [WorkoutScreen]. The session id comes from the navigation argument. */
@Composable
fun WorkoutRoute(
    onBack: () -> Unit,
    onFinished: (sessionId: Long) -> Unit,
    viewModel: WorkoutViewModel = viewModel(factory = WorkoutViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restState by viewModel.restState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionAsked &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionAsked = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.sessionFinished.collect { onFinished(it) }
    }
    WorkoutScreen(
        state = state,
        onBack = onBack,
        onSubmitSet = viewModel::completeSet,
        onEditSet = viewModel::startEdit,
        onCancelEdit = viewModel::cancelEdit,
        onFinish = viewModel::finishWorkout,
        restState = restState,
        onStartRest = viewModel::startManualRest,
        onRestAdjust = viewModel::adjustRest,
        onRestSkip = viewModel::skipRest,
        onRestFinishedShown = viewModel::acknowledgeRestFinished,
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
    onFinish: () -> Unit = {},
    restState: RestTimerState = RestTimerState.Idle,
    onStartRest: (label: String) -> Unit = {},
    onRestAdjust: (deltaSeconds: Int) -> Unit = {},
    onRestSkip: () -> Unit = {},
    onRestFinishedShown: () -> Unit = {},
) {
    var confirmFinish by remember { mutableStateOf(false) }
    Scaffold(
        bottomBar = {
            RestBanner(restState, onRestAdjust, onRestSkip, onRestFinishedShown)
        },
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
                    ExerciseCard(ex, restState !is RestTimerState.Running, onSubmitSet, onEditSet, onCancelEdit, onStartRest)
                }
                item(key = "finish") {
                    Button(
                        onClick = { confirmFinish = true },
                        modifier = Modifier.fillMaxWidth().testTag(WorkoutTags.FINISH),
                    ) {
                        Text(stringResource(R.string.workout_finish))
                    }
                }
            }
            WorkoutUiState.NotFound -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.workout_not_found))
            }
            WorkoutUiState.Loading -> Unit
        }
    }

    if (confirmFinish && state is WorkoutUiState.Ready) {
        val text = if (state.doneSets < state.plannedSets) {
            stringResource(R.string.workout_finish_dialog_missing, state.doneSets, state.plannedSets)
        } else {
            stringResource(R.string.workout_finish_dialog_complete)
        }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.workout_finish_dialog_title)) },
            text = { Text(text, modifier = Modifier.testTag(WorkoutTags.FINISH_DIALOG_TEXT)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFinish = false
                        onFinish()
                    },
                    modifier = Modifier.testTag(WorkoutTags.FINISH_CONFIRM),
                ) {
                    Text(stringResource(R.string.workout_finish_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmFinish = false },
                    modifier = Modifier.testTag(WorkoutTags.FINISH_DISMISS),
                ) {
                    Text(stringResource(R.string.workout_finish_dismiss))
                }
            },
        )
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
    restIdle: Boolean,
    onSubmitSet: (Long, Int, Double, Int, SetOutcome, Int?) -> Unit,
    onEditSet: (Long, Int) -> Unit,
    onCancelEdit: (Long) -> Unit,
    onStartRest: (String) -> Unit,
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
            if (ex.restSeconds == null && restIdle) {
                TextButton(
                    onClick = { onStartRest(ex.name) },
                    modifier = Modifier.testTag(WorkoutTags.REST_START_MANUAL),
                ) {
                    Text(stringResource(R.string.workout_rest_start_manual, formatRest(WorkoutViewModel.MANUAL_REST_SECONDS)))
                }
            }
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

/** How long "Recupero terminato" stays visible before the timer is acknowledged. */
private const val FINISHED_VISIBLE_MILLIS = 3_000L

/** Bottom banner shown while the rest timer is Running or Finished; nothing when Idle. */
@Composable
private fun RestBanner(
    state: RestTimerState,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    onFinishedShown: () -> Unit,
) {
    when (state) {
        RestTimerState.Idle -> Unit
        is RestTimerState.Running -> Surface(
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag(WorkoutTags.REST_BANNER),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.label?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.testTag(WorkoutTags.REST_LABEL),
                    )
                }
                Text(
                    formatRest(state.remainingSeconds),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.testTag(WorkoutTags.REST_TIME),
                )
                LinearProgressIndicator(
                    progress = {
                        (state.remainingMillis / (state.totalSeconds * 1000f)).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAdjust(-15) },
                        modifier = Modifier.weight(1f).testTag(WorkoutTags.REST_MINUS),
                    ) { Text(stringResource(R.string.workout_rest_minus)) }
                    OutlinedButton(
                        onClick = { onAdjust(15) },
                        modifier = Modifier.weight(1f).testTag(WorkoutTags.REST_PLUS),
                    ) { Text(stringResource(R.string.workout_rest_plus)) }
                    Button(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f).testTag(WorkoutTags.REST_SKIP),
                    ) { Text(stringResource(R.string.workout_rest_skip)) }
                }
            }
        }
        is RestTimerState.Finished -> {
            LaunchedEffect(state) {
                delay(FINISHED_VISIBLE_MILLIS)
                onFinishedShown()
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onFinishedShown).testTag(WorkoutTags.REST_BANNER),
            ) {
                Text(
                    stringResource(R.string.workout_rest_finished),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp).testTag(WorkoutTags.REST_FINISHED),
                )
            }
        }
    }
}
