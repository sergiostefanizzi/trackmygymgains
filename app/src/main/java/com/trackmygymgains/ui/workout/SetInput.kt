package com.trackmygymgains.ui.workout

import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.data.repo.PlannedExercise
import java.math.BigDecimal

enum class InputError { EMPTY, NOT_A_NUMBER, TOO_MANY_DECIMALS, OUT_OF_RANGE }

sealed interface InputResult<out T> {
    data class Valid<T>(val value: T) : InputResult<T>

    data class Invalid(val error: InputError) : InputResult<Nothing>
}

/** Text content of the set form (what the user sees in the fields). */
data class SetFormValues(
    val loadText: String,
    val repsText: String,
    val outcome: SetOutcome,
    /** Current RIR of the stepper (kept even for FAILURE so toggling back restores it). */
    val rir: Int,
)

/** Pure (Android-free) parsing, validation and prefill helpers for the set form. */
object SetInput {
    const val MAX_LOAD_KG = 1000.0
    const val MAX_REPS = 999
    const val MAX_RIR = 10
    const val DEFAULT_RIR = 2

    /** Parses a load in kg: "," or "." as separator, 0..[MAX_LOAD_KG], at most 2 decimals. */
    fun parseLoad(text: String): InputResult<Double> {
        val t = text.trim()
        if (t.isEmpty()) return InputResult.Invalid(InputError.EMPTY)
        if (!LOAD_REGEX.matches(t) || t.none { it in '0'..'9' }) return InputResult.Invalid(InputError.NOT_A_NUMBER)
        val decimals = t.substringAfter(if (',' in t) ',' else '.', "")
        if (decimals.length > 2) return InputResult.Invalid(InputError.TOO_MANY_DECIMALS)
        val value = t.replace(',', '.').toDouble()
        if (value > MAX_LOAD_KG) return InputResult.Invalid(InputError.OUT_OF_RANGE)
        return InputResult.Valid(value)
    }

    /** Parses reps: integer in 1..[MAX_REPS]. */
    fun parseReps(text: String): InputResult<Int> {
        val t = text.trim()
        if (t.isEmpty()) return InputResult.Invalid(InputError.EMPTY)
        if (!REPS_REGEX.matches(t)) return InputResult.Invalid(InputError.NOT_A_NUMBER)
        val value = t.toIntOrNull() ?: return InputResult.Invalid(InputError.OUT_OF_RANGE)
        if (value !in 1..MAX_REPS) return InputResult.Invalid(InputError.OUT_OF_RANGE)
        return InputResult.Valid(value)
    }

    /** Formats a load with a comma separator and no trailing zeros (80.0 -> "80", 82.5 -> "82,5"). */
    fun formatLoad(kg: Double): String =
        BigDecimal.valueOf(kg).stripTrailingZeros().toPlainString().replace('.', ',')

    /** First integer found in a RIR target such as "1-2" or "1-2 / MAX REPS"; [DEFAULT_RIR] if none. */
    fun defaultRir(targetRir: String?): Int {
        val first = targetRir?.let { Regex("[0-9]+").find(it)?.value?.toIntOrNull() }
        return (first ?: DEFAULT_RIR).coerceIn(0, MAX_RIR)
    }

    fun formFrom(set: LoggedSet, defaultRir: Int): SetFormValues = SetFormValues(
        loadText = formatLoad(set.loadKg),
        repsText = set.reps.toString(),
        outcome = set.outcome,
        rir = set.rir ?: defaultRir,
    )

    /**
     * Initial form values for set [setNumber] of [exercise]: previous logged set of this exercise in the
     * session, else the same set (or the last one) of the last finished performance, else empty.
     */
    fun prefill(
        exercise: PlannedExercise,
        setNumber: Int,
        loggedInSession: List<LoggedSet>,
        lastPerformance: List<LoggedSet>,
    ): SetFormValues {
        val rir = defaultRir(exercise.targetRir)
        val previous = loggedInSession.filter { it.setNumber < setNumber }.maxByOrNull { it.setNumber }
        val source = previous
            ?: lastPerformance.firstOrNull { it.setNumber == setNumber }
            ?: lastPerformance.lastOrNull()
        return source?.let { formFrom(it, rir) }
            ?: SetFormValues("", "", SetOutcome.BUFFER, rir)
    }

    private val LOAD_REGEX = Regex("[0-9]*[.,]?[0-9]*")
    private val REPS_REGEX = Regex("[0-9]+")
}
