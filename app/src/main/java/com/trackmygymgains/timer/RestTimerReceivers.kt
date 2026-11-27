package com.trackmygymgains.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.trackmygymgains.R
import com.trackmygymgains.TrackMyGymGainsApp

private fun Context.restTimer(): RestTimer = (applicationContext as TrackMyGymGainsApp).container.restTimer

/** Handles the -15 / +15 / Salta actions of the ongoing notification. Not exported. */
class RestTimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timer = context.restTimer()
        when (intent.action) {
            RestNotifier.ACTION_MINUS -> timer.adjust(-RestNotifier.ADJUST_SECONDS)
            RestNotifier.ACTION_PLUS -> timer.adjust(RestNotifier.ADJUST_SECONDS)
            RestNotifier.ACTION_SKIP -> timer.skip()
        }
    }
}

/** Fired by AlarmManager at the end of the rest. Not exported. */
class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val endAt = intent.getLongExtra(RestNotifier.EXTRA_END_AT, -1L)
        if (endAt < 0) return
        val timer = context.restTimer()
        val label = when (val s = timer.state.value) {
            is RestTimerState.Running -> s.label
            is RestTimerState.Finished -> s.label
            RestTimerState.Idle -> null
        }
        RestEndHandler.handle(context, endAt, label, validate = true, timer = timer)
    }
}

/**
 * The single place that vibrates and posts "Recupero terminato".
 *
 * Stale-alarm rule (when [validate]): the alarm for end time E is honoured only if the timer is
 * Running with that same end (alarm marginally early) or has reached its natural end at E
 * ([RestTimer.hasFinishedAt], true for a ticker finish in this process and for a timer found expired at
 * restore after process death). After Salta the state is Idle and the finish marker is not set, after
 * an adjust/restart the running end differs: both are ignored. A per-end dedupe guarantees one
 * vibration even if the alarm and the in-process early-finish path both trigger.
 */
object RestEndHandler {
    private val PATTERN = longArrayOf(0, 300, 120, 300, 120, 600)
    private var lastHandledEndAt = -1L

    @Synchronized
    fun handle(context: Context, endAt: Long, label: String?, validate: Boolean, timer: RestTimer? = null) {
        if (endAt == lastHandledEndAt) return
        if (validate) {
            val t = timer ?: context.restTimer()
            val s = t.state.value
            val valid = (s is RestTimerState.Running && s.endAtMillis == endAt) || t.hasFinishedAt(endAt)
            if (!valid) return
        }
        lastHandledEndAt = endAt
        vibrate(context)
        RestNotifier.cancelOngoing(context)
        val notification = NotificationCompat.Builder(context, RestNotifier.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(context.getString(R.string.notif_rest_done_title))
            .setContentText(label ?: context.getString(R.string.notif_rest_done_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(RestNotifier.openAppIntent(context))
            .build()
        RestNotifier.notifySafely(context, RestNotifier.NOTIFICATION_DONE, notification)
    }

    /** Test hook: forget the dedupe memory. */
    internal fun resetForTesting() {
        lastHandledEndAt = -1L
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1), attrs)
    }
}
