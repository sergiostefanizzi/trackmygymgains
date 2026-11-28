package com.trackmygymgains.data.plan

import com.trackmygymgains.data.xlsx.XlsxWorkbook
import com.trackmygymgains.testing.referencePlanExpected
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Validates the synthetic fixture against the user's real (gitignored) plan, when present. */
class RealPlanParseTest {
    @Test fun firstSheetMatchesFixtureExpectation() {
        val file = File(System.getProperty("user.dir"), "../references/scheda_gym.xlsx")
        assumeTrue(file.exists())
        val plan = file.inputStream().use { WorkoutPlanParser.parse(XlsxWorkbook.open(it), 0) }
        assertEquals(referencePlanExpected, plan.days)
        // History warnings (real data is messy) are allowed; plan warnings are not.
        assertEquals(emptyList<ParseWarning>(), plan.warnings.filterNot { isHistoryWarning(it) })
    }

    @Test fun historyIsReadFromRealFile() {
        val file = File(System.getProperty("user.dir"), "../references/scheda_gym.xlsx")
        assumeTrue(file.exists())
        val plan = file.inputStream().use { WorkoutPlanParser.parse(XlsxWorkbook.open(it), 0) }
        assertTrue(plan.history.isNotEmpty())
        assertTrue(plan.history.all { it.dayIndex in plan.days.indices && it.exercises.isNotEmpty() })
        assertTrue(plan.history.all { s -> s.exercises.all { it.exerciseIndex in plan.days[s.dayIndex].exercises.indices } })
        assertEquals(plan.history.sortedBy { it.date }.map { it.date }, plan.history.map { it.date })
    }

    private fun isHistoryWarning(w: ParseWarning) =
        "settimana" in w.message.lowercase()
}
