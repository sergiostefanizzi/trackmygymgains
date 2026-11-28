package com.trackmygymgains.data.repo

/** Domain view of an imported plan. [days] are ordered by position. */
data class WorkoutPlan(
    val id: Long,
    val name: String,
    val sheetName: String,
    val sourceFileName: String?,
    val importedAt: Long,
    val days: List<WorkoutDay>,
)

/** [exercises] are ordered by position. */
data class WorkoutDay(
    val id: Long,
    val name: String,
    val position: Int,
    val exercises: List<PlannedExercise>,
)

data class PlannedExercise(
    val id: Long,
    val position: Int,
    val name: String,
    val sets: Int,
    val targetReps: String?,
    val targetRir: String?,
    val restSeconds: Int?,
    val notes: String?,
)
