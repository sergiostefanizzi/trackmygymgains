package com.trackmygymgains.timer

import android.content.Context

/** Snapshot of a running rest timer, as persisted across process death. */
data class SavedRestTimer(val endAtMillis: Long, val totalSeconds: Int, val label: String?)

/** Persistence for the running rest timer. Pure interface: JVM-testable. */
interface RestTimerStore {
    fun save(timer: SavedRestTimer)
    fun load(): SavedRestTimer?
    fun clear()
}

/** In-memory store, used in tests. */
class InMemoryRestTimerStore(var saved: SavedRestTimer? = null) : RestTimerStore {
    override fun save(timer: SavedRestTimer) { saved = timer }
    override fun load(): SavedRestTimer? = saved
    override fun clear() { saved = null }
}

class SharedPreferencesRestTimerStore(context: Context) : RestTimerStore {
    private val prefs = (context.applicationContext ?: context)
        .getSharedPreferences("rest_timer", Context.MODE_PRIVATE)

    override fun save(timer: SavedRestTimer) {
        val editor = prefs.edit()
            .putLong(KEY_END, timer.endAtMillis)
            .putInt(KEY_TOTAL, timer.totalSeconds)
        if (timer.label == null) editor.remove(KEY_LABEL) else editor.putString(KEY_LABEL, timer.label)
        editor.apply()
    }

    override fun load(): SavedRestTimer? {
        if (!prefs.contains(KEY_END)) return null
        return SavedRestTimer(
            endAtMillis = prefs.getLong(KEY_END, 0L),
            totalSeconds = prefs.getInt(KEY_TOTAL, 0),
            label = prefs.getString(KEY_LABEL, null),
        )
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_END = "end_at_millis"
        const val KEY_TOTAL = "total_seconds"
        const val KEY_LABEL = "label"
    }
}
