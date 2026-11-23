package com.trackmygymgains.data.plan

import com.trackmygymgains.data.xlsx.SheetGrid
import com.trackmygymgains.data.xlsx.XlsxWorkbook

object WorkoutPlanParser {
    private const val MAX_COLS = 200
    private const val TITLE_LOOKBACK = 3

    private class Columns(
        val name: Int,
        val sets: Int,
        val reps: Int?,
        val rir: Int?,
        val rest: Int?,
        val notes: Int?,
    )

    fun parse(workbook: XlsxWorkbook, sheetIndex: Int = 0): ParsedPlan = parse(workbook.sheet(sheetIndex))

    fun parse(sheet: SheetGrid): ParsedPlan {
        val warnings = mutableListOf<ParseWarning>()
        val days = mutableListOf<ParsedDay>()
        var prevEnd = -1
        var blockNo = 0
        var row = 0
        while (row < sheet.rowCount) {
            val cols = columnsOf(sheet, row)
            if (cols == null) {
                row++
                continue
            }
            blockNo++
            val headerRow = row

            var dayName: String? = null
            for (r in headerRow - 1 downTo maxOf(headerRow - TITLE_LOOKBACK, prevEnd + 1)) {
                val text = clean(sheet.cell(r, cols.name))
                if (text.isNotEmpty()) {
                    dayName = text
                    break
                }
            }
            if (dayName == null) {
                dayName = "Giorno $blockNo"
                warnings += ParseWarning(headerRow + 1, "Nome del giorno non trovato: usato \"$dayName\"")
            }
            if (cols.rest == null) {
                warnings += ParseWarning(
                    headerRow + 1,
                    "Colonna REST non trovata nel giorno \"$dayName\": nessun timer automatico",
                )
            }

            val exercises = mutableListOf<ParsedExercise>()
            var r = headerRow + 1
            while (r < sheet.rowCount) {
                if (columnsOf(sheet, r) != null) break
                val name = clean(sheet.cell(r, cols.name))
                if (name.isEmpty()) break
                parseExercise(sheet, r, name, cols, warnings)?.let { exercises += it }
                r++
            }
            prevEnd = r - 1
            row = r

            if (exercises.isEmpty()) {
                warnings += ParseWarning(headerRow + 1, "Giorno \"$dayName\" ignorato: nessun esercizio valido")
            } else {
                days += ParsedDay(dayName, exercises)
            }
        }
        if (blockNo == 0) {
            throw PlanParseException("Nessun giorno di allenamento trovato nel foglio \"${sheet.name}\"")
        }
        return ParsedPlan(sheet.name, days, warnings)
    }

    private fun parseExercise(
        sheet: SheetGrid,
        row: Int,
        name: String,
        cols: Columns,
        warnings: MutableList<ParseWarning>,
    ): ParsedExercise? {
        val rowNo = row + 1
        val rawSets = sheet.cell(row, cols.sets)
        val sets = parseSets(rawSets)
        if (sets == null) {
            warnings += ParseWarning(
                rowNo,
                "Esercizio \"$name\" ignorato: SET non valido (\"${rawSets.orEmpty().trim()}\")",
            )
            return null
        }
        var rest: Int? = null
        if (cols.rest != null) {
            val result = RestParser.parseSeconds(sheet.cell(row, cols.rest))
            rest = result.seconds
            result.warning?.let { warnings += ParseWarning(rowNo, "Esercizio \"$name\": $it") }
        }
        return ParsedExercise(
            name = name,
            sets = sets,
            targetReps = cols.reps?.let { dashToNull(clean(sheet.cell(row, it))) },
            targetRir = cols.rir?.let { dashToNull(clean(sheet.cell(row, it))) },
            restSeconds = rest,
            notes = cols.notes?.let { dashToNull(sheet.cell(row, it).orEmpty().trim()) },
        )
    }

    internal fun parseSets(raw: String?): Int? {
        val text = raw.orEmpty().trim().replace(',', '.')
        if (text.isEmpty()) return null
        var total = 0
        for (part in text.split('+')) {
            val value = part.trim().toDoubleOrNull() ?: return null
            if (value.isNaN() || value.isInfinite() || value > 1000) return null
            total += value.toInt()
        }
        return if (total > 0) total else null
    }

    private fun columnsOf(sheet: SheetGrid, row: Int): Columns? {
        val labels = HashMap<String, Int>()
        for (c in 0 until MAX_COLS) {
            val text = clean(sheet.cell(row, c)).uppercase()
            if (text.isNotEmpty()) labels.putIfAbsent(text, c)
        }
        val name = labels["ESERCIZIO"] ?: return null
        val sets = labels["SET"] ?: return null
        fun find(vararg keys: String) = keys.firstNotNullOfOrNull { labels[it] }
        return Columns(
            name = name,
            sets = sets,
            reps = find("TARGET REP", "REP"),
            rir = find("TARGET RIR", "RIR"),
            rest = find("REST", "RECUPERO"),
            notes = find("NOTE", "NOTES"),
        )
    }

    private fun clean(raw: String?): String = raw.orEmpty().trim().replace(Regex("\\s+"), " ")

    private fun dashToNull(text: String): String? = if (text.isEmpty() || text == "/" || text == "-") null else text
}
