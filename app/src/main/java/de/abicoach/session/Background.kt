package de.abicoach.session

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.abicoach.AbiCoachApp
import de.abicoach.MainActivity
import de.abicoach.R
import de.abicoach.ai.AiException
import de.abicoach.data.Debrief
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Notifications for work that keeps going when the app is closed. */
object Notifications {
    const val CH_WORK = "work"
    const val CH_DONE = "done"
    const val CH_TIMER = "timer"
    const val EXTRA_DEBRIEF = "open_debrief"
    private const val FOREGROUND_ID = 4801

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CH_WORK, "Läuft im Hintergrund", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Zeigt, dass AbiCoach gerade auswertet oder Material liest"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_DONE, "Ergebnisse", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Meldet, wenn eine Auswertung, ein Prüfungstext oder das Einlesen fertig ist"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_TIMER, "Vorbereitungszeit", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Meldet, wenn die Vorbereitungszeit vor dem Vortrag um ist"
            },
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(context: Context, debriefId: String?): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (debriefId != null) putExtra(EXTRA_DEBRIEF, debriefId)
            }
        return PendingIntent.getActivity(context, debriefId?.hashCode() ?: 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The ongoing notification that keeps the work alive while the app is in the background. */
    fun foreground(context: Context, title: String, text: String, progress: Boolean = true, id: Int = FOREGROUND_ID): ForegroundInfo {
        val notification =
            NotificationCompat.Builder(context, CH_WORK)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open(context, null))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, progress)
                .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    /** A finished-result notification; tapping it opens the debrief (or just the app). */
    fun done(context: Context, key: Int, title: String, text: String, debriefId: String? = null) {
        if (!canNotify(context)) return
        val notification =
            NotificationCompat.Builder(context, CH_DONE)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open(context, debriefId))
                .setAutoCancel(true)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(key, notification) }
    }

    /** A loud reminder (the preparation time is over). Tapping it brings the app back. */
    fun timer(context: Context, key: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val notification =
            NotificationCompat.Builder(context, CH_TIMER)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open(context, null))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(key, notification) }
    }
}

/** What the evaluation is doing right now; the app reads this to show progress and to jump to the result. */
data class AnalysisStatus(
    val id: String? = null,
    val text: String = "",
    val error: String? = null,
    /** Set when an evaluation has just finished. */
    val finishedId: String? = null,
    val points: Int = 0,
)

class AnalysisCenter {
    private val _status = MutableStateFlow(AnalysisStatus())
    val status: StateFlow<AnalysisStatus> = _status.asStateFlow()

    fun begin(id: String) {
        _status.value = AnalysisStatus(id = id, text = "Bereite die Auswertung vor …")
    }

    fun progress(id: String, text: String) {
        if (_status.value.id == id) _status.value = _status.value.copy(text = text, error = null)
    }

    fun done(id: String, points: Int) {
        _status.value = AnalysisStatus(finishedId = id, points = points)
    }

    fun fail(id: String, message: String) {
        _status.value = AnalysisStatus(id = id, error = message)
    }

    fun consumeFinished() {
        if (_status.value.finishedId != null) _status.value = AnalysisStatus()
    }

    /** The student stopped the evaluation. */
    fun cancel(id: String) {
        if (_status.value.id == id || _status.value.id == null) _status.value = AnalysisStatus()
    }
}

