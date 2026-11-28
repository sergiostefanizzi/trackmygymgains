package com.trackmygymgains.ui.workout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackmygymgains.R
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.data.repo.PlannedExercise
import com.trackmygymgains.timer.RestTimerState
import com.trackmygymgains.ui.theme.TrackMyGymGainsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val exercise = PlannedExercise(1, 0, "PULL UP", 3, "4-6", "1-2", 180, "NO ELASTICO")

    private fun logged(n: Int, load: Double, reps: Int, outcome: SetOutcome, rir: Int?) =
        LoggedSet(n.toLong(), 1, n, load, reps, outcome, rir, 0)

    private fun ui(
        exercise: PlannedExercise = this.exercise,
        logged: List<LoggedSet> = emptyList(),
        editing: Int? = null,
        form: SetFormValues = SetFormValues("", "", SetOutcome.BUFFER, 1),
    ) = WorkoutUiState.Ready(
        "FULL BODY 1",
        listOf(
            ExerciseUi(
                exercise = exercise,
                loggedSets = logged,
                currentSet = (1..exercise.sets).firstOrNull { n -> logged.none { it.setNumber == n } },
                currentForm = form,
                editingSet = editing,
            ),
        ),
    )

    private class Submitted {
        val calls = mutableListOf<List<Any?>>()
        val edits = mutableListOf<Pair<Long, Int>>()
        var finishes = 0
    }

    private fun show(state: WorkoutUiState, submitted: Submitted = Submitted()) {
        rule.setContent {
            TrackMyGymGainsTheme {
                WorkoutScreen(
                    state = state,
                    onBack = {},
                    onSubmitSet = { id, n, l, r, o, rir -> submitted.calls += listOf(id, n, l, r, o, rir) },
                    onEditSet = { id, n -> submitted.edits += id to n },
                    onFinish = { submitted.finishes++ },
                )
            }
        }
    }

    @Test
    fun finishDialogShowsMissingSetsAndConfirmCallsBack() {
        val submitted = Submitted()
        show(ui(logged = listOf(logged(1, 80.0, 8, SetOutcome.BUFFER, 2))), submitted)

        rule.onNodeWithTag(WorkoutTags.FINISH).performScrollTo().performClick()
        rule.onNodeWithTag(WorkoutTags.FINISH_DIALOG_TEXT)
            .assertTextEquals("Hai completato 1 serie su 3. Vuoi terminare l'allenamento?")
        assertEquals(0, submitted.finishes)

        rule.onNodeWithTag(WorkoutTags.FINISH_CONFIRM).performClick()

        assertEquals(1, submitted.finishes)
        rule.onNodeWithTag(WorkoutTags.FINISH_DIALOG_TEXT).assertDoesNotExist()
    }

    @Test
    fun finishDialogDismissDoesNotFinish() {
        val submitted = Submitted()
        show(ui(), submitted)

        rule.onNodeWithTag(WorkoutTags.FINISH).performClick()
        rule.onNodeWithTag(WorkoutTags.FINISH_DIALOG_TEXT).assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.FINISH_DISMISS).performClick()

        assertEquals(0, submitted.finishes)
        rule.onNodeWithTag(WorkoutTags.FINISH_DIALOG_TEXT).assertDoesNotExist()
    }

    @Test
    fun finishDialogWhenAllSetsDoneShowsSimpleConfirmation() {
        val all = (1..3).map { logged(it, 80.0, 8, SetOutcome.BUFFER, 2) }
        show(ui(logged = all))

        rule.onNodeWithTag(WorkoutTags.FINISH).performClick()
        rule.onNodeWithTag(WorkoutTags.FINISH_DIALOG_TEXT)
            .assertTextEquals("Hai completato tutte le serie. Vuoi terminare l'allenamento?")
    }

    @Test
    fun showsExerciseTargetsNotesAndProgress() {
        show(ui(logged = listOf(logged(1, 80.0, 8, SetOutcome.BUFFER, 2))))

        rule.onNodeWithText("PULL UP").assertIsDisplayed()
        rule.onNodeWithText("3 × 4-6 · RIR 1-2 · Rec. 3:00").assertIsDisplayed()
        rule.onNodeWithText("NO ELASTICO").assertIsDisplayed()
        rule.onNodeWithText("1/3 serie").assertIsDisplayed()
        rule.onNodeWithText("Serie 3").assertIsDisplayed()
    }

    @Test
    fun targetsLineOmitsNullPartsAndShowsJumpSet() {
        show(ui(exercise = exercise.copy(targetReps = null, targetRir = null, restSeconds = null, notes = null)))

        rule.onNodeWithText("3 serie · Jump set").assertIsDisplayed()
    }

    @Test
    fun completedSetsShowCompactSummary() {
        show(
            ui(
                logged = listOf(
                    logged(1, 80.0, 8, SetOutcome.BUFFER, 2),
                    logged(2, 82.5, 6, SetOutcome.FAILURE, null),
                ),
            ),
        )

        rule.onNodeWithText("1 · 80 kg × 8 · Buffer RIR 2").assertIsDisplayed()
        rule.onNodeWithText("2 · 82,5 kg × 6 · Cedimento").assertIsDisplayed()
    }

    @Test
    fun completedBufferSetWithoutRirShowsNd() {
        show(ui(logged = listOf(logged(1, 80.0, 8, SetOutcome.BUFFER, null))))

        rule.onNodeWithText("1 · 80 kg × 8 · Buffer RIR n/d").assertIsDisplayed()
    }

    @Test
    fun tappingCompletedSetRequestsEdit() {
        val submitted = Submitted()
        show(ui(logged = listOf(logged(1, 80.0, 8, SetOutcome.BUFFER, 2))), submitted)

        rule.onNodeWithText("1 · 80 kg × 8 · Buffer RIR 2").performClick()

        assertEquals(listOf(1L to 1), submitted.edits)
    }

    @Test
    fun editingSetShowsFormWithSaveButton() {
        show(
            ui(
                logged = listOf(logged(1, 80.0, 8, SetOutcome.BUFFER, 2)),
                editing = 1,
            ),
        )

        rule.onNodeWithText(context.getString(R.string.workout_save_set)).assertIsDisplayed()
        rule.onNodeWithText("80").assertIsDisplayed()
    }

    @Test
    fun invalidInputShowsErrorAndDisablesSubmit() {
        show(ui())

        rule.onNodeWithTag(WorkoutTags.SUBMIT).assertIsNotEnabled()

        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).performTextInput("80,123")
        rule.onNodeWithTag(WorkoutTags.REPS_FIELD).performTextInput("8")
        rule.onNodeWithText(context.getString(R.string.workout_error_decimals)).assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.SUBMIT).assertIsNotEnabled()

        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).performTextClearance()
        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).performTextInput("abc")
        rule.onNodeWithText(context.getString(R.string.workout_error_not_a_number)).assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun validInputSubmitsParsedValues() {
        val submitted = Submitted()
        show(ui(), submitted)

        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).performTextInput("82,5")
        rule.onNodeWithTag(WorkoutTags.REPS_FIELD).performTextInput("6")
        rule.onNodeWithTag(WorkoutTags.RIR_PLUS).performClick()
        rule.onNodeWithTag(WorkoutTags.SUBMIT).assertIsEnabled().performClick()

        assertEquals(listOf(listOf(1L, 1, 82.5, 6, SetOutcome.BUFFER, 2)), submitted.calls)
    }

    @Test
    fun failureSubmitsWithoutRir() {
        val submitted = Submitted()
        show(ui(), submitted)

        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).performTextInput("0")
        rule.onNodeWithTag(WorkoutTags.REPS_FIELD).performTextInput("10")
        rule.onNodeWithTag(WorkoutTags.OUTCOME_FAILURE).performClick()
        rule.onNodeWithTag(WorkoutTags.SUBMIT).performClick()

        assertEquals(listOf(listOf(1L, 1, 0.0, 10, SetOutcome.FAILURE, null)), submitted.calls)
    }

    @Test
    fun outcomeToggleHidesAndShowsRirStepper() {
        show(ui())

        rule.onNodeWithTag(WorkoutTags.RIR_VALUE).assertIsDisplayed()
        rule.onNodeWithText("1").assertIsDisplayed()

        rule.onNodeWithTag(WorkoutTags.OUTCOME_FAILURE).performClick()
        rule.onNodeWithTag(WorkoutTags.RIR_VALUE).assertDoesNotExist()
        rule.onNodeWithTag(WorkoutTags.RIR_PLUS).assertDoesNotExist()

        rule.onNodeWithTag(WorkoutTags.OUTCOME_BUFFER).performClick()
        rule.onNodeWithTag(WorkoutTags.RIR_VALUE).assertIsDisplayed()
    }

    @Test
    fun rirStepperStaysWithinZeroAndTen() {
        show(ui(form = SetFormValues("", "", SetOutcome.BUFFER, 0)))

        rule.onNodeWithTag(WorkoutTags.RIR_MINUS).assertIsNotEnabled()
        rule.onNodeWithTag(WorkoutTags.RIR_PLUS).assertIsEnabled()
    }

    private class RestCalls {
        val adjusts = mutableListOf<Int>()
        var skips = 0
        var stops = 0
        val manualStarts = mutableListOf<String>()
    }

    private fun showRest(state: WorkoutUiState, rest: RestTimerState, calls: RestCalls = RestCalls()) {
        rule.setContent {
            TrackMyGymGainsTheme {
                WorkoutScreen(
                    state = state,
                    onBack = {},
                    restState = rest,
                    onStartRest = { calls.manualStarts += it },
                    onRestAdjust = { calls.adjusts += it },
                    onRestSkip = { calls.skips++ },
                    onRestFinishedShown = { calls.stops++ },
                )
            }
        }
    }

    @Test
    fun runningBannerShowsLabelTimeAndButtons() {
        val calls = RestCalls()
        showRest(ui(), RestTimerState.Running(0L, 180, 150_000L, "PULL UP"), calls)

        rule.onNodeWithTag(WorkoutTags.REST_BANNER).assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.REST_LABEL).assertTextEquals("PULL UP")
        rule.onNodeWithTag(WorkoutTags.REST_TIME).assertTextEquals("2:30")
        rule.onNodeWithTag(WorkoutTags.REST_MINUS).performClick()
        rule.onNodeWithTag(WorkoutTags.REST_PLUS).performClick()
        rule.onNodeWithTag(WorkoutTags.REST_SKIP).performClick()
        assertEquals(listOf(-15, 15), calls.adjusts)
        assertEquals(1, calls.skips)
    }

    @Test
    fun finishedBannerShowsFermaAndStaysUntilTapped() {
        val calls = RestCalls()
        showRest(ui(), RestTimerState.Finished(180, "PULL UP"), calls)

        rule.onNodeWithText(context.getString(R.string.workout_rest_finished)).assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.REST_STOP).assertIsDisplayed()
        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithTag(WorkoutTags.REST_STOP).assertIsDisplayed()
        assertEquals(0, calls.stops)
        rule.onNodeWithTag(WorkoutTags.REST_STOP).performClick()
        assertEquals(1, calls.stops)
    }

    @Test
    fun noBannerWhenIdle() {
        showRest(ui(), RestTimerState.Idle)

        rule.onNodeWithTag(WorkoutTags.REST_BANNER).assertDoesNotExist()
    }

    @Test
    fun jumpSetCardShowsManualStartButtonWhileNotRunning() {
        val calls = RestCalls()
        val jump = PlannedExercise(2, 1, "ADDUCTOR MACHINE", 2, "8-10", "1-2", null, null)
        showRest(ui(exercise = jump), RestTimerState.Idle, calls)

        rule.onNodeWithText("Avvia recupero 1:00").assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.REST_START_MANUAL).performClick()
        assertEquals(listOf("ADDUCTOR MACHINE"), calls.manualStarts)
    }

    @Test
    fun manualStartButtonHiddenForExerciseWithRestAndWhileRunning() {
        showRest(ui(), RestTimerState.Idle)
        rule.onNodeWithTag(WorkoutTags.REST_START_MANUAL).assertDoesNotExist()
    }

    @Test
    fun manualStartButtonHiddenWhileTimerRunning() {
        val jump = PlannedExercise(2, 1, "ADDUCTOR MACHINE", 2, "8-10", "1-2", null, null)
        showRest(ui(exercise = jump), RestTimerState.Running(0L, 60, 60_000L, "x"))
        rule.onNodeWithTag(WorkoutTags.REST_START_MANUAL).assertDoesNotExist()
    }

    private fun uiWithLast(
        reps: List<Int>,
        form: SetFormValues = SetFormValues("", "", SetOutcome.BUFFER, 1, "30", "8"),
    ): WorkoutUiState.Ready {
        val ex = exercise.copy(targetReps = "6-8")
        val sets = reps.mapIndexed { i, r -> logged(i + 1, 30.0, r, SetOutcome.BUFFER, 1) }
        val startedAt = java.time.ZonedDateTime.now().withMonth(1).withDayOfMonth(26).withHour(12)
            .toInstant().toEpochMilli()
        val base = ui(exercise = ex, form = form).exercises.first()
        return WorkoutUiState.Ready(
            "FULL BODY 1",
            listOf(
                base.copy(
                    lastPerformance = com.trackmygymgains.data.repo.LastPerformance(startedAt, sets),
                    suggestIncrease = SetInput.shouldSuggestIncrease(ex, sets),
                ),
            ),
        )
    }

    @Test
    fun lastPerformanceLineAndIncreaseHintShownWhenAllAtMax() {
        show(uiWithLast(listOf(8, 8, 8)))

        rule.onNodeWithTag(WorkoutTags.LAST_PERFORMANCE)
            .assertTextEquals("Ultima volta · 26 gen: 30 kg × 8 / 8 / 8 · RIR 1")
        rule.onNodeWithTag(WorkoutTags.INCREASE_HINT).assertIsDisplayed()
        rule.onNodeWithText("Hai raggiunto il massimo delle ripetizioni: prova ad aumentare il carico")
            .assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).assertTextContains("30")
    }

    @Test
    fun lastPerformanceShownWithoutHintWhenBelowMax() {
        show(uiWithLast(listOf(8, 7, 8), SetFormValues("", "", SetOutcome.BUFFER, 1, "30", "8")))

        rule.onNodeWithTag(WorkoutTags.LAST_PERFORMANCE).assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.INCREASE_HINT).assertDoesNotExist()
        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).assertTextContains("30")
    }

    @Test
    fun emptyFieldsSubmitSuggestedValues() {
        val submitted = Submitted()
        show(ui(form = SetFormValues("", "", SetOutcome.BUFFER, 1, "70", "9")), submitted)

        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).assertTextContains("70")
        rule.onNodeWithTag(WorkoutTags.REPS_FIELD).assertTextContains("9")
        rule.onNodeWithTag(WorkoutTags.SUBMIT).assertIsEnabled().performClick()

        assertEquals(listOf(listOf(1L, 1, 70.0, 9, SetOutcome.BUFFER, 1)), submitted.calls)
    }

    @Test
    fun typedValuesOverrideSuggestion() {
        val submitted = Submitted()
        show(ui(form = SetFormValues("", "", SetOutcome.BUFFER, 1, "70", "9")), submitted)

        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).performTextInput("72,5")
        rule.onNodeWithTag(WorkoutTags.SUBMIT).performClick()

        assertEquals(listOf(listOf(1L, 1, 72.5, 9, SetOutcome.BUFFER, 1)), submitted.calls)
    }

    @Test
    fun editingWithoutTypingKeepsLoggedValues() {
        val submitted = Submitted()
        show(ui(logged = listOf(logged(1, 80.0, 8, SetOutcome.BUFFER, 2)), editing = 1), submitted)

        rule.onNodeWithTag(WorkoutTags.SUBMIT).performClick()

        assertEquals(listOf(listOf(1L, 1, 80.0, 8, SetOutcome.BUFFER, 2)), submitted.calls)
    }

    @Test
    fun incrementChipsChangeSuggestionAndToggle() {
        val submitted = Submitted()
        show(uiWithLast(listOf(8, 8, 8)), submitted)

        rule.onNodeWithTag(WorkoutTags.INCREASE_HINT).assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.INCREMENT_1).assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.INCREMENT_2_5).assertIsDisplayed()
        rule.onNodeWithTag(WorkoutTags.INCREMENT_5).assertIsDisplayed()
        rule.onNodeWithText("+2,5 kg").assertIsDisplayed()

        // typed text is cleared when a chip is selected
        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).performTextInput("99")
        rule.onNodeWithTag(WorkoutTags.INCREMENT_5).performScrollTo().performClick()
        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).assertTextContains("35")
        rule.onNodeWithTag(WorkoutTags.SUBMIT).performScrollTo().performClick()
        assertEquals(listOf(listOf(1L, 1, 35.0, 8, SetOutcome.BUFFER, 1)), submitted.calls)

        // tapping again deselects: back to the history load
        rule.onNodeWithTag(WorkoutTags.INCREMENT_5).performScrollTo().performClick()
        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).assertTextContains("30")
        rule.onNodeWithTag(WorkoutTags.SUBMIT).performScrollTo().performClick()
        assertEquals(listOf(1L, 1, 30.0, 8, SetOutcome.BUFFER, 1), submitted.calls.last())

        // switching chips
        rule.onNodeWithTag(WorkoutTags.INCREMENT_1).performScrollTo().performClick()
        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).assertTextContains("31")
        rule.onNodeWithTag(WorkoutTags.INCREMENT_2_5).performScrollTo().performClick()
        rule.onNodeWithTag(WorkoutTags.LOAD_FIELD).assertTextContains("32,5")
    }

    @Test
    fun incrementChipsHiddenOnceASetIsLogged() {
        val base = uiWithLast(listOf(8, 8, 8))
        val ex = base.exercises.first()
        val state = WorkoutUiState.Ready(
            base.dayName,
            listOf(
                ex.copy(
                    loggedSets = listOf(logged(1, 30.0, 8, SetOutcome.BUFFER, 1)),
                    currentSet = 2,
                    currentForm = SetFormValues("", "", SetOutcome.BUFFER, 1, "30", "8"),
                ),
            ),
        )
        show(state)

        rule.onNodeWithTag(WorkoutTags.INCREMENT_1).assertDoesNotExist()
        rule.onNodeWithTag(WorkoutTags.INCREMENT_2_5).assertDoesNotExist()
        rule.onNodeWithTag(WorkoutTags.INCREMENT_5).assertDoesNotExist()
    }

    @Test
    fun noIncrementChipsWithoutSuggestion() {
        show(uiWithLast(listOf(8, 7, 8)))

        rule.onNodeWithTag(WorkoutTags.INCREMENT_5).assertDoesNotExist()
    }

    @Test
    fun noLastPerformanceLineWithoutHistory() {
        show(ui())

        rule.onNodeWithTag(WorkoutTags.LAST_PERFORMANCE).assertDoesNotExist()
        rule.onNodeWithTag(WorkoutTags.INCREASE_HINT).assertDoesNotExist()
    }
}
