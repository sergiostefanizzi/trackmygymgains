package com.trackmygymgains.data.repo

import androidx.room.withTransaction
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.db.entity.ExerciseNoteEntity
import com.trackmygymgains.data.db.entity.PlannedExerciseEntity
import com.trackmygymgains.data.db.entity.SetLogEntity
import com.trackmygymgains.data.db.entity.WorkoutSessionEntity
import com.trackmygymgains.data.db.entity.WorkoutDayEntity
import com.trackmygymgains.data.db.entity.WorkoutPlanEntity
import com.trackmygymgains.data.db.relation.DayWithExercises
import com.trackmygymgains.data.db.relation.PlanWithDays
import com.trackmygymgains.data.plan.ParsedPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Outcome of [PlanRepository.importPlan]. */
data class ImportResult(val planId: Long, val importedSessions: Int, val skippedDuplicates: Int)

class PlanRepository(
    private val db: AppDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val dao get() = db.planDao()
    private val sessionDao get() = db.sessionDao()

    /**
     * Imports [parsed] as the only active plan, in one transaction. Previous plans are kept but deactivated.
     * The history in [ParsedPlan.history] is imported in the same transaction, skipping
     * sessions already present (same day name and local date as a finished session).
     */
    suspend fun importPlan(parsed: ParsedPlan, sourceFileName: String?, planName: String = parsed.sheetName): ImportResult =
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
            val dayIds = ArrayList<Long>()
            val exerciseIds = ArrayList<List<Long>>()
            parsed.days.forEachIndexed { dayIndex, day ->
                val dayId = dao.insertDay(
                    WorkoutDayEntity(planId = planId, name = day.name, position = dayIndex),
                )
                dayIds += dayId
                exerciseIds += dao.insertExercises(
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
            importHistory(parsed, planId, dayIds, exerciseIds)
        }

    private suspend fun importHistory(
        parsed: ParsedPlan,
        planId: Long,
        dayIds: List<Long>,
        exerciseIds: List<List<Long>>,
    ): ImportResult {
        if (parsed.history.isEmpty()) return ImportResult(planId, 0, 0)
        val seen = sessionDao.finishedSessionKeys()
            .mapTo(HashSet()) { it.dayName.trim().lowercase() to Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        var imported = 0
        var skipped = 0
        for (h in parsed.history) {
            val key = parsed.days[h.dayIndex].name.trim().lowercase() to h.date
            if (!seen.add(key)) {
                skipped++
                continue
            }
            val startedAt = h.date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            val sessionId = sessionDao.insertSession(
                WorkoutSessionEntity(
                    planId = planId,
                    dayId = dayIds[h.dayIndex],
                    startedAt = startedAt,
                    endedAt = startedAt,
                    imported = true,
                ),
            )
            for (ex in h.exercises) {
                val exerciseId = exerciseIds[h.dayIndex][ex.exerciseIndex]
                if (ex.sets.isNotEmpty()) {
                    sessionDao.insertSets(
                        ex.sets.mapIndexed { i, s ->
                            SetLogEntity(
                                sessionId = sessionId,
                                plannedExerciseId = exerciseId,
                                setNumber = i + 1,
                                loadKg = s.loadKg,
                                reps = s.reps,
                                outcome = s.outcome,
                                rir = s.rir,
                                completedAt = startedAt,
                            )
                        },
                    )
                }
                ex.feedback?.let {
                    sessionDao.insertNote(ExerciseNoteEntity(sessionId = sessionId, plannedExerciseId = exerciseId, note = it))
                }
            }
            imported++
        }
        return ImportResult(planId, imported, skipped)
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
