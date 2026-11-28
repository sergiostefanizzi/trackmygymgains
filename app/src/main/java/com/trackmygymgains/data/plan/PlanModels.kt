package com.trackmygymgains.data.plan

data class ParsedPlan(
    val sheetName: String,
    val days: List<ParsedDay>,
    val warnings: List<ParseWarning>,
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
