package de.abicoach.session

import android.content.Context
import de.abicoach.AbiCoachApp
import de.abicoach.ai.AiException
import de.abicoach.ai.AiHub
import de.abicoach.ai.GeminiRest
import de.abicoach.data.Debrief
import de.abicoach.data.SessionRecord
import de.abicoach.data.objects
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A grading that runs in Google's cloud: the ids of its background jobs, kept on the phone to pick up the result later. */
data class CloudJob(
    val sessionId: String,
    val gradeId: String,
    val gradeModel: String,
    val secondId: String = "",
    val secondModel: String = "",
    /** The recording went with the job: the grader listened to it himself. */
    val audio: Boolean,
    val startedAt: Long,
) {
    fun toJson(): JSONObject =
        JSONObject().put("sessionId", sessionId).put("gradeId", gradeId).put("gradeModel", gradeModel).put("secondId", secondId)
            .put("secondModel", secondModel).put("audio", audio).put("startedAt", startedAt)

    companion object {
        fun fromJson(o: JSONObject) =
            CloudJob(o.optString("sessionId"), o.optString("gradeId"), o.optString("gradeModel"), o.optString("secondId"), o.optString("secondModel"), o.optBoolean("audio"), o.optLong("startedAt"))
    }
}

/** The cloud jobs that are not picked up yet (one per session). */
class CloudJobStore(context: Context) {
    private val file = File(context.filesDir, "cloud_jobs.json")
    private val jobs = LinkedHashMap<String, CloudJob>()

    init {
        runCatching { JSONArray(file.readText()).objects().map { CloudJob.fromJson(it) }.filter { it.sessionId.isNotBlank() }.forEach { jobs[it.sessionId] = it } }
    }

    @Synchronized
    fun all(): List<CloudJob> = jobs.values.toList()

    @Synchronized
    fun get(sessionId: String): CloudJob? = jobs[sessionId]

    @Synchronized
    fun put(job: CloudJob) {
        jobs[job.sessionId] = job
        save()
    }

    @Synchronized
    fun remove(sessionId: String) {
        if (jobs.remove(sessionId) != null) save()
    }

    private fun save() {
        runCatching { file.writeText(JSONArray().also { a -> jobs.values.forEach { a.put(it.toJson()) } }.toString()) }
    }
}

/**
 * The evaluation in Google's cloud. The phone uploads the recording and hands the grading (and the independent second
 * grading) to Gemini as background jobs – from then on nothing depends on the phone: the app may be closed, the phone
 * locked or offline. When the app (or its background worker) runs again, it picks up the result. Both gradings run at
 * the same time, and the grader listens to the recording himself.
 *
 * It never loops: a job that failed, cannot be read or takes too long is stopped and the grading is done directly
 * (see [AnalysisRunner]); only a lost connection lets the worker try again later, with the job kept.
 */
object CloudAnalysis {
    private const val POLL_MS = 3_000L

    /** A grading normally takes one to three minutes; after this it is given up and graded directly instead. */
    private const val LIMIT_MS = 6 * 60_000L

    /** When the app comes back to an older job, it still looks this long before giving up. */
    private const val RESUME_GRACE_MS = 90_000L

    /** How long the result waits for a second grading that is not finished yet. */
    private const val SECOND_GRACE_MS = 60_000L

    /** Picks up the running job of the session or, if [allowStart], starts a new one. */
    suspend fun run(app: AbiCoachApp, record: SessionRecord, allowStart: Boolean, onProgress: (String) -> Unit): Debrief {
        val job = app.cloudJobs.get(record.id) ?: if (allowStart) start(app, record, onProgress) else throw AiException("Keine Cloud-Auswertung", AiHub.NOT_OFFERED)
        return await(app, record, job, onProgress)
    }

    private suspend fun start(app: AbiCoachApp, record: SessionRecord, onProgress: (String) -> Unit): CloudJob {
        val hub = app.ai
        onProgress("Sammle dein Kursmaterial zum Thema …")
        val courseContext = Analyzer.courseContext(app, record)
        val teacherNotes = app.course.teacherNotes()
        val audio = Analyzer.uploadAudio(app, record, onProgress)
        val intro =
            if (audio.isEmpty()) {
                "Es liegt keine Aufnahme vor: Aussprache und Flüssigkeit kannst du nur aus dem Transkript schätzen."
            } else {
                "Die Aufnahme des Prüflings liegt bei: Höre sie an und bewerte Aussprache, Intonation und Flüssigkeit daraus." +
                    if (record.studentText.isBlank()) " Es gibt kein Transkript – schreibe das Gesagte in student_transcript." else ""
            }
        val context = Prompts.analysisContext(record, courseContext, teacherNotes, null, Analyzer.profileNote(app), app.insights.knownTexts())
        onProgress("Gebe die Bewertung an Googles Cloud …")
        val grade = hub.startCloudBrain(Prompts.ANALYSIS_SYSTEM, listOf(GeminiRest.textPart("$intro\n\n$context")) + audio, Prompts.analysisSchema)
        var job = CloudJob(record.id, grade.id, grade.model, audio = audio.isNotEmpty(), startedAt = System.currentTimeMillis())
        app.cloudJobs.put(job)
        // The second examiner works at the same time, without the goals and the profile of the student.
        if (Analyzer.needsSecondOpinion(app, record)) {
            try {
                val secondContext = Prompts.analysisContext(record.copy(goals = emptyList()), courseContext, teacherNotes, null, "")
                val second = hub.startCloudBrain(Prompts.SECOND_SYSTEM, listOf(GeminiRest.textPart("$intro\n\n$secondContext")) + audio, Prompts.secondSchema)
                job = job.copy(secondId = second.id, secondModel = second.model)
                app.cloudJobs.put(job)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onProgress("Zweitbewertung übersprungen: ${e.message}")
            }
        }
        return job
    }

