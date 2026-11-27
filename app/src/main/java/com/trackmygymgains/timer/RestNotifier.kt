package com.trackmygymgains.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trackmygymgains.MainActivity
import com.trackmygymgains.R
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Mirrors the app-wide [RestTimer] into system surfaces, without a foreground service:
 *  - while Running: an ongoing silent notification with a native countdown chronometer (no per-second
 *    updates) and -15 / +15 / Salta actions, plus an AlarmManager alarm at the end of the rest;
 *  - Idle after Running (skip): both are cancelled;
 *  - Finished: the ongoing notification is cancelled. The vibration and the "Recupero terminato"
 *    notification come from [RestTimerAlarmReceiver] (single source), except when an adjustment
 *    ended the timer early (the alarm would not fire at the right time), in which case it is triggered here.
 *
 * Started from `TrackMyGymGainsApp.onCreate`, so it also runs when the process is recreated and the
 * timer is restored.
 */
class RestNotifier(
    context: Context,
    private val timer: RestTimer,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val context = context.applicationContext ?: context
    private var job: Job? = null
    private var postedKey: Pair<Long, String?>? = null
    private var scheduledEndAt: Long? = null

    fun start() {
        if (job != null) return
        job = scope.launch { timer.state.collect { onState(it) } }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun onState(state: RestTimerState) {
        when (state) {
            is RestTimerState.Running -> {
                val key = state.endAtMillis to state.label
                if (key != postedKey) {
                    postedKey = key
                    postOngoing(state)
                }
                if (scheduledEndAt != state.endAtMillis) {
                    scheduledEndAt = state.endAtMillis
                    scheduleAlarm(context, state.endAtMillis)
                }
            }
            is RestTimerState.Idle -> {
                if (postedKey != null) cancelOngoing(context)
                postedKey = null
                if (scheduledEndAt != null) cancelAlarm(context)
                scheduledEndAt = null
            }
            is RestTimerState.Finished -> {
                if (postedKey != null) cancelOngoing(context)
                postedKey = null
                val pending = scheduledEndAt
                if (pending != null && !state.restored && pending > clock.millis() + EARLY_FINISH_TOLERANCE_MS) {
                    // Ended early by an adjustment: the alarm for the old end must not fire later.
                    cancelAlarm(context)
                    scheduledEndAt = null
                    RestEndHandler.handle(context, pending, state.label, validate = false)
                }
                // Otherwise the alarm (about now) does the job; keep it as the fallback for late inexact alarms.
            }
        }
    }

    private fun postOngoing(state: RestTimerState.Running) {
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(state.label ?: context.getString(R.string.notif_rest_title_default))
            .setWhen(state.endAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent(context))
            .addAction(0, context.getString(R.string.workout_rest_minus), actionIntent(context, ACTION_MINUS))
            .addAction(0, context.getString(R.string.workout_rest_plus), actionIntent(context, ACTION_PLUS))
            .addAction(0, context.getString(R.string.workout_rest_skip), actionIntent(context, ACTION_SKIP))
        notifySafely(context, NOTIFICATION_RUNNING, builder.build())
    }

    companion object {
        const val CHANNEL_RUNNING = "rest_timer"
        const val CHANNEL_DONE = "rest_done"
        const val NOTIFICATION_RUNNING = 1001
        const val NOTIFICATION_DONE = 1002
        const val ACTION_MINUS = "com.trackmygymgains.timer.REST_MINUS"
        const val ACTION_PLUS = "com.trackmygymgains.timer.REST_PLUS"
        const val ACTION_SKIP = "com.trackmygymgains.timer.REST_SKIP"
        const val EXTRA_END_AT = "end_at_millis"
        const val ADJUST_SECONDS = 15
        private const val EARLY_FINISH_TOLERANCE_MS = 1_000L
        private const val REQUEST_ALARM = 1
        private const val REQUEST_OPEN = 2
        private const val REQUEST_MINUS = 3
        private const val REQUEST_PLUS = 4
        private const val REQUEST_SKIP = 5

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RUNNING,
                    context.getString(R.string.notif_channel_rest_running),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DONE,
                    context.getString(R.string.notif_channel_rest_done),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    // The vibration comes from RestTimerAlarmReceiver (alarm usage); avoid a double one.
                    enableVibration(false)
                },
            )
        }

        fun cancelOngoing(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_RUNNING)
        }

        /** Posts, ignoring a missing POST_NOTIFICATIONS permission (the app still works without). */
        fun notifySafely(context: Context, id: Int, notification: Notification) {
            try {
                val nm = NotificationManagerCompat.from(context)
                if (nm.areNotificationsEnabled()) nm.notify(id, notification)
            } catch (_: SecurityException) {
                // Permission denied: banner and vibration remain.
            }
        }

        fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context, REQUEST_OPEN, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun actionIntent(context: Context, action: String): PendingIntent {
            val request = when (action) {
                ACTION_MINUS -> REQUEST_MINUS
                ACTION_PLUS -> REQUEST_PLUS
                else -> REQUEST_SKIP
            }
            val intent = Intent(context, RestTimerActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun alarmIntent(context: Context, endAtMillis: Long): PendingIntent {
            val intent = Intent(context, RestTimerAlarmReceiver::class.java).putExtra(EXTRA_END_AT, endAtMillis)
            return PendingIntent.getBroadcast(
                context, REQUEST_ALARM, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun scheduleAlarm(context: Context, endAtMillis: Long) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = alarmIntent(context, endAtMillis)
            am.cancel(pi) // drop a previous alarm for an older end (extras are not part of PendingIntent identity)
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            try {
                if (exact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
                }
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
            }
        }

        fun cancelAlarm(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            am.cancel(alarmIntent(context, 0L))
        }
    }
}
