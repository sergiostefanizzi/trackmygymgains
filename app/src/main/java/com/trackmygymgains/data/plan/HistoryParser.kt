package com.trackmygymgains.data.plan

import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.xlsx.SheetGrid
import java.time.DateTimeException
import java.time.LocalDate

/** Reads the per-week history columns (CARICO/REP/RIR/FEEDBACK Wn) of one day block. */
internal object HistoryParser {
    private const val MAX_COLS = 200
    private val weekLabel = Regex("^(CARICO|REP|RIR|FEEDBACK) W(\\d+)$")
    private val datePattern = Regex("(?<!\\d)(\\d{1,2})/(\\d{1,2})/(\\d{4}|\\d{2})(?!\\d)")

    private class WeekCols(val week: Int, val load: Int, val reps: Int, val rir: Int?, val feedback: Int?) {
        val first get() = load
        val last get() = maxOf(load, reps, rir ?: 0, feedback ?: 0)
    }

    /** A successfully parsed plan exercise: [index] in its day, sheet [row] (0-based). */
    class Row(val index: Int, val row: Int, val name: String)

    fun parseBlock(
        sheet: SheetGrid,
        dayIndex: Int,
        dayName: String,
        headerRow: Int,
        titleFirstRow: Int,
        rows: List<Row>,
        warnings: MutableList<ParseWarning>,
    ): List<ParsedHistorySession> {
        val ordered = weekColumns(sheet, headerRow).sortedBy { it.first }
        val result = mutableListOf<ParsedHistorySession>()
        for ((i, w) in ordered.withIndex()) {
            val end = if (i + 1 < ordered.size) ordered[i + 1].first - 1 else w.last
            val exercises = rows.mapNotNull { parseExercise(sheet, it, w, warnings) }
            if (exercises.none { it.sets.isNotEmpty() }) continue
            val date = findDate(sheet, titleFirstRow, headerRow - 1, w.first, maxOf(end, w.last))
            if (date == null) {
                warnings += ParseWarning(
                    headerRow + 1,
                    "Settimana ${w.week} del giorno \"$dayName\" ignorata: data mancante",
                )
                continue
            }
            result += ParsedHistorySession(dayIndex, w.week, date, exercises)
        }
        return result
    }

    private fun weekColumns(sheet: SheetGrid, headerRow: Int): List<WeekCols> {
        val found = HashMap<Pair<String, Int>, Int>()
        for (c in 0 until MAX_COLS) {
            val text = sheet.cell(headerRow, c).orEmpty().trim().replace(Regex("\\s+"), " ").uppercase()
            val m = weekLabel.matchEntire(text) ?: continue
            found.putIfAbsent(m.groupValues[1] to m.groupValues[2].toInt(), c)
        }
        return found.keys.map { it.second }.distinct().sorted().mapNotNull { n ->
            val load = found["CARICO" to n] ?: return@mapNotNull null
            val reps = found["REP" to n] ?: return@mapNotNull null
            WeekCols(n, load, reps, found["RIR" to n], found["FEEDBACK" to n])
        }
    }

    private fun findDate(sheet: SheetGrid, fromRow: Int, toRow: Int, fromCol: Int, toCol: Int): LocalDate? {
        for (r in maxOf(fromRow, 0)..toRow) {
            for (c in fromCol..toCol) {
                val text = sheet.cell(r, c) ?: continue
                val m = datePattern.find(text) ?: continue
                val year = m.groupValues[3].let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
                try {
                    return LocalDate.of(year, m.groupValues[2].toInt(), m.groupValues[1].toInt())
                } catch (_: DateTimeException) {
                    // invalid date counts as missing; keep searching
                }
            }
        }
        return null
    }

    private fun parseExercise(
        sheet: SheetGrid,
        row: Row,
        w: WeekCols,
        warnings: MutableList<ParseWarning>,
    ): ParsedHistoryExercise? {
        val rowNo = row.row + 1
        val rawLoad = sheet.cell(row.row, w.load).orEmpty().trim()
        val rawReps = sheet.cell(row.row, w.reps).orEmpty().trim()
        val rawRir = w.rir?.let { sheet.cell(row.row, it) }.orEmpty().trim()
        val feedback = w.feedback?.let { sheet.cell(row.row, it) }.orEmpty().trim()
            .let { if (it.isEmpty() || it == "/" || it == "-") null else it }
        if (rawLoad.isEmpty() && rawReps.isEmpty() && rawRir.isEmpty() && feedback == null) return null

        fun skip(reason: String): ParsedHistoryExercise? {
            warnings += ParseWarning(
                rowNo,
                "Esercizio \"${row.name}\", settimana ${w.week}: $reason, dati ignorati",
            )
            return null
        }

        if (rawReps.isEmpty()) {
            if (rawLoad.isNotEmpty()) return skip("carico senza ripetizioni")
            return feedback?.let { ParsedHistoryExercise(row.index, emptyList(), it) }
        }
        val reps = rawReps.split('/').map { parseReps(it) }
        if (reps.any { it == null }) return skip("ripetizioni non valide (\"$rawReps\")")
        if (rawLoad.isEmpty()) return skip("carico mancante")
        val loads = rawLoad.split('/').map { parseLoad(it) }
        if (loads.any { it == null }) return skip("carico non valido (\"$rawLoad\")")

        var outcome = SetOutcome.BUFFER
        var rir: Int? = null
        if (rawRir.isNotEmpty()) {
            val v = rawRir.replace(',', '.').toDoubleOrNull()
            val n = v?.takeIf { !it.isNaN() && !it.isInfinite() && it == Math.rint(it) && it in 0.0..10.0 }?.toInt()
            when (n) {
                null -> warnings += ParseWarning(
                    rowNo,
                    "Esercizio \"${row.name}\", settimana ${w.week}: RIR non valido (\"$rawRir\"), considerato non registrato",
                )
                0 -> outcome = SetOutcome.FAILURE
                else -> rir = n
            }
        }
        val sets = reps.mapIndexed { i, r ->
            ParsedHistorySet(loads[minOf(i, loads.size - 1)]!!, r!!, outcome, rir)
        }
        return ParsedHistoryExercise(row.index, sets, feedback)
    }

    private fun parseReps(part: String): Int? {
        val v = part.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite() || v != Math.rint(v) || v < 1 || v > 999) return null
        return v.toInt()
    }

    private fun parseLoad(part: String): Double? {
        val v = part.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite() || v < 0 || v > 1000) return null
        return v
    }
}
