package de.abicoach.path

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.abicoach.AbiCoachApp
import de.abicoach.MainActivity
import de.abicoach.R
import de.abicoach.ui.Planner
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** The daily reminder at the chosen hour (WorkManager, like in Mental Math Plus). */
object Reminder {
    private const val WORK = "daily_reminder"
    const val CHANNEL = "reminder"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, "Tägliche Erinnerung", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Erinnert dich an dein Abi-Training"
        manager.createNotificationChannel(channel)
    }

    fun schedule(context: Context, hour: Int) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes().coerceAtLeast(1)
        val request =
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The text of today's reminder: the next step on the Abi-Weg and the days left. */
    fun message(app: AbiCoachApp): String {
        val worlds = app.worlds()
        val progress = app.path.progress.value
        val profile = app.profiles.profile.value
        val due = PathEngine.due(worlds, progress).firstOrNull()
        val next = PathEngine.next(worlds, progress, profile)
        val step =
            when {
                due != null -> "Auffrischen: ${due.level.title}"
                next != null -> "Weiterklettern: ${next.level.title}"
                else -> Planner.today(app).firstOrNull()?.title ?: "Eine Übung nach Wahl"
            }
        val days = Planner.daysUntilExam(app)
        return step + (days?.let { " · noch $it Tage bis zum Abi" } ?: "")
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AbiCoachApp
        if (!app.settings.reminderOn) return Result.success()
        // Already practised enough today: no reminder.
        if (app.store.minutesToday() >= app.settings.dailyMinutes) return Result.success()
        if (!Reminder.canNotify(app)) return Result.success()
        val intent =
            Intent(app, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification =
            NotificationCompat.Builder(app, Reminder.CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("Zeit für dein Abi-Training")
                .setContentText(Reminder.message(app))
                .setStyle(NotificationCompat.BigTextStyle().bigText(Reminder.message(app)))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        runCatching { NotificationManagerCompat.from(app).notify(4711, notification) }
        return Result.success()
    }
}