/** Evaluates a session and folds the result into the profile, the Abi-Weg and the vocabulary. */
object AnalysisRunner {
    /** [newCloudJob] false (a retry of the background worker): no new cloud job – a running one is still picked up. */
    suspend fun run(app: AbiCoachApp, record: SessionRecord, newCloudJob: Boolean = true, onProgress: (String) -> Unit): Debrief {
        val startInCloud = app.ai.canRunInCloud && app.settings.cloudAnalysis && newCloudJob
        // A job that already runs at Google is picked up even after switching to another company.
        val running = app.cloudJobs.get(record.id) != null && app.settings.geminiKey.isNotBlank()
        val graded =
            if (startInCloud || running) {
                try {
                    CloudAnalysis.run(app, record, allowStart = startInCloud, onProgress = onProgress)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Only a lost connection waits (the job goes on in the cloud). Anything else: grade directly, now.
                    if (e.isConnectionProblem()) throw e
                    CloudAnalysis.cancel(app, record.id)
                    onProgress("Die Cloud-Auswertung hat nicht geklappt – werte direkt aus …")
                    app.pendingNotice = "Die Auswertung lief diesmal direkt statt in Googles Cloud (${e.message?.take(120)})."
                    Analyzer(app).analyze(record, onProgress)
                }
            } else {
                if (app.cloudJobs.get(record.id) != null) CloudAnalysis.cancel(app, record.id)
                Analyzer(app).analyze(record, onProgress)
            }
        // The comparison with earlier sessions is made afterwards: the grader itself never sees earlier points.
        val debrief = graded.copy(progress = runCatching { ProgressMaker.make(app, record, graded) }.getOrNull())
        app.store.save(record.copy(debrief = debrief))
        app.vocab.addAll(de.abicoach.practice.PracticeFeed.fromDebrief(debrief, record.topicId))
        // A second attempt at the same text counts less for the profile, so it does not inflate the forecast.
        // Two independent gradings that agree make the rating more reliable; a clear disagreement makes it count less.
        val reliability = debrief.secondOpinion?.let { if (it.agrees) 1.15 else 0.8 } ?: 1.0
        val weight = (record.kind.profileWeight * (if (record.retryOf.isNotBlank()) 0.4 else 1.0) * reliability).coerceAtMost(1.0)
        val now = System.currentTimeMillis()
        // Content knowledge counts for the topic field of the session (and, in a simulation, for the part-2 topics).
        val topicPoints = debrief.skills[de.abicoach.profile.Skill.SACHWISSEN] ?: debrief.inhaltPoints
        app.profiles.update { p ->
            var rated = p.withRatings(debrief.skills, weight, now, record.kind.short)
            rated = rated.withTopic(record.topicId, topicPoints, weight, now)
            if (record.kind.hasPart2) record.task?.part2?.forEach { t -> rated = rated.withTopic(t.topicId, topicPoints, weight * 0.4, now) }
            // A conversation over several topic fields (chosen in the setup) counts for each of them.
            if (record.task == null) record.extraTopics.forEach { t -> rated = rated.withTopic(t, topicPoints, weight * 0.6, now) }
            if (record.kind == SessionKind.PLACEMENT) rated.copy(placementDone = true, placementAt = now) else rated
        }
        // The error memory: every mistake of this session, grouped by its rule, followed over time.
        val words = record.studentText.split(Regex("\\s+")).count { it.isNotBlank() }
        val mistakes = (debrief.errors + record.liveErrors).distinctBy { Prompts.normalize(it.quote) + "|" + Prompts.normalize(it.correction) }
        runCatching { app.errorMemory.ingest(mistakes, record.id, de.abicoach.learn.ErrorMemoryStore.SOURCE_SESSION, record.startedAt, words) }
        // The learner memory: a habit the student worked on (session goal) and what the grader noticed this time.
        runCatching {
            record.goals.filter { it.kind == "habit" && it.target.isNotBlank() }.forEach { g ->
                debrief.goalResults.firstOrNull { it.id == g.id }?.let { r -> app.insights.resolve(g.target, r.result, now) }
            }
            app.insights.add(debrief.observations, now)
        }
        // The student's good ideas on this topic go into its dossier the next time it is renewed.
        if (record.topicId in 0..13 && app.dossiers.get(record.topicId) == null) de.abicoach.memory.DossierScheduler.kick(app)
        if (record.levelId.isNotBlank()) {
            de.abicoach.path.Curriculum.level(record.levelId)?.let { level ->
                val points = de.abicoach.path.PathEngine.pointsFor(level, debrief)
                app.path.update { de.abicoach.path.PathEngine.applyResult(it, level, points) }
            }
        }
        if (record.kind == SessionKind.PLACEMENT) {
            val profile = app.profiles.profile.value
            app.path.update { de.abicoach.path.PathEngine.applyPlacement(it, app.worlds(), profile) }
        }
        return debrief
    }
}

