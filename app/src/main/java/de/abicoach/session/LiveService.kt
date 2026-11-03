package de.abicoach.session

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import de.abicoach.MainActivity
import de.abicoach.R

/**
 * Keeps the app allowed to use the microphone while a live conversation runs: Android silences the microphone of an
 * app that is not visible (screen off, another app on top) unless it runs a foreground service of type "microphone".
 * The ongoing notification "AbiCoach hört zu" is that service.
 */
class LiveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.createChannels(this)
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply { this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification: Notification =
            NotificationCompat.Builder(this, Notifications.CH_WORK)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("AbiCoach hört zu")
                .setContentText("Deine Sprechübung läuft. Tippe, um zurückzukehren.")
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        // Always promote first: a service started with startForegroundService must call this, even when it is about to stop.
        runCatching {
            // The "microphone" service type exists from Android 11 (API 30) on.
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val NOTIFICATION_ID = 4802
        private const val ACTION_STOP = "de.abicoach.STOP_LIVE"
        private val handler = Handler(Looper.getMainLooper())

        @Volatile private var startedAt = 0L

        /** Counts the starts, so that a late "stop" of an earlier conversation never stops the next one. */
        @Volatile private var generation = 0

        /** Called when a live conversation starts, while the app is on screen. Failures are not fatal: the session still works. */
        fun start(context: Context) {
            startedAt = System.currentTimeMillis()
            generation++
            runCatching {
                val intent = Intent(context, LiveService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            }
        }

        /** Stops the service, but not before it had a moment to start (stopping at once would crash the app). */
        fun stop(context: Context) {
            val app = context.applicationContext
            val mine = generation
            val wait = (1_500 - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0)
            handler.postDelayed({ if (mine == generation) runCatching { app.stopService(Intent(app, LiveService::class.java)) } }, wait)
        }
    }
}
