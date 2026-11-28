package com.trackmygymgains.data.plan

import com.trackmygymgains.data.xlsx.XlsxWorkbook
import com.trackmygymgains.testing.referencePlanExpected
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Validates the synthetic fixture against the user's real (gitignored) plan, when present. */
class RealPlanParseTest {
    @Test fun firstSheetMatchesFixtureExpectation() {
        val file = File(System.getProperty("user.dir"), "../references/scheda_gym.xlsx")
        assumeTrue(file.exists())
        val plan = file.inputStream().use { WorkoutPlanParser.parse(XlsxWorkbook.open(it), 0) }
        assertEquals(referencePlanExpected, plan.days)
        assertEquals(emptyList<ParseWarning>(), plan.warnings)
    }
}
