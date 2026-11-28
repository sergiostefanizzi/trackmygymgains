package com.trackmygymgains

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.testing.InMemoryContainerRule
import com.trackmygymgains.testing.REFERENCE_SHEET_NAME
import com.trackmygymgains.testing.referencePlanExpected
import com.trackmygymgains.ui.importplan.ImportTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityRobolectricTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val containerRule = InMemoryContainerRule()
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    @Test
    fun withoutPlanShowsOnboarding() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTagCount(ImportTags.PICK_FILE) > 0
        }
        composeRule.onNodeWithTag(ImportTags.PICK_FILE).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.import_pick_file)).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}

@RunWith(AndroidJUnit4::class)
class MainActivityWithPlanRobolectricTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val containerRule = InMemoryContainerRule(
        ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList()),
    )
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    @Test
    fun withPlanShowsHomeWithDays() {
        val first = referencePlanExpected.first()
        val summary = context.getString(
            R.string.home_day_summary,
            first.name,
            context.resources.getQuantityString(R.plurals.exercises_count, first.exercises.size, first.exercises.size),
        )
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(summary)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(summary).assertIsDisplayed()
        composeRule.onNodeWithText("FULL BODY 1 · 7 esercizi").assertIsDisplayed()
    }
}
