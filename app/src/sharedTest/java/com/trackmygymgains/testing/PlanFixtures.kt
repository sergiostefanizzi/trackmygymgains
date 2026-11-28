package com.trackmygymgains.testing

import com.trackmygymgains.data.plan.ParsedDay
import com.trackmygymgains.data.plan.ParsedExercise

/** Name of the first (reference) sheet built by [referenceSheetXlsx]. */
const val REFERENCE_SHEET_NAME = "2.0 8ª 091126"

/** Name of the second, old-style sheet built by [referenceSheetXlsx]. */
const val OLD_SHEET_NAME = "8ª 140926"

private class RawExercise(
    val name: String,
    val sets: Double,
    val reps: String,
    val rir: String,
    val rest: String,
    val note: String,
)

private class RawDay(val titleRow: Int, val title: String, val exercises: List<RawExercise>)

private fun e(name: String, sets: Double, reps: String, rir: String, rest: String, note: String) =
    RawExercise(name, sets, reps, rir, rest, note)

private val rawDays = listOf(
    RawDay(
        1, "FULL BODY 1",
        listOf(
            e("PULL UP", 3.0, "4-6", "1-2", "3'", "NO ELASTICO - CLASSICO"),
            e("DISTENSIONI MULTIP.", 3.0, "6-8", "1-2", "3'", "PANCA INCLINATA SOLITA (buco 18 - 20°) - F1\" PETTO"),
            e("ILIAC PULL DOWN", 2.0, "6-8", "1-2 / MAX REP", "2'", "/"),
            e("POLIQUIN PUSH DOWN", 3.0, "8-10", "1-2", "2'", "PANCA BUCO 4 - LEGGERMENTE DISTANTE DAL CAVO - GOMITO DI FIANCO AL BUSTO"),
            e("Y RAISES", 2.0, "8-10", "1-2 ", "2'", "30\" RECUPERO TRA LE BRACCIA - buco 18"),
            e("LEG CURL MACHINE", 3.0, "7-9", "1-2 / MAX REPS", "2'", "LEG CURL SEDUTO - 4 schiena. M"),
            e("LEG EXTENSION MACHINE", 2.0, "7-9", "1-2", "2'", "/ - 4 schiena, S"),
        ),
    ),
    RawDay(
        13, "FULL BODY 2",
        listOf(
            e("BANDED PULL UP", 3.0, "4-6", "1-2", "3'", "CON ELASTICO E FERMO CHIUSURA 1\""),
            e("CALF LEG PRESS SING.", 3.0, "8-10", "1-2 / MAX REPS", "2'", "FERMO 2\" ALLUNGAMENTO - 6 buco"),
            e("PENDULUM SQUAT", 3.0, "6-8", "1-2", "3'", "/"),
            e("CURL MANUBRI ", 3.0, "7-10", "1-2", "2'", "IN PIEDI - SUPINAZIONE"),
            e("SHOULDER PRESS ", 3.0, "6-9", "1-2", "3'", "/ - sotto 71"),
            e("ADDUCTOR MACHINE", 2.0, "8-10", "1-2", "/", "JUMP SET - 30\" DI REST POI ABDUCTOR"),
            e("ABDUCTOR MACHINE", 2.0, "8-10", "1-2", "/", "JUMP SET - 1' DI REST POI ADDUCTOR"),
            e("CABLE CRUNCH", 3.0, "8-10", "1-2 / MAX REPS", "2'", "/"),
        ),
    ),
    RawDay(
        25, "FULL BODY 3",
        listOf(
            e("HAMMER CURL CORDA", 3.0, "8-10", "1-2", "/", "JUMP SET - 30\" DI REST POI FRENCH P. - buco 15"),
            e("OVERHEAD EXTENSION", 3.0, "8-10", "1-2", "/", "JUMP SET - 1\" DI REST POI HAMMER C."),
            e("ROMANIAN DEADLIFT", 4.0, "6-8", "1-2", "3'", "/"),
            e("DISTENSIONI MANUBRI", 3.0, "5-8", "1-2", "3'", "FAI 1\" DI FERMO IN ALLUNGAMENTO"),
            e("LAT MACHINE", 2.0, "6-8", "1-2", "3'", "MAG SOLITA"),
            e("LEG EXTENSION", 2.0, "7-9", "1-2 / MAX REPS", "2'", "/"),
            e("RAW DIAMOND", 2.0, "6-8 ", "1-2 / MAX REPS", "3'", "PRESA LARGA SOLITA"),
            e("ALZATE LATERALI", 3.0, "7-11", "1-2 / MAX REPS", "2'", "MANUBRI - CON IL PETTO IN APPOGGIO SU PANCA 70°"),
        ),
    ),
)

