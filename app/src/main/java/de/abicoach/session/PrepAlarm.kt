package de.abicoach.session

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import de.abicoach.AbiCoachApp

/** Rings (as a notification) when the preparation time is over, even if the student is in another app or the phone is locked. */
object PrepAlarm {
    private const val REQUEST = 7001

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, PrepAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun schedule(context: Context, endsAt: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (endsAt <= System.currentTimeMillis()) return
        val intent = pending(context)
        try {
            if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent)
            }
        } catch (e: SecurityException) {
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent) }
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(pending(context)) }
    }
}

class PrepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AbiCoachApp
        // On screen, the timer of the preparation page already shows it.
        if (app != null && app.isVisible) return
        Notifications.timer(context, 4850, "Vorbereitungszeit vorbei", "Du kannst jetzt deinen Vortrag beginnen. Tippe, um zurückzukehren.")
    }
}
