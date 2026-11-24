package com.trackmygymgains.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.trackmygymgains.data.db.entity.PlannedExerciseEntity
import com.trackmygymgains.data.db.entity.WorkoutDayEntity
import com.trackmygymgains.data.db.entity.WorkoutPlanEntity

/** A day with its exercises. Room does not order @Relation lists, so callers sort by position. */
data class DayWithExercises(
    @Embedded val day: WorkoutDayEntity,
    @Relation(parentColumn = "id", entityColumn = "dayId")
    val exercises: List<PlannedExerciseEntity>,
)

/** A plan with its days (and their exercises). Days are not ordered by Room; callers sort by position. */
data class PlanWithDays(
    @Embedded val plan: WorkoutPlanEntity,
    @Relation(entity = WorkoutDayEntity::class, parentColumn = "id", entityColumn = "planId")
    val days: List<DayWithExercises>,
)
