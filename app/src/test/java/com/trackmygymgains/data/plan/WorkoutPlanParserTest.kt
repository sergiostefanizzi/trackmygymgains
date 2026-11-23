package com.trackmygymgains.data.plan

import com.trackmygymgains.data.xlsx.XlsxWorkbook
import com.trackmygymgains.testing.OLD_SHEET_NAME
import com.trackmygymgains.testing.REFERENCE_SHEET_NAME
import com.trackmygymgains.testing.XlsxTestBuilder
import com.trackmygymgains.testing.referencePlanExpected
import com.trackmygymgains.testing.referenceSheetXlsx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkoutPlanParserTest {
    private fun parseBytes(bytes: ByteArray, sheet: Int = 0) =
        WorkoutPlanParser.parse(XlsxWorkbook.open(bytes.inputStream()), sheet)

    private fun single(build: XlsxTestBuilder.SheetBuilder.() -> Unit) =
        parseBytes(XlsxTestBuilder().sheet("S", build).build())

    @Test fun referenceFixtureParsesToExpected() {
        val plan = parseBytes(referenceSheetXlsx())
        assertEquals(REFERENCE_SHEET_NAME, plan.sheetName)
        assertEquals(referencePlanExpected, plan.days)
        assertEquals(emptyList<ParseWarning>(), plan.warnings)
    }

    @Test fun referenceDetails() {
        val days = parseBytes(referenceSheetXlsx()).days
        assertEquals(listOf(7, 8, 8), days.map { it.exercises.size })
        val pull = days[0].exercises[0]
        assertEquals(ParsedExercise("PULL UP", 3, "4-6", "1-2", 180, "NO ELASTICO - CLASSICO"), pull)
        val iliac = days[0].exercises[2]
        assertNull(iliac.notes)
        assertEquals("1-2 / MAX REP", iliac.targetRir)
        assertNull(days[1].exercises.first { it.name == "ADDUCTOR MACHINE" }.restSeconds)
        assertTrue(days[1].exercises.any { it.name == "CURL MANUBRI" })
        assertEquals("6-8", days[2].exercises.first { it.name == "RAW DIAMOND" }.targetReps)
        assertEquals(4, days[2].exercises.first { it.name == "ROMANIAN DEADLIFT" }.sets)
    }

    @Test fun secondSheetIsReadOnRequest() {
        val plan = parseBytes(referenceSheetXlsx(), 1)
        assertEquals(OLD_SHEET_NAME, plan.sheetName)
        assertEquals(listOf(ParsedDay("VECCHIO GIORNO", listOf(ParsedExercise("SQUAT", 5, "5", null, null, null)))), plan.days)
        assertEquals(1, plan.warnings.size)
    }

    @Test fun columnsLocatedByLabelInAnyOrder() {
        val plan = single {
            cell("B1", "GIORNO X")
            row(2, "NOTE", "ESERCIZIO", "RIR", "REST", "SET", null, "REP")
            row(3, "n1", "SQUAT", "2", "90\"", 4.0, null, "5")
        }
        assertEquals(
            listOf(ParsedDay("GIORNO X", listOf(ParsedExercise("SQUAT", 4, "5", "2", 90, "n1")))),
            plan.days,
        )
        assertTrue(plan.warnings.isEmpty())
    }

    @Test fun missingRestColumnGivesNullsAndWarning() {
        val plan = single {
            cell("A1", "D1")
            row(2, "ESERCIZIO", "SET", "TARGET REP")
            row(3, "SQUAT", 3.0, "5")
        }
        assertNull(plan.days[0].exercises[0].restSeconds)
        assertNull(plan.days[0].exercises[0].targetRir)
        assertNull(plan.days[0].exercises[0].notes)
        assertEquals(1, plan.warnings.size)
        assertTrue(plan.warnings[0].message.contains("REST"))
    }

    @Test fun invalidSetRowIsSkippedWithWarning() {
        val plan = single {
            cell("A1", "D1")
            row(2, "ESERCIZIO", "SET", "TARGET REP", "TARGET RIR", "REST", "NOTE")
            row(3, "A", 3.0, "5", "1", "2'", "/")
            row(4, "B", "boh", "5", "1", "2'", "/")
            row(5, "C", "1 + 2", "5", "1", "2'", "-")
            row(6, "D", "0", "5", "1", "2'", "/")
            row(7, "E", "3,0", "5", "1", "2'", "/")
        }
        assertEquals(listOf("A", "C", "E"), plan.days[0].exercises.map { it.name })
        assertEquals(listOf(3, 3, 3), plan.days[0].exercises.map { it.sets })
        assertEquals(listOf(4, 6), plan.warnings.map { it.row })
        assertTrue(plan.warnings[0].message.contains("B"))
    }

    @Test fun emptyDayIsDroppedWithWarning() {
        val plan = single {
            cell("A1", "VUOTO")
            row(2, "ESERCIZIO", "SET")
            cell("A5", "PIENO")
            row(6, "ESERCIZIO", "SET")
            row(7, "SQUAT", 3.0)
        }
        assertEquals(listOf("PIENO"), plan.days.map { it.name })
        assertTrue(plan.warnings.any { it.row == 2 && it.message.contains("VUOTO") })
    }

    @Test fun noHeaderThrows() {
        val bytes = XlsxTestBuilder().sheet("Foglio") { row(1, "ciao", "mondo") }.build()
        try {
            parseBytes(bytes)
            fail("expected PlanParseException")
        } catch (e: PlanParseException) {
            assertTrue(e.message!!.startsWith("Nessun giorno di allenamento trovato nel foglio"))
        }
    }

    @Test fun dayNameFallback() {
        val plan = single {
            row(1, "ESERCIZIO", "SET")
            row(2, "SQUAT", 3.0)
        }
        assertEquals("Giorno 1", plan.days[0].name)
        assertTrue(plan.warnings.any { it.message.contains("Nome del giorno") })
    }

    @Test fun restWarningsCarryRowAndExercise() {
        val plan = single {
            cell("A1", "D1")
            row(2, "ESERCIZIO", "SET", "REST")
            row(3, "SQUAT", 3.0, "3")
        }
        assertEquals(180, plan.days[0].exercises[0].restSeconds)
        assertEquals(3, plan.warnings.single().row)
        assertTrue(plan.warnings.single().message.contains("SQUAT"))
    }
}
