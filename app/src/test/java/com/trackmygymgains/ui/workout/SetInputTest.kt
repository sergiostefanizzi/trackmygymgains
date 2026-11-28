package com.trackmygymgains.ui.workout

import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.data.repo.PlannedExercise
import com.trackmygymgains.data.repo.LastPerformance
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetInputTest {

    private fun load(text: String) = SetInput.parseLoad(text)

    private fun loadError(text: String): InputError = (load(text) as InputResult.Invalid).error

    private fun repsError(text: String): InputError = (SetInput.parseReps(text) as InputResult.Invalid).error

    @Test
    fun loadAcceptsCommaAndDot() {
        assertEquals(InputResult.Valid(82.5), load("82,5"))
        assertEquals(InputResult.Valid(82.5), load("82.5"))
        assertEquals(InputResult.Valid(80.0), load(" 80 "))
        assertEquals(InputResult.Valid(0.5), load(",5"))
        assertEquals(InputResult.Valid(80.0), load("80,"))
        assertEquals(InputResult.Valid(12.25), load("12,25"))
    }

    @Test
    fun loadZeroIsAllowedForBodyweight() {
        assertEquals(InputResult.Valid(0.0), load("0"))
        assertEquals(InputResult.Valid(0.0), load("0,00"))
    }

    @Test
    fun loadRejectsInvalidStrings() {
        assertEquals(InputError.EMPTY, loadError(""))
        assertEquals(InputError.EMPTY, loadError("   "))
        for (bad in listOf("abc", "-5", "-0", "1,2,3", "1.2.3", "8 0", "1e3", ",", ".", "+5")) {
            assertEquals(bad, InputError.NOT_A_NUMBER, loadError(bad))
        }
    }

    @Test
    fun loadRejectsMoreThanTwoDecimals() {
        assertEquals(InputError.TOO_MANY_DECIMALS, loadError("80,123"))
        assertEquals(InputError.TOO_MANY_DECIMALS, loadError("80.500"))
    }

    @Test
    fun loadUpperBound() {
        assertEquals(InputResult.Valid(1000.0), load("1000"))
        assertEquals(InputError.OUT_OF_RANGE, loadError("1000,01"))
        assertEquals(InputError.OUT_OF_RANGE, loadError("5000"))
    }

    @Test
    fun repsRules() {
        assertEquals(InputResult.Valid(1), SetInput.parseReps("1"))
        assertEquals(InputResult.Valid(8), SetInput.parseReps(" 8 "))
        assertEquals(InputResult.Valid(999), SetInput.parseReps("999"))
        assertEquals(InputError.EMPTY, repsError(""))
        assertEquals(InputError.OUT_OF_RANGE, repsError("0"))
        assertEquals(InputError.OUT_OF_RANGE, repsError("1000"))
        assertEquals(InputError.OUT_OF_RANGE, repsError("99999999999"))
        for (bad in listOf("-1", "6,5", "6.5", "abc", "1 2")) {
            assertEquals(bad, InputError.NOT_A_NUMBER, repsError(bad))
        }
    }

    @Test
    fun formatLoadUsesCommaAndNoTrailingZeros() {
        assertEquals("80", SetInput.formatLoad(80.0))
        assertEquals("82,5", SetInput.formatLoad(82.5))
        assertEquals("0", SetInput.formatLoad(0.0))
        assertEquals("12,25", SetInput.formatLoad(12.25))
        assertEquals("1000", SetInput.formatLoad(1000.0))
    }

    @Test
    fun defaultRirIsFirstIntegerOfTarget() {
        assertEquals(1, SetInput.defaultRir("1-2"))
        assertEquals(1, SetInput.defaultRir("1-2 / MAX REPS"))
        assertEquals(0, SetInput.defaultRir("0"))
        assertEquals(2, SetInput.defaultRir(null))
        assertEquals(2, SetInput.defaultRir("MAX"))
        assertEquals(10, SetInput.defaultRir("15"))
    }

    private val exercise = PlannedExercise(1, 0, "PULL UP", 3, "4-6", "1-2", 180, null)

    private fun set(n: Int, load: Double, reps: Int, outcome: SetOutcome, rir: Int?) =
        LoggedSet(n.toLong(), 1, n, load, reps, outcome, rir, 0)

    @Test
    fun prefillPrefersPreviousSetInSession() {
        val inSession = listOf(set(1, 80.0, 8, SetOutcome.BUFFER, 2))
        val last = listOf(set(1, 70.0, 10, SetOutcome.FAILURE, null), set(2, 70.0, 9, SetOutcome.FAILURE, null))
        assertEquals(SetFormValues("80", "8", SetOutcome.BUFFER, 2), SetInput.prefill(exercise, 2, inSession, last))
    }

    @Test
    fun prefillFallsBackToLastPerformanceSameSetThenLast() {
        val last = listOf(set(1, 70.0, 10, SetOutcome.BUFFER, 3), set(2, 72.5, 9, SetOutcome.FAILURE, null))
        assertEquals(SetFormValues("70", "10", SetOutcome.BUFFER, 3), SetInput.prefill(exercise, 1, emptyList(), last))
        assertEquals(SetFormValues("72,5", "9", SetOutcome.FAILURE, 1), SetInput.prefill(exercise, 2, emptyList(), last))
        assertEquals(SetFormValues("72,5", "9", SetOutcome.FAILURE, 1), SetInput.prefill(exercise, 3, emptyList(), last))
    }

    @Test
    fun prefillWithoutSourceIsEmptyBufferWithTargetRir() {
        assertEquals(SetFormValues("", "", SetOutcome.BUFFER, 1), SetInput.prefill(exercise, 1, emptyList(), emptyList()))
        assertEquals(
            SetFormValues("", "", SetOutcome.BUFFER, 2),
            SetInput.prefill(exercise.copy(targetRir = null), 1, emptyList(), emptyList()),
        )
    }

    @Test
    fun repsUpperBoundParsing() {
        assertEquals(8, SetInput.repsUpperBound("6-8"))
        assertEquals(10, SetInput.repsUpperBound("8-10 "))
        assertEquals(11, SetInput.repsUpperBound("7-11"))
        assertEquals(8, SetInput.repsUpperBound("8"))
        assertEquals(8, SetInput.repsUpperBound("6–8"))
        assertNull(SetInput.repsUpperBound("MAX"))
        assertNull(SetInput.repsUpperBound(null))
        assertNull(SetInput.repsUpperBound(""))
    }

    private val ex8 = exercise.copy(sets = 3, targetReps = "6-8")

    @Test
    fun suggestionWhenAllSetsAtMax() {
        val last = listOf(8, 8, 9).mapIndexed { i, r -> set(i + 1, 30.0, r, SetOutcome.BUFFER, 1) }
        assertTrue(SetInput.shouldSuggestIncrease(ex8, last))
    }

    @Test
    fun noSuggestionWhenOneSetBelowFewerSetsNoBoundOrNoHistory() {
        val below = listOf(8, 7, 8).mapIndexed { i, r -> set(i + 1, 30.0, r, SetOutcome.BUFFER, 1) }
        assertFalse(SetInput.shouldSuggestIncrease(ex8, below))
        val fewer = listOf(8, 8).mapIndexed { i, r -> set(i + 1, 30.0, r, SetOutcome.BUFFER, 1) }
        assertFalse(SetInput.shouldSuggestIncrease(ex8, fewer))
        val ok = listOf(8, 8, 8).mapIndexed { i, r -> set(i + 1, 30.0, r, SetOutcome.BUFFER, 1) }
        assertFalse(SetInput.shouldSuggestIncrease(ex8.copy(targetReps = "MAX"), ok))
        assertFalse(SetInput.shouldSuggestIncrease(ex8.copy(targetReps = null), ok))
        assertFalse(SetInput.shouldSuggestIncrease(ex8, emptyList()))
    }

    @Test
    fun prefillIncrementsOnlyWhenFromLastPerformance() {
        val last = listOf(set(1, 30.0, 8, SetOutcome.BUFFER, 1), set(2, 30.0, 8, SetOutcome.BUFFER, 1))
        assertEquals("32,5", SetInput.prefill(exercise, 1, emptyList(), last, true).loadText)
        assertEquals("32,5", SetInput.prefill(exercise, 3, emptyList(), last, true).loadText) // last set
        assertEquals("30", SetInput.prefill(exercise, 1, emptyList(), last, false).loadText)
        val inSession = listOf(set(1, 32.5, 8, SetOutcome.BUFFER, 1))
        assertEquals("32,5", SetInput.prefill(exercise, 2, inSession, last, true).loadText)
        val heavy = listOf(set(1, 999.0, 8, SetOutcome.BUFFER, 1))
        assertEquals("1000", SetInput.prefill(exercise, 1, emptyList(), heavy, true).loadText)
        assertEquals(SetFormValues("", "", SetOutcome.BUFFER, 1), SetInput.prefill(exercise, 1, emptyList(), emptyList(), true))
    }

    private val labels = SetInput.LastLabels(
        line = "Ultima volta · %1${'$'}s: %2${'$'}s · %3${'$'}s",
        loadsReps = "%1${'$'}s kg × %2${'$'}s",
        rir = "RIR %1${'$'}s",
        failure = "Cedimento",
        failureShort = "C",
        notAvailable = "n/d",
    )
    private val rome = ZoneId.of("Europe/Rome")
    private fun at(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 12, 0, 0, 0, rome).toInstant().toEpochMilli()
    private fun line(sets: List<LoggedSet>, year: Int = 2026, date: Long = at(2026, 10, 26)) =
        SetInput.lastPerformanceLine(LastPerformance(date, sets), labels, rome, year)

    @Test
    fun lineWithSameLoadAndRir() {
        val sets = (1..3).map { set(it, 30.0, 8, SetOutcome.BUFFER, 1) }
        assertEquals("Ultima volta · 26 ott: 30 kg × 8 / 8 / 8 · RIR 1", line(sets))
    }

    @Test
    fun lineWithMixedLoads() {
        val sets = listOf(
            set(1, 35.0, 4, SetOutcome.BUFFER, 1), set(2, 30.0, 5, SetOutcome.BUFFER, 1), set(3, 25.0, 7, SetOutcome.BUFFER, 1),
        )
        assertEquals("Ultima volta · 26 ott: 35 / 30 / 25 kg × 4 / 5 / 7 · RIR 1", line(sets))
        assertTrue(line(listOf(set(1, 32.5, 4, SetOutcome.BUFFER, 1))).contains("32,5 kg"))
    }

    @Test
    fun lineWithMixedRirCedimentoAndNd() {
        val mixed = listOf(
            set(1, 30.0, 8, SetOutcome.BUFFER, 1), set(2, 30.0, 8, SetOutcome.BUFFER, 1), set(3, 30.0, 6, SetOutcome.FAILURE, null),
        )
        assertTrue(line(mixed).endsWith("· RIR 1 / 1 / C"))
        val allFailure = (1..2).map { set(it, 30.0, 8, SetOutcome.FAILURE, null) }
        assertTrue(line(allFailure).endsWith("· Cedimento"))
        val allNd = (1..2).map { set(it, 30.0, 8, SetOutcome.BUFFER, null) }
        assertTrue(line(allNd).endsWith("· RIR n/d"))
        val ndMixed = listOf(set(1, 30.0, 8, SetOutcome.BUFFER, null), set(2, 30.0, 8, SetOutcome.BUFFER, 2))
        assertTrue(line(ndMixed).endsWith("· RIR n/d / 2"))
    }

    @Test
    fun lineShowsYearOnlyWhenDifferentFromCurrent() {
        val sets = listOf(set(1, 30.0, 8, SetOutcome.BUFFER, 1))
        assertTrue(line(sets, year = 2026).startsWith("Ultima volta · 26 ott:"))
        assertTrue(line(sets, year = 2026, date = at(2025, 10, 26)).startsWith("Ultima volta · 26 ott 2025:"))
    }
}
