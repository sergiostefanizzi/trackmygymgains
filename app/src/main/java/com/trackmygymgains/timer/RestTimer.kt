package com.trackmygymgains.timer

import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface RestTimerState {
    data object Idle : RestTimerState

    /** [remainingMillis] is `endAtMillis - clock.millis()` at emission time (> 0). */
    data class Running(
        val endAtMillis: Long,
        val totalSeconds: Int,
        val remainingMillis: Long,
        val label: String?,
    ) : RestTimerState {
        /** Remaining seconds rounded up: 3:00 at start, 0:01 in the last second. */
        val remainingSeconds: Int get() = ((remainingMillis + 999) / 1000).toInt()
    }

    /**
     * The timer reached zero. [restored] is true when it expired while the process was dead
     * (the user should still see "Recupero terminato"; the alarm still rings, as it is a natural end).
     */
    data class Finished(val totalSeconds: Int, val label: String?, val restored: Boolean = false) : RestTimerState
}

/**
 * App-wide rest timer based on an end timestamp, so it survives rotation and process death.
 *
 * State design: `Idle -> Running -> Finished -> Idle`. Finished is a *state*, not a one-shot
 * event, and it is entered exactly once per timer (a single ticker exists at a time and the
 * Running -> Finished transition clears it). While Finished, the alarm service rings and the banner shows
 * "Ferma" until the user calls [acknowledgeFinished]; [start] and [skip] also leave Finished (and stop the ringing).
 *
 * Restore on creation: a saved timer still in the future resumes Running; one that already
 * expired goes to Finished(restored = true) and the store is cleared. Finishing and [skip]
 * always clear the store.
 */
class RestTimer(
    private val scope: CoroutineScope,
    private val store: RestTimerStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val _state = MutableStateFlow<RestTimerState>(RestTimerState.Idle)
    val state: StateFlow<RestTimerState> = _state

    private var ticker: Job? = null
    private var endAtMillis = 0L
    private var totalSeconds = 0
    private var label: String? = null
    private var lastFinishedEndAt = 0L

    /**
     * True when a timer whose end was [endAtMillis] reached its natural end in this process (finished by
     * the ticker, or found already expired at restore). Used to validate end-of-rest alarms.
     */
    @Synchronized
    fun hasFinishedAt(endAtMillis: Long): Boolean = lastFinishedEndAt == endAtMillis

    init {
        val saved = store.load()
        if (saved != null) {
            if (saved.endAtMillis > clock.millis()) {
                begin(saved.endAtMillis, saved.totalSeconds, saved.label, persist = false)
            } else {
                store.clear()
                lastFinishedEndAt = saved.endAtMillis
                _state.value = RestTimerState.Finished(saved.totalSeconds, saved.label, restored = true)
            }
        }
    }

    /** Starts (or replaces) the timer. [label] is e.g. the exercise name. */
    @Synchronized
    fun start(durationSeconds: Int, label: String? = null) {
        if (durationSeconds <= 0) {
            skip()
            return
        }
        begin(clock.millis() + durationSeconds * 1000L, durationSeconds, label, persist = true)
    }

    /** Stops the timer (back to Idle, no Finished state). */
    @Synchronized
    fun skip() {
        ticker?.cancel()
        ticker = null
        store.clear()
        _state.value = RestTimerState.Idle
    }

    /**
     * Adds [deltaSeconds] (may be negative) to the remaining time; reaching 0 finishes the timer.
     * [RestTimerState.Running.totalSeconds] stays the planned rest so the progress bar visibly follows the
     * adjustment; it only grows when the new remaining time exceeds it (bar full, never above 100%).
     */
    @Synchronized
    fun adjust(deltaSeconds: Int) {
        if (_state.value !is RestTimerState.Running) return
        val newEnd = endAtMillis + deltaSeconds * 1000L
        val remaining = newEnd - clock.millis()
        if (remaining <= 0) {
            finish()
            return
        }
        val remainingSec = ((remaining + 999) / 1000).toInt()
        begin(newEnd, maxOf(totalSeconds, remainingSec), label, persist = true)
    }

    /** Leaves the Finished state (back to Idle). No-op in other states. */
    @Synchronized
    fun acknowledgeFinished() {
        if (_state.value is RestTimerState.Finished) _state.value = RestTimerState.Idle
    }

    private fun begin(end: Long, total: Int, label: String?, persist: Boolean) {
        ticker?.cancel()
        endAtMillis = end
        totalSeconds = total
        this.label = label
        if (persist) store.save(SavedRestTimer(end, total, label))
        emitRunning()
        lateinit var job: Job
        job = scope.launch {
            while (true) {
                val remaining = endAtMillis - clock.millis()
                if (remaining <= 0) break
                val toNextSecond = remaining % 1000
                delay(if (toNextSecond == 0L) 1000L else toNextSecond)
                synchronized(this@RestTimer) {
                    if (ticker !== job) return@launch
                    if (endAtMillis - clock.millis() <= 0) {
                        finish()
                        return@launch
                    }
                    emitRunning()
                }
            }
        }
        ticker = job
    }

    private fun emitRunning() {
        _state.value = RestTimerState.Running(endAtMillis, totalSeconds, endAtMillis - clock.millis(), label)
    }

    private fun finish() {
        ticker?.cancel()
        ticker = null
        store.clear()
        lastFinishedEndAt = endAtMillis
        _state.value = RestTimerState.Finished(totalSeconds, label)
    }
}
