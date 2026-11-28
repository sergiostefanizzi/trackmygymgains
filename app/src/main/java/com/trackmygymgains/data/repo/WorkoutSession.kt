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
)

data class LoggedSet(
    val id: Long,
    val plannedExerciseId: Long,
    val setNumber: Int,
    val loadKg: Double,
    val reps: Int,
    val outcome: SetOutcome,
    /** Only for BUFFER; null for FAILURE. */
    val rir: Int?,
    val completedAt: Long,
)
