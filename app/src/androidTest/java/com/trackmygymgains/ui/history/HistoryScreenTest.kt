package com.trackmygymgains.ui.history

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackmygymgains.ui.theme.TrackMyGymGainsTheme
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val friday = Instant.parse("2026-11-27T18:30:00Z").toEpochMilli()

    private val items = listOf(
        HistoryItem(
            sessionId = 1,
            dayName = "FULL BODY 1",
            startedAt = friday,
            durationMillis = 65 * 60_000L,
            doneSets = 2,
            plannedSets = 18,
        ),
        HistoryItem(
            sessionId = 2,
            dayName = "FULL BODY 2",
            startedAt = friday - 86_400_000L,
            durationMillis = 30 * 60_000L,
            doneSets = 3,
            plannedSets = null,
        ),
    )

    private class Calls {
        val opened = mutableListOf<Long>()
        val deleted = mutableListOf<Long>()
        var back = 0
    }

    private fun setContent(state: HistoryUiState, calls: Calls) {
        rule.setContent {
            TrackMyGymGainsTheme {
                HistoryScreen(
                    state = state,
                    onBack = { calls.back++ },
                    onOpenSummary = { calls.opened += it },
                    onDelete = { calls.deleted += it },
                    zone = ZoneOffset.UTC,
                    locale = Locale.ITALIAN,
                )
            }
        }
    }

    @Test
    fun showsDateDayDurationAndSetsOfEachCard() {
        setContent(HistoryUiState.Content(items), Calls())

        rule.onAllNodes(hasTestTag(HistoryTags.CARD)).assertCountEquals(2)
        rule.onNodeWithText("ven 27 nov · 18:30").assertIsDisplayed()
        rule.onNodeWithText("FULL BODY 1").assertIsDisplayed()
        rule.onNodeWithText("Durata: 1 h 05 min").assertIsDisplayed()
        rule.onNodeWithText("2/18 serie").assertIsDisplayed()
        rule.onNodeWithText("FULL BODY 2").assertIsDisplayed()
        rule.onNodeWithText("3 serie").assertIsDisplayed()
    }

    @Test
    fun importedCardShowsDateOnlyImportedLabelAndNoDuration() {
        val imported = HistoryItem(
            sessionId = 3,
            dayName = "FULL BODY 3",
            startedAt = Instant.parse("2026-10-13T12:00:00Z").toEpochMilli(),
            durationMillis = 0,
            doneSets = 4,
            plannedSets = 9,
            imported = true,
        )
        setContent(HistoryUiState.Content(listOf(imported)), Calls())

        rule.onNodeWithText("mar 13 ott 2026").assertIsDisplayed()
        rule.onNodeWithText("Importato").assertIsDisplayed()
        rule.onNodeWithTag(HistoryTags.DURATION).assertDoesNotExist()
        rule.onNodeWithText("4/9 serie").assertIsDisplayed()
    }

    @Test
    fun appCardHasNoImportedLabel() {
        setContent(HistoryUiState.Content(items), Calls())

        rule.onNodeWithTag(HistoryTags.IMPORTED).assertDoesNotExist()
    }

    @Test
    fun emptyStateWhenNothingFinished() {
        setContent(HistoryUiState.Content(emptyList()), Calls())

        rule.onNodeWithTag(HistoryTags.EMPTY).assertIsDisplayed()
        rule.onNodeWithText("Nessun allenamento completato").assertIsDisplayed()
    }

    @Test
    fun tappingCardOpensItsSummary() {
        val calls = Calls()
        setContent(HistoryUiState.Content(items), calls)

        rule.onAllNodes(hasTestTag(HistoryTags.CARD))[0].performClick()
        rule.onAllNodes(hasTestTag(HistoryTags.CARD))[1].performClick()

        assertEquals(listOf(1L, 2L), calls.opened)
        assertEquals(0, calls.deleted.size)
    }

    @Test
    fun deleteDialogDismissKeepsSession() {
        val calls = Calls()
        setContent(HistoryUiState.Content(items), calls)

        rule.onAllNodes(hasTestTag(HistoryTags.DELETE)).onFirst().performClick()
        rule.onNodeWithText("Eliminare l'allenamento?").assertIsDisplayed()
        rule.onNodeWithText("Le serie registrate verranno cancellate.").assertIsDisplayed()
        rule.onNodeWithTag(HistoryTags.DELETE_DISMISS).performClick()

        rule.onAllNodes(hasTestTag(HistoryTags.DELETE_CONFIRM)).assertCountEquals(0)
        assertEquals(0, calls.deleted.size)
        assertEquals(0, calls.opened.size)
    }

    @Test
    fun deleteDialogConfirmDeletesThatSession() {
        val calls = Calls()
        setContent(HistoryUiState.Content(items), calls)

        rule.onAllNodes(hasTestTag(HistoryTags.DELETE)).onFirst().performClick()
        rule.onNodeWithTag(HistoryTags.DELETE_CONFIRM).performClick()

        assertEquals(listOf(1L), calls.deleted)
        assertEquals(0, calls.opened.size)
    }
}
