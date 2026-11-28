package com.trackmygymgains.data.db

import androidx.room.TypeConverter
import com.trackmygymgains.data.model.SetOutcome

class Converters {
    @TypeConverter
    fun outcomeToString(outcome: SetOutcome): String = outcome.name

    @TypeConverter
    fun stringToOutcome(value: String): SetOutcome = SetOutcome.valueOf(value)
}
