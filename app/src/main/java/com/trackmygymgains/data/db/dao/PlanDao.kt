package com.trackmygymgains.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.trackmygymgains.data.db.entity.PlannedExerciseEntity
import com.trackmygymgains.data.db.entity.WorkoutDayEntity
import com.trackmygymgains.data.db.entity.WorkoutPlanEntity
import com.trackmygymgains.data.db.relation.DayWithExercises
import com.trackmygymgains.data.db.relation.PlanWithDays
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Insert
    suspend fun insertPlan(plan: WorkoutPlanEntity): Long

    @Insert
    suspend fun insertDay(day: WorkoutDayEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<PlannedExerciseEntity>): List<Long>

    @Query("UPDATE workout_plan SET isActive = 0")
    suspend fun deactivateAll()

    @Transaction
    @Query("SELECT * FROM workout_plan WHERE isActive = 1 LIMIT 1")
    fun observeActivePlan(): Flow<PlanWithDays?>

    @Transaction
    @Query("SELECT * FROM workout_plan WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlan(): PlanWithDays?

    @Transaction
    @Query("SELECT * FROM workout_plan WHERE id = :id")
    suspend fun getPlan(id: Long): PlanWithDays?

    @Transaction
    @Query("SELECT * FROM workout_day WHERE id = :dayId")
    suspend fun getDay(dayId: Long): DayWithExercises?

    @Query("DELETE FROM workout_plan WHERE id = :id")
    suspend fun deletePlan(id: Long)
}
