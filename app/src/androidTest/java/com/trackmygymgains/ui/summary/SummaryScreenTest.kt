package com.trackmygymgains.ui.summary

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.data.repo.PlannedExercise
import com.trackmygymgains.ui.theme.TrackMyGymGainsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SummaryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val pullUp = PlannedExercise(1, 0, "PULL UP", 3, "4-6", "1-2", 180, null)
    private val dips = PlannedExercise(2, 1, "DISTENSIONI", 2, "6-8", "1-2", 180, null)

    private fun state() = SummaryUiState.Ready(
        dayName = "FULL BODY 1",
        durationMillis = 65 * 60_000L,
        plannedSets = 5,
        doneSets = 2,
        exercises = listOf(
            SummaryExercise(
                pullUp,
                listOf(
                    LoggedSet(1, 1, 1, 82.5, 6, SetOutcome.BUFFER, 2, 0),
                    LoggedSet(2, 1, 2, 80.0, 5, SetOutcome.FAILURE, null, 0),
                ),
            ),
            SummaryExercise(dips, emptyList()),
        ),
    )

    @Test
    fun showsDayDurationSetsTotalAndExerciseLines() {
        rule.setContent { TrackMyGymGainsTheme { SummaryScreen(state = state(), onHome = {}) } }

        rule.onNodeWithText("FULL BODY 1").assertIsDisplayed()
        rule.onNodeWithTag(SummaryTags.DURATION).assertIsDisplayed()
        rule.onNodeWithText("Durata: 1 h 05 min").assertIsDisplayed()
        rule.onNodeWithText("2/5 serie").assertIsDisplayed()
        rule.onNodeWithText("PULL UP").assertIsDisplayed()
        rule.onNodeWithText("2/3 serie").assertIsDisplayed()
        rule.onNodeWithText("82,5 kg × 6 · Buffer RIR 2").assertIsDisplayed()
        rule.onNodeWithText("80 kg × 5 · Cedimento").assertIsDisplayed()
        rule.onNodeWithText("Nessuna serie registrata").assertIsDisplayed()
    }

    @Test
    fun homeButtonInvokesCallback() {
        var homeCalls = 0
        rule.setContent {
            TrackMyGymGainsTheme { SummaryScreen(state = state(), onHome = { homeCalls++ }) }
        }

        rule.onNodeWithTag(SummaryTags.HOME).performClick()

        assertEquals(1, homeCalls)
    }
}