    private suspend fun await(app: AbiCoachApp, record: SessionRecord, job: CloudJob, onProgress: (String) -> Unit): Debrief {
        val gemini = app.ai.gemini
        val began = System.currentTimeMillis()
        val deadline = maxOf(job.startedAt + LIMIT_MS, began + RESUME_GRACE_MS)
        var gradeDoneAt = 0L
        var second: GeminiRest.CloudState? = null
        val label = AiHub.pretty(job.gradeModel)
        while (true) {
            val now = System.currentTimeMillis()
            val grade =
                try {
                    gemini.cloudState(job.gradeId)
                } catch (e: AiException) {
                    // The job is unknown or gone at Google: it will never come.
                    if (e.isPermanent) {
                        app.cloudJobs.remove(record.id)
                        throw CloudGaveUp("Google kennt den Auftrag nicht mehr")
                    }
                    throw e
                }
            if (job.secondId.isNotBlank() && (second == null || second.running)) {
                second = runCatching { gemini.cloudState(job.secondId) }.getOrNull() ?: second
            }
            when {
                grade.done -> {
                    if (gradeDoneAt == 0L) gradeDoneAt = now
                    val waitForSecond = second?.running == true && now - gradeDoneAt < SECOND_GRACE_MS
                    if (!waitForSecond) return finish(app, record, job, grade, second)
                    onProgress("Die erste Bewertung ist fertig – die zweite Prüferin braucht noch einen Moment …")
                }
                grade.ended -> {
                    cancel(app, record.id)
                    throw CloudGaveUp("Google meldet „${grade.status}“${if (grade.error.isNotBlank()) ": ${grade.error.take(120)}" else ""}")
                }
                else -> {
                    val minutes = (now - job.startedAt) / 60_000L
                    val seconds = (now - job.startedAt) / 1000L % 60
                    onProgress(
                        "$label bewertet deine Leistung in Googles Cloud" + (if (job.secondId.isNotBlank()) ", zusammen mit einer zweiten Prüferin" else "") +
                            " (seit $minutes:${"%02d".format(seconds)}) – du kannst die App ruhig schließen.",
                    )
                }
            }
            if (now > deadline && gradeDoneAt == 0L) {
                cancel(app, record.id)
                throw CloudGaveUp("die Cloud hat länger als ${LIMIT_MS / 60_000} Minuten gebraucht")
            }
            delay(POLL_MS)
        }
    }

    private suspend fun finish(app: AbiCoachApp, record: SessionRecord, job: CloudJob, grade: GeminiRest.CloudState, second: GeminiRest.CloudState?): Debrief {
        val label = AiHub.pretty(job.gradeModel)
        val json =
            try {
                GeminiRest.parseJsonLoose(grade.text)
            } catch (e: AiException) {
                // A broken answer stays broken: never read it again, grade directly.
                cancel(app, record.id)
                throw CloudGaveUp("die Antwort aus der Cloud war unvollständig")
            }
        var debrief =
            Analyzer.keepOwnGoals(
                record,
                Prompts.parseDebrief(json, record.studentText, job.audio, null, label, if (job.audio) label else ""),
            )
        if (second != null && second.done) {
            runCatching { GeminiRest.parseJsonLoose(second.text) }.getOrNull()?.let { debrief = Analyzer.combine(debrief, it, AiHub.pretty(job.secondModel)) }
        } else if (job.secondId.isNotBlank()) {
            // Not ready in time: the result comes without the second grading.
            runCatching { app.ai.gemini.cancelCloud(job.secondId) }
        }
        app.cloudJobs.remove(record.id)
        return debrief
    }

    /** Stops the evaluation of a session in the cloud (the background worker is stopped separately). */
    suspend fun cancel(app: AbiCoachApp, sessionId: String) {
        val job = app.cloudJobs.get(sessionId) ?: return
        app.cloudJobs.remove(sessionId)
        runCatching { app.ai.gemini.cancelCloud(job.gradeId) }
        if (job.secondId.isNotBlank()) runCatching { app.ai.gemini.cancelCloud(job.secondId) }
    }
}

/** The cloud cannot deliver this grading (failed, unreadable, too slow): it is graded directly instead. */
class CloudGaveUp(reason: String) : Exception(reason)