object AnalysisScheduler {
    fun enqueue(context: Context, id: String) {
        val request =
            OneTimeWorkRequestBuilder<AnalysisWorker>()
                .setInputData(Data.Builder().putString(AnalysisWorker.KEY_ID, id).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork("analyze-$id", ExistingWorkPolicy.REPLACE, request)
    }

    /** Picks up an evaluation that is still running in the cloud (a worker that already runs is kept). */
    fun resume(context: Context, id: String) {
        val request =
            OneTimeWorkRequestBuilder<AnalysisWorker>()
                .setInputData(Data.Builder().putString(AnalysisWorker.KEY_ID, id).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork("analyze-$id", ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("analyze-$id")
    }
}

/**
 * Evaluates a session in the background: keeps running when the app is closed or the phone is locked,
 * shows an ongoing notification and tells the student when the result is ready.
 */
class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo =
        Notifications.foreground(
            applicationContext,
            "AbiCoach wertet deine Sitzung aus",
            if ((applicationContext as AbiCoachApp).let { it.ai.canRunInCloud && it.settings.cloudAnalysis }) {
                "Die Bewertung läuft in Googles Cloud – du kannst die App schließen, das Ergebnis kommt trotzdem."
            } else {
                "Die KI hört zu und bewertet nach dem NRW-Raster …"
            },
        )

    override suspend fun doWork(): Result {
        val app = applicationContext as AbiCoachApp
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val record = app.store.get(id) ?: return Result.failure()
        if (record.debrief != null) {
            app.analysis.done(id, record.debrief.totalPoints)
            return Result.success()
        }
        runCatching { setForeground(getForegroundInfo()) }
        app.analysis.begin(id)
        return try {
            // A retry never starts another cloud job: it grades directly (or picks up the job that is still running).
            val debrief = AnalysisRunner.run(app, record, newCloudJob = runAttemptCount == 0) { text -> app.analysis.progress(id, text) }
            app.pendingNotice = app.ai.lastNotice.also { app.ai.lastNotice = null }
            app.analysis.done(id, debrief.totalPoints)
            if (!app.isVisible) {
                Notifications.done(
                    app,
                    4900 + (id.hashCode() and 0x3FF),
                    "Auswertung fertig: ${debrief.totalPoints} Punkte",
                    "${record.kind.label} – tippe, um Fehler, 15-Punkte-Sätze und deine nächsten Schritte zu sehen.",
                    id,
                )
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A few more tries for a lost connection or a busy server – then it stops with a clear message, never endless.
            val temporary = e.isConnectionProblem() || (e as? AiException)?.isTransient == true
            if (temporary && runAttemptCount < MAX_RETRIES) {
                app.analysis.progress(id, "Die Verbindung wackelt – neuer Versuch gleich (${runAttemptCount + 1} von $MAX_RETRIES) …")
                Result.retry()
            } else {
                val message = "Auswertung fehlgeschlagen: ${e.message}"
                app.analysis.fail(id, message)
                if (!app.isVisible) Notifications.done(app, 4900 + (id.hashCode() and 0x3FF), "Auswertung nicht geklappt", "Tippe, um es erneut zu versuchen. ${e.message.orEmpty()}".take(200), id)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_ID = "id"
        private const val MAX_RETRIES = 3
    }
}

/** No connection (network down, timeout): waiting helps. Everything else does not get better by trying again. */
internal fun Throwable.isConnectionProblem(): Boolean = this is IOException || (this as? AiException)?.isNetwork == true
