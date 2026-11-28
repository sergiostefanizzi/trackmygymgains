package com.trackmygymgains.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat

/**
 * Alarm-clock style end-of-rest ringing: a foreground service that shows the "Recupero terminato"
 * notification (with "Ferma") and vibrates in a repeating pattern until stopped.
 *
 * Stopped by [RestNotifier] when the timer state leaves Finished (Ferma, new rest, Salta, workout
 * finished/cancelled), or automatically after [MAX_RING_MILLIS] (safety cap): then the vibration stops and
 * the notification stays as a normal, dismissible "Recupero terminato" notification.
 */
class RestAlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var label: String? = null
    private val capRunnable = Runnable { onCap() }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        label = intent?.getStringExtra(EXTRA_LABEL)
        ServiceCompat.startForeground(
            this,
            RestNotifier.NOTIFICATION_DONE,
            RestEndHandler.buildDoneNotification(this, label, withStopAction = true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        acquireWakeLock()
        val vibrator = getSystemService(Vibrator::class.java)
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, 0), RestEndHandler.alarmAttributes())
        }
        handler.removeCallbacks(capRunnable)
        handler.postDelayed(capRunnable, MAX_RING_MILLIS)
        return START_NOT_STICKY
    }

    private fun onCap() {
        silence()
        // Keep the notification, as a plain one the user can swipe away.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        RestNotifier.notifySafely(
            this,
            RestNotifier.NOTIFICATION_DONE,
            RestEndHandler.buildDoneNotification(this, label, withStopAction = false),
        )
        stopSelf()
    }

    private fun silence() {
        handler.removeCallbacks(capRunnable)
        getSystemService(Vibrator::class.java)?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "trackmygymgains:restAlarm")
            .also { it.acquire(MAX_RING_MILLIS + 5_000L) }
    }

    override fun onDestroy() {
        silence()
        super.onDestroy()
    }

    companion object {
        /** Safety cap: ringing stops by itself after one minute. */
        const val MAX_RING_MILLIS = 60_000L
        private const val EXTRA_LABEL = "label"
        private val PATTERN = longArrayOf(0, 500, 300, 500, 300, 800, 1000)

        fun startIntent(context: Context, label: String?): Intent =
            Intent(context, RestAlarmService::class.java).putExtra(EXTRA_LABEL, label)

        fun stop(context: Context) {
            context.stopService(Intent(context, RestAlarmService::class.java))
        }
    }
}
