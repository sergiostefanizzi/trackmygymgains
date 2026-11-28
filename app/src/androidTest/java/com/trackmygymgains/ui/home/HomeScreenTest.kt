package com.trackmygymgains.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackmygymgains.R
import com.trackmygymgains.data.repo.PlannedExercise
import com.trackmygymgains.data.repo.WorkoutDay
import com.trackmygymgains.data.repo.WorkoutPlan
import com.trackmygymgains.ui.theme.TrackMyGymGainsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun exercise(i: Int) = PlannedExercise(i.toLong(), i, "EX $i", 3, "8", "1", 120, null)

    private val plan = WorkoutPlan(
        id = 1,
        name = "Scheda test",
        sheetName = "Scheda test",
        sourceFileName = null,
        importedAt = 0,
        days = listOf(
            WorkoutDay(1, "FULL BODY 1", 0, (0 until 7).map(::exercise)),
            WorkoutDay(2, "FULL BODY 2", 1, listOf(exercise(10))),
        ),
    )

    private val openSession = OpenSessionInfo(sessionId = 42, dayName = "FULL BODY 2", startedAt = 0L)

    private class Calls {
        var imported = 0
        val started = mutableListOf<Long>()
        val resumed = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()
    }

    private fun setContent(state: HomeUiState, calls: Calls) {
        rule.setContent {
            TrackMyGymGainsTheme {
                HomeScreen(
                    state = state,
                    onImportNew = { calls.imported++ },
                    onOpenHistory = {},
                    onStartDay = { calls.started += it },
                    onResumeSession = { calls.resumed += it },
                    onCancelSession = { calls.cancelled += it },
                )
            }
        }
    }

    @Test
    fun showsPlanNameDaysAndImportMenu() {
        val calls = Calls()
        setContent(HomeUiState.Content(plan), calls)

        rule.onNodeWithText("Scheda test").assertIsDisplayed()
        rule.onNodeWithText("FULL BODY 1 · 7 esercizi").assertIsDisplayed()
        rule.onNodeWithText("FULL BODY 2 · 1 esercizio").assertIsDisplayed()

        rule.onNodeWithTag(HomeTags.MENU).performClick()
        rule.onNodeWithText(context.getString(R.string.home_import_new)).performClick()
        assertEquals(1, calls.imported)
    }

    @Test
    fun startButtonsStartTheirDayWhenNoSessionIsOpen() {
        val calls = Calls()
        setContent(HomeUiState.Content(plan), calls)

        rule.onNodeWithTag(HomeTags.startTag(1)).assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithTag(HomeTags.startTag(2)).assertIsEnabled()
        rule.onNodeWithTag(HomeTags.OPEN_SESSION).assertDoesNotExist()

        rule.onNodeWithTag(HomeTags.startTag(2)).performClick()
        assertEquals(listOf(2L), calls.started)
    }

    @Test
    fun openSessionCardShowsDayNameStartTimeAndResumes() {
        val calls = Calls()
        setContent(HomeUiState.Content(plan, openSession), calls)

        rule.onNodeWithTag(HomeTags.OPEN_SESSION).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.home_open_session_title)).assertIsDisplayed()
        rule.onNodeWithText(
            context.getString(
                R.string.home_open_session_summary,
                "FULL BODY 2",
                formatStartTime(openSession.startedAt),
            ),
        ).assertIsDisplayed()

        rule.onNodeWithTag(HomeTags.RESUME).performClick()
        assertEquals(listOf(42L), calls.resumed)
    }

    @Test
    fun cancelAsksForConfirmationBeforeDiscarding() {
        val calls = Calls()
        setContent(HomeUiState.Content(plan, openSession), calls)

        rule.onNodeWithTag(HomeTags.CANCEL_SESSION).performClick()
        rule.onNodeWithText(context.getString(R.string.home_cancel_dialog_title)).assertIsDisplayed()
        rule.onNodeWithTag(HomeTags.CANCEL_DISMISS).performClick()
        assertEquals(emptyList<Long>(), calls.cancelled)

        rule.onNodeWithTag(HomeTags.CANCEL_SESSION).performClick()
        rule.onNodeWithTag(HomeTags.CANCEL_CONFIRM).performClick()
        assertEquals(listOf(42L), calls.cancelled)
    }

    @Test
    fun startButtonsAreDisabledWhileSessionIsOpen() {
        val calls = Calls()
        setContent(HomeUiState.Content(plan, openSession), calls)

        rule.onNodeWithTag(HomeTags.startTag(1)).assertIsNotEnabled()
        rule.onNodeWithTag(HomeTags.startTag(2)).assertIsNotEnabled()
        rule.onNodeWithTag(HomeTags.START_DISABLED_HINT).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.home_start_disabled_hint)).assertIsDisplayed()
    }
}
