package com.trackmygymgains

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
}
