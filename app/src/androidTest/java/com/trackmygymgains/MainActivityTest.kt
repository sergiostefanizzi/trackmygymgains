package com.trackmygymgains

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.testing.InMemoryContainerRule
import com.trackmygymgains.testing.REFERENCE_SHEET_NAME
import com.trackmygymgains.testing.referencePlanExpected
import com.trackmygymgains.ui.home.HomeTags
import com.trackmygymgains.ui.importplan.ImportTags
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private val containerRule = InMemoryContainerRule()
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    @Test
    fun withoutPlanShowsOnboarding() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag(ImportTags.PICK_FILE)).fetchSemanticsNodes().isNotEmpty()
        }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.import_pick_file)
        composeRule.onNodeWithText(label).assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class MainActivityWithPlanTest {

    private val containerRule = InMemoryContainerRule(
        ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList()),
    )
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun withPlanShowsHomeAndCanOpenImport() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("FULL BODY 1", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("FULL BODY 1 · 7 esercizi").assertIsDisplayed()

        composeRule.onNodeWithTag(HomeTags.MENU).performClick()
        composeRule.onNodeWithTag(HomeTags.IMPORT_NEW).performClick()
        composeRule.onNodeWithTag(ImportTags.PICK_FILE).assertIsDisplayed()
    }

    @Test
    fun startWorkoutBackToHomeResumeThenCancel() {
        val placeholder = context.getString(R.string.workout_placeholder)
        val back = context.getString(R.string.workout_back)
        val start = context.getString(R.string.home_start_workout)

        waitForText("FULL BODY 1 · 7 esercizi")
        composeRule.onAllNodesWithText(start).onFirst().performClick()

        waitForText(placeholder)
        composeRule.onNodeWithText("FULL BODY 1").assertIsDisplayed()

        composeRule.onNodeWithContentDescription(back).performClick()
        waitForTag(HomeTags.OPEN_SESSION)
        composeRule.onNodeWithTag(HomeTags.OPEN_SESSION).assertIsDisplayed()

        composeRule.onNodeWithTag(HomeTags.RESUME).performClick()
        waitForText(placeholder)

        composeRule.onNodeWithContentDescription(back).performClick()
        waitForTag(HomeTags.CANCEL_SESSION)
        composeRule.onNodeWithTag(HomeTags.CANCEL_SESSION).performClick()
        composeRule.onNodeWithTag(HomeTags.CANCEL_CONFIRM).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag(HomeTags.OPEN_SESSION)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText(start).onFirst().assertIsEnabled()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
