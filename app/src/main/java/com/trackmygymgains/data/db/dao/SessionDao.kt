package com.trackmygymgains.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.trackmygymgains.data.db.entity.SetLogEntity
import com.trackmygymgains.data.db.entity.WorkoutSessionEntity
import com.trackmygymgains.data.db.relation.SessionWithSets
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT planId FROM workout_day WHERE id = :dayId")
    suspend fun planIdForDay(dayId: Long): Long?

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun findSession(id: Long): WorkoutSessionEntity?

    @Query("SELECT COUNT(*) FROM workout_session WHERE endedAt IS NULL")
    suspend fun countOpenSessions(): Int

    @Query("UPDATE workout_session SET endedAt = :endedAt WHERE id = :id")
    suspend fun markEnded(id: Long, endedAt: Long)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Transaction
    @Query("SELECT * FROM workout_session WHERE endedAt IS NULL LIMIT 1")
    fun observeOpenSession(): Flow<SessionWithSets?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE endedAt IS NULL LIMIT 1")
    suspend fun getOpenSession(): SessionWithSets?

    /** Finished sessions, newest first. Room does not order @Relation lists, so callers sort the sets. */
    @Transaction
    @Query("SELECT * FROM workout_session WHERE endedAt IS NOT NULL ORDER BY startedAt DESC, id DESC")
    fun observeFinishedSessions(): Flow<List<SessionWithSets>>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionWithSets?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun getSession(id: Long): SessionWithSets?

    @Query("SELECT * FROM set_log WHERE sessionId = :sessionId AND plannedExerciseId = :plannedExerciseId AND setNumber = :setNumber")
    suspend fun findSet(sessionId: Long, plannedExerciseId: Long, setNumber: Int): SetLogEntity?

    @Insert
    suspend fun insertSet(set: SetLogEntity): Long

    @Update
    suspend fun updateSet(set: SetLogEntity): Int

    @Query("DELETE FROM set_log WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM set_log WHERE sessionId = :sessionId")
    suspend fun deleteSetsOfSession(sessionId: Long)

    /**
     * Sets of the most recent finished session that logged an exercise with [name] (case-insensitive, trimmed),
     * restricted to exercises with that name. Matching is by name so history survives a plan re-import.
     */
    @Query(
        """
        SELECT * FROM set_log
        WHERE sessionId = (
            SELECT ws.id FROM workout_session ws
            JOIN set_log sl ON sl.sessionId = ws.id
            JOIN planned_exercise pe ON pe.id = sl.plannedExerciseId
            WHERE ws.endedAt IS NOT NULL
              AND LOWER(TRIM(pe.name)) = LOWER(TRIM(:name))
              AND (:excludeSessionId IS NULL OR ws.id <> :excludeSessionId)
            ORDER BY ws.startedAt DESC, ws.id DESC
            LIMIT 1
        )
        AND plannedExerciseId IN (
            SELECT pe2.id FROM planned_exercise pe2 WHERE LOWER(TRIM(pe2.name)) = LOWER(TRIM(:name))
        )
        ORDER BY setNumber
        """,
    )
    suspend fun lastPerformance(name: String, excludeSessionId: Long?): List<SetLogEntity>
}
