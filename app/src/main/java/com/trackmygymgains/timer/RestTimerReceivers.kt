package com.trackmygymgains.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.app.Notification
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.trackmygymgains.R
import com.trackmygymgains.TrackMyGymGainsApp

private fun Context.restTimer(): RestTimer = (applicationContext as TrackMyGymGainsApp).container.restTimer

/** Handles the -15 / +15 / Salta actions of the ongoing notification and "Ferma" of the end alarm. Not exported. */
class RestTimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timer = context.restTimer()
        when (intent.action) {
            RestNotifier.ACTION_MINUS -> timer.adjust(-RestNotifier.ADJUST_SECONDS)
            RestNotifier.ACTION_PLUS -> timer.adjust(RestNotifier.ADJUST_SECONDS)
            RestNotifier.ACTION_SKIP -> timer.skip()
            RestNotifier.ACTION_STOP -> timer.acknowledgeFinished()
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
 * The single place that starts the end-of-rest alarm ([RestAlarmService]: vibration until stopped +
 * "Recupero terminato" notification).
 *
 * Stale-alarm rule (when [validate]): the alarm for end time E is honoured only if the timer is
 * Running with that same end (alarm marginally early) or is Finished after a natural end at E
 * ([RestTimer.hasFinishedAt], true for a ticker finish in this process and for a timer found expired at
 * restore after process death). After Salta the state is Idle, after Ferma too (nobody would stop the
 * ringing), after an adjust/restart the running end differs: all are ignored. A per-end dedupe guarantees
 * one ringing even if the alarm and the in-process early-finish path both trigger.
 *
 * If the foreground service cannot be started, falls back to a one-shot vibration + a plain notification.
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
            val valid = (s is RestTimerState.Running && s.endAtMillis == endAt) ||
                (s is RestTimerState.Finished && t.hasFinishedAt(endAt))
            if (!valid) return
        }
        lastHandledEndAt = endAt
        RestNotifier.cancelOngoing(context)
        try {
            ContextCompat.startForegroundService(context, RestAlarmService.startIntent(context, label))
        } catch (_: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException: still alert the user.
            vibrateOnce(context)
            RestNotifier.notifySafely(
                context,
                RestNotifier.NOTIFICATION_DONE,
                buildDoneNotification(context, label, withStopAction = false),
            )
        }
    }

    /** "Recupero terminato" notification; with a "Ferma" action when it belongs to the ringing service. */
    fun buildDoneNotification(context: Context, label: String?, withStopAction: Boolean): Notification {
        val builder = NotificationCompat.Builder(context, RestNotifier.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(context.getString(R.string.notif_rest_done_title))
            .setContentText(label ?: context.getString(R.string.notif_rest_done_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(RestNotifier.openAppIntent(context))
        if (withStopAction) {
            builder.setOngoing(true)
                .addAction(
                    0,
                    context.getString(R.string.workout_rest_stop),
                    RestNotifier.actionIntent(context, RestNotifier.ACTION_STOP),
                )
        } else {
            builder.setAutoCancel(true)
        }
        return builder.build()
    }

    /** Test hook: forget the dedupe memory. */
    internal fun resetForTesting() {
        lastHandledEndAt = -1L
    }

    @Suppress("DEPRECATION")
    private fun vibrateOnce(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1), alarmAttributes())
    }

    fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}
