package com.trackmygymgains.data.repo

import androidx.room.withTransaction
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.db.entity.PlannedExerciseEntity
import com.trackmygymgains.data.db.entity.WorkoutDayEntity
import com.trackmygymgains.data.db.entity.WorkoutPlanEntity
import com.trackmygymgains.data.db.relation.DayWithExercises
import com.trackmygymgains.data.db.relation.PlanWithDays
import com.trackmygymgains.data.plan.ParsedPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

class PlanRepository(
    private val db: AppDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao get() = db.planDao()

    /**
     * Imports [parsed] as the only active plan, in one transaction. Previous plans are kept but deactivated.
     * Returns the id of the new plan.
     */
    suspend fun importPlan(parsed: ParsedPlan, sourceFileName: String?, planName: String = parsed.sheetName): Long =
        db.withTransaction {
            dao.deactivateAll()
            val planId = dao.insertPlan(
                WorkoutPlanEntity(
                    name = planName,
                    sourceFileName = sourceFileName,
                    sheetName = parsed.sheetName,
                    importedAt = clock.millis(),
                    isActive = true,
                ),
            )
            parsed.days.forEachIndexed { dayIndex, day ->
                val dayId = dao.insertDay(
                    WorkoutDayEntity(planId = planId, name = day.name, position = dayIndex),
                )
                dao.insertExercises(
                    day.exercises.mapIndexed { index, ex ->
                        PlannedExerciseEntity(
                            dayId = dayId,
                            position = index,
                            name = ex.name,
                            sets = ex.sets,
                            targetReps = ex.targetReps,
                            targetRir = ex.targetRir,
                            restSeconds = ex.restSeconds,
                            notes = ex.notes,
                        )
                    },
                )
            }
            planId
        }

    fun observeActivePlan(): Flow<WorkoutPlan?> =
        dao.observeActivePlan().map { it?.toDomain() }

    suspend fun getActivePlan(): WorkoutPlan? = dao.getActivePlan()?.toDomain()

    fun observeHasActivePlan(): Flow<Boolean> = observeActivePlan().map { it != null }

    /** A day of any plan (active or not), e.g. the day of an open session after a re-import. */
    suspend fun getDay(dayId: Long): WorkoutDay? = dao.getDay(dayId)?.toDomain()

    private fun PlanWithDays.toDomain(): WorkoutPlan = WorkoutPlan(
        id = plan.id,
        name = plan.name,
        sheetName = plan.sheetName,
        sourceFileName = plan.sourceFileName,
        importedAt = plan.importedAt,
        days = days.sortedBy { it.day.position }.map { it.toDomain() },
    )

    private fun DayWithExercises.toDomain(): WorkoutDay = WorkoutDay(
        id = day.id,
        name = day.name,
        position = day.position,
        exercises = exercises.sortedBy { it.position }.map { e ->
            PlannedExercise(
                id = e.id,
                position = e.position,
                name = e.name,
                sets = e.sets,
                targetReps = e.targetReps,
                targetRir = e.targetRir,
                restSeconds = e.restSeconds,
                notes = e.notes,
            )
        },
    )
}