/** Builds an xlsx with the reference sheet (columns A-F as in the user's plan plus history noise) and an old-style second sheet. */
fun referenceSheetXlsx(): ByteArray = XlsxTestBuilder()
    .sheet(REFERENCE_SHEET_NAME) {
        for (day in rawDays) {
            val titleRow = day.titleRow
            cell(titleRow, 0, day.title)
            for (w in 0 until 8) {
                cell(titleRow, 6 + 4 * w, "SETTIMANA ${w + 1} / DATA: ${if (w < 3) "0${w + 1}/09/2026" else ""}")
            }
            val headerRow = titleRow + 1
            row(headerRow, "ESERCIZIO", "SET", "TARGET REP", "TARGET RIR", "REST", "NOTE")
            for (w in 0 until 8) {
                val labels = listOf("CARICO", "REP", "RIR", "FEEDBACK")
                labels.forEachIndexed { i, l -> cell(headerRow, 6 + 4 * w + i, "$l W${w + 1}") }
            }
            day.exercises.forEachIndexed { i, ex ->
                val r = headerRow + 1 + i
                row(r, ex.name, ex.sets, ex.reps, ex.rir, ex.rest, ex.note)
                if (i % 2 == 0) {
                    cell(r, 6, "37,5")
                    cell(r, 7, "6 / 5 / 5")
                    cell(r, 8, "0.0")
                    cell(r, 9, "ok")
                } else {
                    cell(r, 6, "0")
                    cell(r, 10, "40")
                    cell(r, 11, "8 / 8 / 7")
                }
            }
        }
    }
    .sheet(OLD_SHEET_NAME) {
        cell("A1", "VECCHIO GIORNO")
        row(2, "ESERCIZIO", "SET", "REP")
        row(3, "SQUAT", 5.0, "5")
    }
    .build()

private fun ex(name: String, sets: Int, reps: String, rir: String, rest: Int?, note: String?) =
    ParsedExercise(name, sets, reps, rir, rest, note)

/** Expected parse of sheet 0 of [referenceSheetXlsx] (also the expected content of the user's real first sheet). */
val referencePlanExpected: List<ParsedDay> = listOf(
    ParsedDay(
        "FULL BODY 1",
        listOf(
            ex("PULL UP", 3, "4-6", "1-2", 180, "NO ELASTICO - CLASSICO"),
            ex("DISTENSIONI MULTIP.", 3, "6-8", "1-2", 180, "PANCA INCLINATA SOLITA (buco 18 - 20°) - F1\" PETTO"),
            ex("ILIAC PULL DOWN", 2, "6-8", "1-2 / MAX REP", 120, null),
            ex("POLIQUIN PUSH DOWN", 3, "8-10", "1-2", 120, "PANCA BUCO 4 - LEGGERMENTE DISTANTE DAL CAVO - GOMITO DI FIANCO AL BUSTO"),
            ex("Y RAISES", 2, "8-10", "1-2", 120, "30\" RECUPERO TRA LE BRACCIA - buco 18"),
            ex("LEG CURL MACHINE", 3, "7-9", "1-2 / MAX REPS", 120, "LEG CURL SEDUTO - 4 schiena. M"),
            ex("LEG EXTENSION MACHINE", 2, "7-9", "1-2", 120, "/ - 4 schiena, S"),
        ),
    ),
    ParsedDay(
        "FULL BODY 2",
        listOf(
            ex("BANDED PULL UP", 3, "4-6", "1-2", 180, "CON ELASTICO E FERMO CHIUSURA 1\""),
            ex("CALF LEG PRESS SING.", 3, "8-10", "1-2 / MAX REPS", 120, "FERMO 2\" ALLUNGAMENTO - 6 buco"),
            ex("PENDULUM SQUAT", 3, "6-8", "1-2", 180, null),
            ex("CURL MANUBRI", 3, "7-10", "1-2", 120, "IN PIEDI - SUPINAZIONE"),
            ex("SHOULDER PRESS", 3, "6-9", "1-2", 180, "/ - sotto 71"),
            ex("ADDUCTOR MACHINE", 2, "8-10", "1-2", null, "JUMP SET - 30\" DI REST POI ABDUCTOR"),
            ex("ABDUCTOR MACHINE", 2, "8-10", "1-2", null, "JUMP SET - 1' DI REST POI ADDUCTOR"),
            ex("CABLE CRUNCH", 3, "8-10", "1-2 / MAX REPS", 120, null),
        ),
    ),
    ParsedDay(
        "FULL BODY 3",
        listOf(
            ex("HAMMER CURL CORDA", 3, "8-10", "1-2", null, "JUMP SET - 30\" DI REST POI FRENCH P. - buco 15"),
            ex("OVERHEAD EXTENSION", 3, "8-10", "1-2", null, "JUMP SET - 1\" DI REST POI HAMMER C."),
            ex("ROMANIAN DEADLIFT", 4, "6-8", "1-2", 180, null),
            ex("DISTENSIONI MANUBRI", 3, "5-8", "1-2", 180, "FAI 1\" DI FERMO IN ALLUNGAMENTO"),
            ex("LAT MACHINE", 2, "6-8", "1-2", 180, "MAG SOLITA"),
            ex("LEG EXTENSION", 2, "7-9", "1-2 / MAX REPS", 120, null),
            ex("RAW DIAMOND", 2, "6-8", "1-2 / MAX REPS", 180, "PRESA LARGA SOLITA"),
            ex("ALZATE LATERALI", 3, "7-11", "1-2 / MAX REPS", 120, "MANUBRI - CON IL PETTO IN APPOGGIO SU PANCA 70°"),
        ),
    ),
)
