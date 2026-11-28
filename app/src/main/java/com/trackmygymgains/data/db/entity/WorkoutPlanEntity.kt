package com.trackmygymgains.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_plan")
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceFileName: String?,
    val sheetName: String,
    /** Epoch millis. */
    val importedAt: Long,
    val isActive: Boolean,
)
