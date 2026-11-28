package com.trackmygymgains.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.trackmygymgains.data.db.dao.PlanDao
import com.trackmygymgains.data.db.dao.SessionDao
import com.trackmygymgains.data.db.entity.ExerciseNoteEntity
import com.trackmygymgains.data.db.entity.PlannedExerciseEntity
import com.trackmygymgains.data.db.entity.SetLogEntity
import com.trackmygymgains.data.db.entity.WorkoutDayEntity
import com.trackmygymgains.data.db.entity.WorkoutPlanEntity
import com.trackmygymgains.data.db.entity.WorkoutSessionEntity

@Database(
    entities = [
        WorkoutPlanEntity::class,
        WorkoutDayEntity::class,
        PlannedExerciseEntity::class,
        WorkoutSessionEntity::class,
        SetLogEntity::class,
        ExerciseNoteEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun sessionDao(): SessionDao
}
