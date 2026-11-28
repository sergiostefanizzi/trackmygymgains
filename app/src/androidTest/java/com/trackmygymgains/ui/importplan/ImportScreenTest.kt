package com.trackmygymgains.ui.importplan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackmygymgains.R
import com.trackmygymgains.data.plan.ParseWarning
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.testing.OLD_SHEET_NAME
import com.trackmygymgains.testing.REFERENCE_SHEET_NAME
import com.trackmygymgains.testing.referencePlanExpected
import com.trackmygymgains.ui.theme.TrackMyGymGainsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun preview(
        sheets: List<String> = listOf(REFERENCE_SHEET_NAME),
        selected: Int = 0,
        plan: ParsedPlan? = ParsedPlan(REFERENCE_SHEET_NAME, referencePlanExpected, emptyList()),
        sheetError: String? = null,
        warnings: List<ParseWarning> = emptyList(),
    ) = ImportUiState.Preview("scheda.xlsx", sheets, selected, plan, sheetError, warnings)

    private fun show(
        state: ImportUiState,
        replaces: Boolean = false,
        onPick: () -> Unit = {},
        onSelectSheet: (Int) -> Unit = {},
        onConfirm: () -> Unit = {},
    ) {
        rule.setContent {
            TrackMyGymGainsTheme {
                ImportScreen(
                    state = state,
                    replacesExisting = replaces,
                    canNavigateBack = false,
                    onPickFile = onPick,
                    onSelectSheet = onSelectSheet,
                    onConfirm = onConfirm,
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun idleShowsIntroAndPickButton() {
        var picked = 0
        show(ImportUiState.Idle, onPick = { picked++ })

        rule.onNodeWithText(str(R.string.import_intro)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.import_pick_file)).assertIsDisplayed().performClick()
        assertEquals(1, picked)
    }

    @Test
    fun previewShowsDaysExercisesRestAndNotes() {
        show(preview())

        rule.onNodeWithText(str(R.string.import_file_name, "scheda.xlsx")).assertIsDisplayed()
        referencePlanExpected.forEach { rule.onNodeWithText(it.name).performScrollTo().assertIsDisplayed() }
        rule.onNodeWithText("PULL UP").performScrollTo().assertIsDisplayed()
        rule.onAllNodes(hasText("3 × 4-6", substring = true)).onFirst().performScrollTo().assertIsDisplayed()
        rule.onAllNodes(hasText(str(R.string.exercise_rir, "1-2"), substring = true)).onFirst().assertExists()
        rule.onAllNodes(hasText(str(R.string.exercise_rest, "3:00"), substring = true)).onFirst().assertExists()
        rule.onNodeWithText("NO ELASTICO - CLASSICO").assertExists()
        // ADDUCTOR MACHINE is a jump set: no rest. There are 4 jump sets in the reference plan.
        rule.onNodeWithText("ADDUCTOR MACHINE").performScrollTo().assertIsDisplayed()
        rule.onAllNodes(hasText(str(R.string.exercise_no_rest), substring = true)).assertCountEquals(4)
        rule.onNodeWithTag(ImportTags.SHEET_SELECTOR).assertDoesNotExist()
    }

    @Test
    fun warningsAreShownInCard() {
        show(preview(warnings = listOf(ParseWarning(7, "Qualcosa non va"))))

        rule.onNodeWithTag(ImportTags.WARNINGS).assertExists()
        rule.onNodeWithText(str(R.string.import_warning_row, 7, "Qualcosa non va")).assertExists()
    }

    @Test
    fun sheetSelectorAppearsWithTwoSheetsAndSwitchingCallsBack() {
        var selected = -1
        show(
            preview(sheets = listOf(REFERENCE_SHEET_NAME, OLD_SHEET_NAME)),
            onSelectSheet = { selected = it },
        )

        rule.onNodeWithTag(ImportTags.SHEET_SELECTOR).assertIsDisplayed().performClick()
        rule.onNodeWithTag(ImportTags.SHEET_OPTION_PREFIX + 1).assertIsDisplayed().performClick()
        assertEquals(1, selected)
    }

    @Test
    fun sheetErrorKeepsSelectorAndDisablesConfirm() {
        show(
            preview(
                sheets = listOf(REFERENCE_SHEET_NAME, OLD_SHEET_NAME),
                plan = null,
                sheetError = "Nessun giorno di allenamento trovato",
            ),
        )

        rule.onNodeWithTag(ImportTags.SHEET_ERROR).assertIsDisplayed()
        rule.onNodeWithText("Nessun giorno di allenamento trovato").assertIsDisplayed()
        rule.onNodeWithTag(ImportTags.SHEET_SELECTOR).assertIsDisplayed()
        rule.onNodeWithTag(ImportTags.CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun confirmInvokesCallback() {
        var confirmed = 0
        show(preview(), onConfirm = { confirmed++ })

        rule.onNodeWithTag(ImportTags.CONFIRM).assertIsEnabled().performClick()
        assertEquals(1, confirmed)
        rule.onNodeWithTag(ImportTags.REPLACE_HINT).assertDoesNotExist()
    }

    @Test
    fun replacingExistingPlanIsExplained() {
        show(preview(), replaces = true)

        rule.onNodeWithText(str(R.string.import_confirm_replace)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.import_replace_hint)).assertIsDisplayed()
    }

    @Test
    fun pickOtherFileInvokesCallback() {
        var picked = 0
        show(preview(), onPick = { picked++ })

        rule.onNodeWithTag(ImportTags.PICK_OTHER_FILE).performClick()
        assertEquals(1, picked)
    }

    @Test
    fun errorShowsMessageAndRetryButton() {
        var picked = 0
        show(ImportUiState.Error("Messaggio di errore"), onPick = { picked++ })

        rule.onNodeWithText("Messaggio di errore").assertIsDisplayed()
        rule.onNodeWithTag(ImportTags.PICK_OTHER_FILE).performClick()
        assertEquals(1, picked)
    }
}
