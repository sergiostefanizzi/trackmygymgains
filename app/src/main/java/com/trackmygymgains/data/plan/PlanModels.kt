package com.trackmygymgains.data.plan

data class ParsedPlan(
    val sheetName: String,
    val days: List<ParsedDay>,
    val warnings: List<ParseWarning>,
    val history: List<ParsedHistorySession> = emptyList(),
)

/** One workout of a day block performed in a given week, read from the CARICO/REP/RIR/FEEDBACK columns. */
data class ParsedHistorySession(
    /** Index into [ParsedPlan.days]. */
    val dayIndex: Int,
    val week: Int,
    val date: java.time.LocalDate,
    val exercises: List<ParsedHistoryExercise>,
)

data class ParsedHistoryExercise(
    /** Index into [ParsedDay.exercises]. */
    val exerciseIndex: Int,
    val sets: List<ParsedHistorySet>,
    val feedback: String?,
)

/** [rir] is null for FAILURE outcomes and when not recorded. */
data class ParsedHistorySet(
    val loadKg: Double,
    val reps: Int,
    val outcome: com.trackmygymgains.data.model.SetOutcome,
    val rir: Int?,
)

data class ParsedDay(val name: String, val exercises: List<ParsedExercise>)

data class ParsedExercise(
    val name: String,
    val sets: Int,
    val targetReps: String?,
    val targetRir: String?,
    val restSeconds: Int?,
    val notes: String?,
)

/** Non-fatal problem found while parsing. [row] is the 1-based sheet row; [message] is Italian and user-facing. */
data class ParseWarning(val row: Int, val message: String)

/** Fatal parsing problem; [message] is Italian and user-facing. */
class PlanParseException(message: String) : Exception(message)
