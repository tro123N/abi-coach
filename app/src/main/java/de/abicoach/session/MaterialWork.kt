package de.abicoach.session

import android.content.Context
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
import de.abicoach.ai.AiException
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionKind
import de.abicoach.ui.SessionSetup
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

object MaterialScheduler {
    fun workName(key: String) = "material-$key"

    fun enqueue(context: Context, key: String, request: MaterialGenerator.Request, fixedDocId: String?, fixedPage: Int, prefetch: Boolean) {
        val data =
            Data.Builder()
                .putString(MaterialWorker.KEY, key)
                .putInt(MaterialWorker.TOPIC, request.topicId)
                .putString(MaterialWorker.TEXT_TYPE, request.textType.name)
                .putString(MaterialWorker.SOURCE, request.source.name)
                .putBoolean(MaterialWorker.SHORT, request.short)
                .putString(MaterialWorker.INSTRUCTION, request.instruction)
                .putString(MaterialWorker.DOC, fixedDocId.orEmpty())
                .putInt(MaterialWorker.PAGE, fixedPage)
                .putBoolean(MaterialWorker.PREFETCH, prefetch)
                .putString(MaterialWorker.UNIT, request.unitId)
                .build()
        val builder =
            OneTimeWorkRequestBuilder<MaterialWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
        // The text the student waits for runs at once; a text prepared in advance may wait for a quiet moment.
        if (!prefetch) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        WorkManager.getInstance(context).enqueueUniqueWork(workName(key), ExistingWorkPolicy.REPLACE, builder.build())
    }

    fun cancel(context: Context, key: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(key))
    }
}

/**
 * Writes the exam text and the three tasks in the background: it keeps running when the app is closed or the phone
 * is locked, and tells the student when the material is ready. A text "prepared in advance" works silently.
 */
class MaterialWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo =
        Notifications.foreground(applicationContext, "AbiCoach schreibt deinen Prüfungstext", "Die KI erstellt Text und drei Aufgaben …", id = 4803)

    override suspend fun doWork(): Result {
        val app = applicationContext as AbiCoachApp
        val key = inputData.getString(KEY) ?: return Result.failure()
        val prefetch = inputData.getBoolean(PREFETCH, false)
        val request =
            MaterialGenerator.Request(
                topicId = inputData.getInt(TOPIC, -1),
                textType = runCatching { Knowledge.TextType.valueOf(inputData.getString(TEXT_TYPE).orEmpty()) }.getOrDefault(Knowledge.TextType.GEMISCHT),
                source = runCatching { Knowledge.Source.valueOf(inputData.getString(SOURCE).orEmpty()) }.getOrDefault(Knowledge.Source.COURSE),
                short = inputData.getBoolean(SHORT, false),
                instruction = inputData.getString(INSTRUCTION).orEmpty(),
                unitId = inputData.getString(UNIT).orEmpty(),
            )
        val docId = inputData.getString(DOC)?.takeIf { it.isNotBlank() }
        val page = inputData.getInt(PAGE, 0)
        if (!prefetch) {
            runCatching { setForeground(getForegroundInfo()) }
            app.material.begin(key)
        }
        return try {
            val (task, note) =
                MaterialGenerator.generate(app, request, fixedDocId = docId, fixedPage = page) { text -> if (!prefetch) app.material.progress(key, text) }
            val finalNote = note ?: if (prefetch) null else app.ai.lastNotice.also { app.ai.lastNotice = null }
            app.shelf.put(key, task, finalNote)
            if (!prefetch) {
                app.material.done(key)
                if (!app.isVisible) {
                    Notifications.done(app, 4851, "Dein Prüfungstext ist fertig", "Tippe, um mit der Vorbereitung zu beginnen – die Zeit läuft erst dann.")
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val temporary = e is IOException || (e as? AiException)?.isTransient == true || e is IllegalStateException
            if (temporary && runAttemptCount < 3) {
                if (!prefetch) app.material.progress(key, "Das hat nicht gleich geklappt – neuer Versuch …")
                Result.retry()
            } else {
                if (!prefetch) {
                    app.material.fail(key, "Material konnte nicht erstellt werden: ${e.message}")
                    if (!app.isVisible) Notifications.done(app, 4851, "Prüfungstext nicht erstellt", "Starte die Übung noch einmal. ${e.message.orEmpty()}".take(180))
                }
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY = "key"
        const val TOPIC = "topic"
        const val TEXT_TYPE = "text_type"
        const val SOURCE = "source"
        const val SHORT = "short"
        const val INSTRUCTION = "instruction"
        const val DOC = "doc"
        const val PAGE = "page"
        const val PREFETCH = "prefetch"
        const val UNIT = "unit"
    }
}

/** Prepares one ordinary exam text in advance, so that the next practice starts without waiting. */
object MaterialPrefetch {
    private const val MIN_GAP_MS = 20 * 60_000L

    @Volatile private var lastStartedAt = 0L

    /** Starts the silent preparation of a text, if the student wants it, nothing is ready yet and the last try is a while ago. */
    fun maybeStart(app: AbiCoachApp) {
        if (!app.settings.prefetchMaterial || !app.ai.hasGemini) return
        // New students do the placement test first (short text); a text in advance would only cost money.
        if (!app.profiles.profile.value.placementDone) return
        if (app.shelf.has(MaterialShelf.PREFETCH)) return
        val now = System.currentTimeMillis()
        if (now - lastStartedAt < MIN_GAP_MS) return
        lastStartedAt = now
        val topic = de.abicoach.learn.LearningBrain.pickTopic(app)
        val source = if (app.course.hasMaterial()) Knowledge.Source.COURSE else Knowledge.Source.NEW
        MaterialScheduler.enqueue(app, MaterialShelf.PREFETCH, MaterialGenerator.Request(topic, Knowledge.TextType.GEMISCHT, source), null, 0, prefetch = true)
    }

    /** The prepared text if it fits an ordinary practice without special wishes; it is removed from the shelf. */
    fun takeFor(app: AbiCoachApp, s: SessionSetup): MaterialShelf.Entry? {
        val ordinary =
            (s.kind == SessionKind.PART1 || s.kind == SessionKind.SIMULATION) &&
                s.fixedDocId == null && s.instruction.isBlank() &&
                s.textType == Knowledge.TextType.GEMISCHT && s.source != Knowledge.Source.MATERIAL && s.source != Knowledge.Source.REAL
        if (!ordinary) return null
        val entry = app.shelf.peek(MaterialShelf.PREFETCH) ?: return null
        val fits = s.topicChoice == Knowledge.FROM_COURSE || s.topicChoice == Knowledge.RANDOM || s.topicChoice == entry.task.topicId
        if (!fits) return null
        return app.shelf.take(MaterialShelf.PREFETCH)
    }
}
