package com.trackmygymgains.data.repo

import androidx.room.withTransaction
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.db.entity.SetLogEntity
import com.trackmygymgains.data.db.entity.WorkoutSessionEntity
import com.trackmygymgains.data.db.relation.SessionWithSets
import com.trackmygymgains.data.model.SetOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

class SessionRepository(
    private val db: AppDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao get() = db.sessionDao()

    /** Opens a session for [dayId] of its plan. Throws [IllegalStateException] if a session is already open. */
    suspend fun startSession(dayId: Long): Long = db.withTransaction {
        check(dao.countOpenSessions() == 0) { "A workout session is already open" }
        val planId = dao.planIdForDay(dayId) ?: throw IllegalArgumentException("Unknown day $dayId")
        dao.insertSession(
            WorkoutSessionEntity(planId = planId, dayId = dayId, startedAt = clock.millis(), endedAt = null),
        )
    }

    fun observeOpenSession(): Flow<WorkoutSession?> = dao.observeOpenSession().map { it?.toDomain() }

    suspend fun getOpenSession(): WorkoutSession? = dao.getOpenSession()?.toDomain()

    fun observeSession(id: Long): Flow<WorkoutSession?> = dao.observeSession(id).map { it?.toDomain() }

    /**
     * Logs a set. Logging the same (session, exercise, setNumber) again replaces the values and keeps the id.
     * The session must be open.
     */
    suspend fun logSet(
        sessionId: Long,
        plannedExerciseId: Long,
        setNumber: Int,
        loadKg: Double,
        reps: Int,
        outcome: SetOutcome,
        rir: Int?,
    ): Long {
        require(loadKg.isFinite() && loadKg >= 0.0) { "loadKg must be >= 0, was $loadKg" }
        require(reps >= 0) { "reps must be >= 0, was $reps" }
        require(setNumber >= 1) { "setNumber must be >= 1, was $setNumber" }
        when (outcome) {
            SetOutcome.BUFFER -> require(rir != null && rir in 0..10) { "BUFFER sets need rir in 0..10, was $rir" }
            SetOutcome.FAILURE -> require(rir == null) { "FAILURE sets must not have rir, was $rir" }
        }
        return db.withTransaction {
            val session = dao.findSession(sessionId)
            check(session != null && session.endedAt == null) { "Session $sessionId is not open" }
            val now = clock.millis()
            val existing = dao.findSet(sessionId, plannedExerciseId, setNumber)
            if (existing != null) {
                dao.updateSet(
                    existing.copy(loadKg = loadKg, reps = reps, outcome = outcome, rir = rir, completedAt = now),
                )
                existing.id
            } else {
                dao.insertSet(
                    SetLogEntity(
                        sessionId = sessionId,
                        plannedExerciseId = plannedExerciseId,
                        setNumber = setNumber,
                        loadKg = loadKg,
                        reps = reps,
                        outcome = outcome,
                        rir = rir,
                        completedAt = now,
                    ),
                )
            }
        }
    }

    suspend fun deleteSet(setId: Long) = dao.deleteSet(setId)

    /** Marks the session as ended now. No-op if it is already finished. */
    suspend fun finishSession(sessionId: Long) = db.withTransaction {
        val session = dao.findSession(sessionId) ?: throw IllegalArgumentException("Unknown session $sessionId")
        if (session.endedAt == null) {
            dao.markEnded(sessionId, clock.millis())
        }
    }

    /**
     * Deletes an open session and its sets ("Annulla allenamento"). No-op for an unknown id.
     * Throws [IllegalStateException] for a finished session: only open sessions can be discarded.
     */
    suspend fun discardSession(sessionId: Long) = db.withTransaction {
        val session = dao.findSession(sessionId) ?: return@withTransaction
        check(session.endedAt == null) { "Session $sessionId is finished and cannot be discarded" }
        dao.deleteSetsOfSession(sessionId)
        dao.deleteSession(sessionId)
    }

    /** Finished sessions (open ones excluded), newest first by start time, with their sets ordered. */
    fun observeFinishedSessions(): Flow<List<WorkoutSession>> =
        dao.observeFinishedSessions().map { sessions -> sessions.map { it.toDomain() } }

    /**
     * Deletes a finished session and its sets ("Elimina" in the history). No-op for an unknown id.
     * Throws [IllegalStateException] for an open session: open ones are discarded with [discardSession].
     */
    suspend fun deleteFinishedSession(sessionId: Long) = db.withTransaction {
        val session = dao.findSession(sessionId) ?: return@withTransaction
        check(session.endedAt != null) { "Session $sessionId is open and cannot be deleted from the history" }
        dao.deleteSetsOfSession(sessionId)
        dao.deleteSession(sessionId)
    }

    /**
     * Sets of the most recent finished session that logged an exercise named [exerciseName]
     * (case-insensitive, trimmed), ordered by setNumber. Sessions in [excludeSessionId] are ignored.
     */
    suspend fun lastPerformance(exerciseName: String, excludeSessionId: Long? = null): List<LoggedSet> =
        dao.lastPerformance(exerciseName.trim(), excludeSessionId).map { it.toDomain() }

    /** Like [lastPerformance] but also carries the date of the session; null when there is no history. */
    suspend fun lastPerformanceWithDate(exerciseName: String, excludeSessionId: Long? = null): LastPerformance? {
        val entities = dao.lastPerformance(exerciseName.trim(), excludeSessionId)
        val sessionId = entities.firstOrNull()?.sessionId ?: return null
        val startedAt = dao.getSession(sessionId)?.session?.startedAt ?: return null
        return LastPerformance(startedAt, entities.map { it.toDomain() })
    }

    private fun SessionWithSets.toDomain(): WorkoutSession = WorkoutSession(
        id = session.id,
        planId = session.planId,
        dayId = session.dayId,
        startedAt = session.startedAt,
        endedAt = session.endedAt,
        sets = sets.sortedWith(compareBy({ it.plannedExerciseId }, { it.setNumber })).map { it.toDomain() },
        imported = session.imported,
        notes = notes.associate { it.plannedExerciseId to it.note },
    )

    private fun SetLogEntity.toDomain(): LoggedSet = LoggedSet(
        id = id,
        plannedExerciseId = plannedExerciseId,
        setNumber = setNumber,
        loadKg = loadKg,
        reps = reps,
        outcome = outcome,
        rir = rir,
        completedAt = completedAt,
    )
}
