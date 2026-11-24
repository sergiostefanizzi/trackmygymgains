package com.trackmygymgains.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.trackmygymgains.data.model.SetOutcome

@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlannedExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannedExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("plannedExerciseId"),
        Index(value = ["sessionId", "plannedExerciseId", "setNumber"], unique = true),
    ],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val plannedExerciseId: Long,
    /** 1-based. */
    val setNumber: Int,
    val loadKg: Double,
    val reps: Int,
    val outcome: SetOutcome,
    /** Only for BUFFER; null for FAILURE. */
    val rir: Int?,
    /** Epoch millis. */
    val completedAt: Long,
)
