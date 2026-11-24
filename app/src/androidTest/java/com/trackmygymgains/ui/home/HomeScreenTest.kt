package com.trackmygymgains.ui.home

import androidx.compose.ui.test.assertIsDisplayed
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

    @Test
    fun showsPlanNameDaysAndImportMenu() {
        var imported = 0
        rule.setContent {
            TrackMyGymGainsTheme {
                HomeScreen(HomeUiState.Content(plan), onImportNew = { imported++ })
            }
        }

        rule.onNodeWithText("Scheda test").assertIsDisplayed()
        rule.onNodeWithText("FULL BODY 1 · 7 esercizi").assertIsDisplayed()
        rule.onNodeWithText("FULL BODY 2 · 1 esercizio").assertIsDisplayed()

        rule.onNodeWithTag(HomeTags.MENU).performClick()
        rule.onNodeWithText(context.getString(R.string.home_import_new)).performClick()
        assertEquals(1, imported)
    }
}
