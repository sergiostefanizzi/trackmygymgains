package com.trackmygymgains.ui.workout

import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.repo.LoggedSet
import com.trackmygymgains.data.repo.PlannedExercise
import com.trackmygymgains.data.repo.LastPerformance
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class InputError { EMPTY, NOT_A_NUMBER, TOO_MANY_DECIMALS, OUT_OF_RANGE }

sealed interface InputResult<out T> {
    data class Valid<T>(val value: T) : InputResult<T>

    data class Invalid(val error: InputError) : InputResult<Nothing>
}

/**
 * Initial content of the set form. [loadText]/[repsText] are what the user has typed (initially empty);
 * [suggestedLoad]/[suggestedReps] are shown as placeholders and used when the field is left empty.
 */
data class SetFormValues(
    val loadText: String,
    val repsText: String,
    val outcome: SetOutcome,
    /** Current RIR of the stepper (kept even for FAILURE so toggling back restores it). */
    val rir: Int,
    val suggestedLoad: String? = null,
    val suggestedReps: String? = null,
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
        loadText = "",
        repsText = "",
        outcome = set.outcome,
        rir = set.rir ?: defaultRir,
        suggestedLoad = formatLoad(set.loadKg),
        suggestedReps = set.reps.toString(),
    )

    /** Typed load, or [suggested] when the field is empty. Empty without a suggestion is EMPTY. */
    fun resolveLoad(typed: String, suggested: String?): InputResult<Double> =
        if (typed.isBlank() && suggested != null) parseLoad(suggested) else parseLoad(typed)

    /** Typed reps, or [suggested] when the field is empty. Empty without a suggestion is EMPTY. */
    fun resolveReps(typed: String, suggested: String?): InputResult<Int> =
        if (typed.isBlank() && suggested != null) parseReps(suggested) else parseReps(typed)

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
        // A set already logged in this session keeps its load: the user chose it.
        if (previous != null) return formFrom(previous, rir)
        val source = lastPerformance.firstOrNull { it.setNumber == setNumber }
            ?: lastPerformance.lastOrNull()
            ?: return SetFormValues("", "", SetOutcome.BUFFER, rir)
        return formFrom(source, rir)
    }

    /** Increments offered next to the increase hint, in kg. */
    val INCREMENTS_KG = listOf(1.0, 2.5, 5.0)

    /** [baseLoad] (a formatted load) plus [incrementKg], capped at [MAX_LOAD_KG]; [baseLoad] when no increment. */
    fun suggestedLoadWith(baseLoad: String?, incrementKg: Double?): String? {
        if (incrementKg == null || baseLoad == null) return baseLoad
        val base = (parseLoad(baseLoad) as? InputResult.Valid)?.value ?: return baseLoad
        return formatLoad(minOf(base + incrementKg, MAX_LOAD_KG))
    }

    /** Upper bound of a rep range such as "6-8", "6–8" or "8"; null for anything else ("MAX", null). */
    fun repsUpperBound(targetReps: String?): Int? {
        val m = REPS_RANGE_REGEX.matchEntire(targetReps?.trim() ?: return null) ?: return null
        return m.groupValues[2].ifEmpty { m.groupValues[1] }.toIntOrNull()
    }

    /** True when [last] covers every planned set and all of them reached the top of the rep range. */
    fun shouldSuggestIncrease(exercise: PlannedExercise, last: List<LoggedSet>): Boolean {
        val bound = repsUpperBound(exercise.targetReps) ?: return false
        return last.isNotEmpty() && last.size >= exercise.sets && last.all { it.reps >= bound }
    }

    /** Localised pieces of the "Ultima volta" line (format strings use [String.format] placeholders). */
    data class LastLabels(
        /** args: date, loads x reps, outcome. */
        val line: String,
        /** args: loads, reps. */
        val loadsReps: String,
        /** arg: rir text. */
        val rir: String,
        val failure: String,
        val failureShort: String,
        val notAvailable: String,
    )

    /** "Ultima volta · 26 ott: 30 kg × 8 / 8 / 8 · RIR 1". The year is added only when not [currentYear]. */
    fun lastPerformanceLine(last: LastPerformance, labels: LastLabels, zone: ZoneId, currentYear: Int): String {
        val sets = last.sets
        val date = Instant.ofEpochMilli(last.startedAt).atZone(zone)
        val pattern = if (date.year == currentYear) "d MMM" else "d MMM yyyy"
        val dateText = DateTimeFormatter.ofPattern(pattern, Locale.ITALIAN).format(date)

        val loads = sets.map { formatLoad(it.loadKg) }
        val loadsText = if (loads.distinct().size == 1) loads.first() else loads.joinToString(" / ")
        val loadsReps = labels.loadsReps.format(loadsText, sets.joinToString(" / ") { it.reps.toString() })

        val outcomes = sets.map { it.outcome to it.rir }
        val outcomeText = if (outcomes.distinct().size == 1) {
            if (sets.first().outcome == SetOutcome.FAILURE) {
                labels.failure
            } else {
                labels.rir.format(sets.first().rir?.toString() ?: labels.notAvailable)
            }
        } else {
            labels.rir.format(
                sets.joinToString(" / ") {
                    if (it.outcome == SetOutcome.FAILURE) labels.failureShort else it.rir?.toString() ?: labels.notAvailable
                },
            )
        }
        return labels.line.format(dateText, loadsReps, outcomeText)
    }

    private val LOAD_REGEX = Regex("[0-9]*[.,]?[0-9]*")
    private val REPS_REGEX = Regex("[0-9]+")
    private val REPS_RANGE_REGEX = Regex("([0-9]+)(?:\\s*[-–—]\\s*([0-9]+))?")
}
