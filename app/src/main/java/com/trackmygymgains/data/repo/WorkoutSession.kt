package com.trackmygymgains.data.repo

import com.trackmygymgains.data.model.SetOutcome

/** A workout session. [endedAt] is null while open. [sets] are ordered by plannedExerciseId, then setNumber. */
data class WorkoutSession(
    val id: Long,
    val planId: Long,
    val dayId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val sets: List<LoggedSet>,
    val imported: Boolean = false,
    /** plannedExerciseId to the free-text feedback of that exercise. */
    val notes: Map<Long, String> = emptyMap(),
)

data class LoggedSet(
    val id: Long,
    val plannedExerciseId: Long,
    val setNumber: Int,
    val loadKg: Double,
    val reps: Int,
    val outcome: SetOutcome,
    /** Only for BUFFER; null for FAILURE or when not recorded (imported sets). */
    val rir: Int?,
    val completedAt: Long,
)

/** Sets of the most recent finished session that logged an exercise, with that session's start time (epoch millis). */
data class LastPerformance(val startedAt: Long, val sets: List<LoggedSet>)
