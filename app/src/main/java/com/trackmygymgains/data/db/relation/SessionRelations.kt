package com.trackmygymgains.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.trackmygymgains.data.db.entity.SetLogEntity
import com.trackmygymgains.data.db.entity.WorkoutSessionEntity

/** A workout session with its logged sets. Room does not order @Relation lists, so callers sort. */
data class SessionWithSets(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<SetLogEntity>,
)
