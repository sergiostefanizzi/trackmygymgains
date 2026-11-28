package com.trackmygymgains.data.plan

import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.xlsx.XlsxWorkbook
import com.trackmygymgains.testing.XlsxTestBuilder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryParserTest {
    private fun parse(build: XlsxTestBuilder.SheetBuilder.() -> Unit) =
        WorkoutPlanParser.parse(
            XlsxWorkbook.open(XlsxTestBuilder().sheet("S", build).build().inputStream()),
            0,
        )

    /** Column (0-based) of the CARICO/REP/RIR/FEEDBACK cell of [week] (1-based) when weeks are laid out 4 wide from G. */
    private fun col(week: Int, offset: Int) = 6 + 4 * (week - 1) + offset

    private val planHeader = arrayOf("ESERCIZIO", "SET", "TARGET REP", "TARGET RIR", "REST", "NOTE")

    /** Writes the title row [titleRow] (1-based), the header row below it and the week headers for [weeks]. */
    private fun XlsxTestBuilder.SheetBuilder.block(
        titleRow: Int,
        day: String,
        weeks: Int,
        dates: Map<Int, String> = emptyMap(),
        labels: (Int, Int) -> String = { w, o -> listOf("CARICO", "REP", "RIR", "FEEDBACK")[o] + " W$w" },
    ) {
        cell(titleRow, 0, day)
        for ((w, d) in dates) cell(titleRow, col(w, 0), "SETTIMANA $w / DATA: $d")
        row(titleRow + 1, *planHeader)
        for (w in 1..weeks) for (o in 0..3) cell(titleRow + 1, col(w, o), labels(w, o))
    }

    private fun XlsxTestBuilder.SheetBuilder.ex(
        row: Int,
        name: String,
        sets: Any = 3.0,
        week: Int = 1,
        load: Any? = null,
        reps: Any? = null,
        rir: Any? = null,
        feedback: Any? = null,
    ) {
        row(row, name, sets, "6-8", "1-2", "3'", "/")
        cell(row, col(week, 0), load)
        cell(row, col(week, 1), reps)
        cell(row, col(week, 2), rir)
        cell(row, col(week, 3), feedback)
    }

    private fun set(load: Double, reps: Int, outcome: SetOutcome = SetOutcome.BUFFER, rir: Int? = null) =
        ParsedHistorySet(load, reps, outcome, rir)

    @Test fun perSetLoadsRepsAndRir() {
        val plan = parse {
            block(1, "FULL BODY 1", 1, mapOf(1 to "03/11/2026"))
            ex(3, "PULL UP", load = "35 / 30 /25", reps = "4 / 5 / 7", rir = 1.0)
        }
        assertEquals(emptyList<ParseWarning>(), plan.warnings)
        assertEquals(1, plan.history.size)
        val s = plan.history[0]
        assertEquals(0, s.dayIndex)
        assertEquals(1, s.week)
        assertEquals(LocalDate.of(2026, 11, 3), s.date)
        assertEquals(
            listOf(
                ParsedHistoryExercise(
                    0,
                    listOf(
                        set(35.0, 4, rir = 1),
                        set(30.0, 5, rir = 1),
                        set(25.0, 7, rir = 1),
                    ),
                    null,
                ),
            ),
            s.exercises,
        )
    }

    @Test fun singleLoadAppliesToEverySetAndDecimalSeparators() {
        val plan = parse {
            block(1, "D", 2, mapOf(1 to "01/02/2026", 2 to "08/02/2026"))
            ex(3, "A", load = 22.5, reps = "8 / 8 / 8")
            ex(4, "B", week = 2, load = "37,5", reps = "10 / 9")
            ex(5, "C", week = 2, load = "21,25 / 63.5", reps = "5 / 5")
        }
        val w1 = plan.history[0].exercises.single().sets
        assertEquals(listOf(22.5, 22.5, 22.5), w1.map { it.loadKg })
        val w2 = plan.history[1].exercises
        assertEquals(listOf(37.5, 37.5), w2[0].sets.map { it.loadKg })
        assertEquals(listOf(10, 9), w2[0].sets.map { it.reps })
        assertEquals(listOf(21.25, 63.5), w2[1].sets.map { it.loadKg })
        assertEquals(listOf(1, 2), w2.map { it.exerciseIndex })
    }

    @Test fun fewerLoadsRepeatLastAndExtrasAreIgnored() {
        val plan = parse {
            block(1, "D", 1, mapOf(1 to "01/02/2026"))
            ex(3, "A", load = "0 / 2,5", reps = "8 / 8 / 8 / 8")
            ex(4, "B", load = "10 / 20 / 30", reps = "5 / 5")
        }
        val ex = plan.history.single().exercises
        assertEquals(listOf(0.0, 2.5, 2.5, 2.5), ex[0].sets.map { it.loadKg })
        assertEquals(listOf(10.0, 20.0), ex[1].sets.map { it.loadKg })
        assertEquals(emptyList<ParseWarning>(), plan.warnings)
    }

    @Test fun rirZeroOneEmptyAndInvalid() {
        val plan = parse {
            block(1, "D", 1, mapOf(1 to "01/02/2026"))
            ex(3, "ZERO", load = 10, reps = 8, rir = "0.0")
            ex(4, "ONE", load = 10, reps = 8, rir = "1.0")
            ex(5, "EMPTY", load = 10, reps = 8)
            ex(6, "BAD", load = 10, reps = 8, rir = "boh")
            ex(7, "HIGH", load = 10, reps = 8, rir = 11)
        }
        val sets = plan.history.single().exercises.map { it.sets.single() }
        assertEquals(set(10.0, 8, SetOutcome.FAILURE, null), sets[0])
        assertEquals(set(10.0, 8, SetOutcome.BUFFER, 1), sets[1])
        assertEquals(set(10.0, 8, SetOutcome.BUFFER, null), sets[2])
        assertEquals(set(10.0, 8, SetOutcome.BUFFER, null), sets[3])
        assertEquals(set(10.0, 8, SetOutcome.BUFFER, null), sets[4])
        assertEquals(listOf(6, 7), plan.warnings.map { it.row })
        assertTrue(plan.warnings.all { "RIR" in it.message })
    }

    @Test fun invalidDataIsSkippedWithWarning() {
        val plan = parse {
            block(1, "D", 1, mapOf(1 to "01/02/2026"))
            ex(3, "OK", load = 10, reps = 8)
            ex(4, "LOAD ONLY", load = 75)
            ex(5, "REPS ONLY", reps = "8 / 8")
            ex(6, "BAD REPS", load = 10, reps = "8 / x")
            ex(7, "FRACTION", load = 10, reps = "8.5")
            ex(8, "ZERO REPS", load = 10, reps = "0")
            ex(9, "BAD LOAD", load = "10 / abc", reps = "8 / 8")
            ex(10, "HUGE LOAD", load = 1001, reps = 8)
            ex(11, "WHOLE", load = 10, reps = "8.0")
        }
        val ex = plan.history.single().exercises
        assertEquals(listOf(0, 8), ex.map { it.exerciseIndex })
        assertEquals(listOf(4, 5, 6, 7, 8, 9, 10), plan.warnings.map { it.row })
        assertTrue(plan.warnings.all { "settimana 1" in it.message })
    }

    @Test fun missingDateWithDataWarnsOncePerDayWeek() {
        val plan = parse {
            block(1, "FULL BODY 1", 2, mapOf(2 to "10/11/2026"))
            ex(3, "A", week = 1, load = 10, reps = 8)
            ex(4, "B", week = 1, load = 10, reps = 8)
            ex(3, "A", week = 2, load = 12, reps = 8)
        }
        assertEquals(listOf(2), plan.history.map { it.week })
        assertEquals(
            listOf(ParseWarning(2, "Settimana 1 del giorno \"FULL BODY 1\" ignorata: data mancante")),
            plan.warnings,
        )
    }

    @Test fun titleTextWithoutDateAndInvalidDateCountAsMissing() {
        val plan = parse {
            block(1, "D", 2)
            cell(1, col(1, 0), "SETTIMANA 1 / DATA: rest new gym")
            cell(1, col(2, 0), "SETTIMANA 2 / DATA: 31/02/2026")
            ex(3, "A", week = 1, load = 10, reps = 8)
            ex(3, "A", week = 2, load = 10, reps = 8)
        }
        assertTrue(plan.history.isEmpty())
        assertEquals(2, plan.warnings.size)
    }

    @Test fun dateWithoutDataAndDataFreeWeeksAreSilent() {
        val plan = parse {
            block(1, "D", 3, mapOf(1 to "01/02/2026", 2 to "08/02/2026"))
            ex(3, "A", week = 2, load = 10, reps = 8)
        }
        assertEquals(listOf(2), plan.history.map { it.week })
        assertEquals(emptyList<ParseWarning>(), plan.warnings)
    }

    @Test fun nonChronologicalWeeksAreOrderedByDateThenDay() {
        val plan = parse {
            block(1, "D1", 3, mapOf(1 to "20/10/2026", 2 to "17/11/2026", 3 to "13/10/2026"))
            ex(3, "A", week = 1, load = 10, reps = 8)
            ex(3, "A", week = 2, load = 10, reps = 8)
            ex(3, "A", week = 3, load = 10, reps = 8)
            block(6, "D2", 1, mapOf(1 to "13/10/2026"))
            ex(8, "B", week = 1, load = 10, reps = 8)
        }
        assertEquals(
            listOf(LocalDate.of(2026, 10, 13) to 0, LocalDate.of(2026, 10, 13) to 1, LocalDate.of(2026, 10, 20) to 0, LocalDate.of(2026, 11, 17) to 0),
            plan.history.map { it.date to it.dayIndex },
        )
        assertEquals(listOf(3, 1, 1, 2), plan.history.map { it.week })
    }

    @Test fun feedbackOnlyExerciseIsKeptWithoutSets() {
        val plan = parse {
            block(1, "D", 1, mapOf(1 to "01/02/2026"))
            ex(3, "A", load = 10, reps = 8, feedback = "  buco 18 - 20  ")
            ex(4, "B", feedback = "dolore spalla")
            ex(5, "C", feedback = "/")
            ex(6, "D", feedback = "-")
        }
        val ex = plan.history.single().exercises
        assertEquals(listOf(0, 1), ex.map { it.exerciseIndex })
        assertEquals("buco 18 - 20", ex[0].feedback)
        assertEquals(emptyList<ParsedHistorySet>(), ex[1].sets)
        assertEquals("dolore spalla", ex[1].feedback)
        assertEquals(emptyList<ParseWarning>(), plan.warnings)
    }

    @Test fun feedbackAloneDoesNotCreateSession() {
        val plan = parse {
            block(1, "D", 1, mapOf(1 to "01/02/2026"))
            ex(3, "A", feedback = "solo nota")
        }
        assertTrue(plan.history.isEmpty())
        assertEquals(emptyList<ParseWarning>(), plan.warnings)
    }

    @Test fun skippedPlanRowsAreSkippedInHistoryAndIndicesFollowParsedExercises() {
        val plan = parse {
            block(1, "D", 1, mapOf(1 to "01/02/2026"))
            ex(3, "BAD", sets = "boh", load = 99, reps = 9)
            ex(4, "GOOD", load = 10, reps = 8)
        }
        assertEquals(1, plan.days.single().exercises.size)
        val ex = plan.history.single().exercises.single()
        assertEquals(0, ex.exerciseIndex)
        assertEquals(10.0, ex.sets.single().loadKg, 0.0)
        assertEquals(1, plan.warnings.size)
        assertTrue("SET non valido" in plan.warnings.single().message)
    }

    @Test fun twoDigitYearsAndLabelsWithTrailingSpaces() {
        val plan = parse {
            block(
                1, "D", 2, mapOf(1 to "3/9/26", 2 to "05/10/2026"),
                labels = { w, o -> "  " + listOf("carico", "Rep", "RIR", "feedback")[o] + "  w$w " },
            )
            ex(3, "A", week = 1, load = 10, reps = 8)
            ex(3, "A", week = 2, load = 10, reps = 8)
        }
        assertEquals(listOf(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 10, 5)), plan.history.map { it.date })
        assertEquals(emptyList<ParseWarning>(), plan.warnings)
    }

    @Test fun eachDayBlockHasItsOwnDates() {
        val plan = parse {
            block(1, "FB 1", 2, mapOf(1 to "01/02/2026", 2 to "08/02/2026"))
            ex(3, "A", week = 1, load = 10, reps = 8)
            ex(4, "B", week = 2, load = 10, reps = 8)
            block(6, "FB 2", 2, mapOf(1 to "03/02/2026", 2 to "10/02/2026"))
            ex(8, "C", week = 1, load = 20, reps = 6)
            ex(9, "D", week = 2, load = 20, reps = 6)
        }
        assertEquals(
            listOf(
                Triple(LocalDate.of(2026, 2, 1), 0, 1),
                Triple(LocalDate.of(2026, 2, 3), 1, 1),
                Triple(LocalDate.of(2026, 2, 8), 0, 2),
                Triple(LocalDate.of(2026, 2, 10), 1, 2),
            ),
            plan.history.map { Triple(it.date, it.dayIndex, it.week) },
        )
        assertEquals(listOf(0, 0, 1, 1), plan.history.map { it.exercises.single().exerciseIndex })
    }

    @Test fun dateOfWeekIsFoundOnlyInItsOwnColumns() {
        val plan = parse {
            block(1, "D", 2, mapOf(2 to "10/11/2026"))
            ex(3, "A", week = 1, load = 10, reps = 8)
        }
        assertTrue(plan.history.isEmpty())
        assertEquals(1, plan.warnings.size)
    }

    @Test fun sheetWithoutWeekColumnsHasNoHistory() {
        val plan = parse {
            row(1, "FULL BODY 1")
            row(2, *planHeader)
            row(3, "PULL UP", 3.0, "4-6", "1-2", "3'", "/")
        }
        assertTrue(plan.history.isEmpty())
        assertTrue(plan.warnings.isEmpty())
        assertNull(plan.days.single().exercises.single().notes)
    }

    @Test fun weekWithoutRepColumnIsIgnored() {
        val plan = parse {
            row(1, "D")
            row(2, *planHeader, "CARICO W1")
            row(3, "A", 3.0, "4-6", "1-2", "3'", "/", 20)
        }
        assertTrue(plan.history.isEmpty())
        assertTrue(plan.warnings.isEmpty())
    }
}
