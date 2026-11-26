package com.trackmygymgains.ui.workout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackmygymgains.R
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.data.repo.PlannedExercise
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
    }

    private fun show(state: WorkoutUiState, submitted: Submitted = Submitted()) {
        rule.setContent {
            TrackMyGymGainsTheme {
                WorkoutScreen(
                    state = state,
                    onBack = {},
                    onSubmitSet = { id, n, l, r, o, rir -> submitted.calls += listOf(id, n, l, r, o, rir) },
                    onEditSet = { id, n -> submitted.edits += id to n },
                )
            }
        }
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
}
