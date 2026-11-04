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

/** The job is still running in the cloud when the worker's time is up: the worker looks again a little later. */
class CloudPending : Exception("Die Auswertung läuft noch in der Cloud.")

/**
 * The evaluation in Google's cloud. The phone uploads the recording and hands the grading (and the independent second
 * grading) to Gemini as background jobs – from then on nothing depends on the phone: the app may be closed, the phone
 * locked or offline. When the app (or its background worker) runs again, it picks up the result. Both gradings run at
 * the same time, and the grader listens to the recording himself, so this is also faster than the step-by-step way.
 */
object CloudAnalysis {
    private const val POLL_MS = 4_000L

    /** How long one run of the background worker waits before it hands over to the next run. */
    private const val WINDOW_MS = 8 * 60_000L

    /** After this the job is given up and the grading starts again. */
    private const val MAX_AGE_MS = 6 * 60 * 60_000L

    /** How long the result waits for a second grading that is not finished yet. */
    private const val SECOND_GRACE_MS = 120_000L

    suspend fun run(app: AbiCoachApp, record: SessionRecord, onProgress: (String) -> Unit): Debrief {
        val job = app.cloudJobs.get(record.id) ?: start(app, record, onProgress)
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
        var gradeDoneAt = 0L
        var second: GeminiRest.CloudState? = null
        val label = AiHub.pretty(job.gradeModel)
        while (true) {
            val now = System.currentTimeMillis()
            val grade = gemini.cloudState(job.gradeId)
            if (job.secondId.isNotBlank() && (second == null || second.running)) {
                second = runCatching { gemini.cloudState(job.secondId) }.getOrNull() ?: second
            }
            if (grade.done) {
                if (gradeDoneAt == 0L) gradeDoneAt = now
                val waitForSecond = second?.running == true && now - gradeDoneAt < SECOND_GRACE_MS
                if (!waitForSecond) return finish(app, record, job, grade, second)
                onProgress("Die erste Bewertung ist fertig – die zweite Prüferin braucht noch einen Moment …")
            } else if (!grade.running) {
                // Failed or cancelled in the cloud: the next try starts a new job.
                app.cloudJobs.remove(record.id)
                throw AiException("Die Bewertung in der Cloud hat nicht geklappt (${grade.status}${if (grade.error.isNotBlank()) ": ${grade.error}" else ""})", 503)
            } else {
                val minutes = (now - job.startedAt) / 60_000L
                val seconds = (now - job.startedAt) / 1000L % 60
                onProgress(
                    "$label bewertet deine Leistung in Googles Cloud" + (if (job.secondId.isNotBlank()) ", zusammen mit einer zweiten Prüferin" else "") +
                        " (seit $minutes:${"%02d".format(seconds)}) – du kannst die App ruhig schließen.",
                )
            }
            if (now - job.startedAt > MAX_AGE_MS) {
                app.cloudJobs.remove(record.id)
                throw AiException("Die Auswertung in der Cloud hat zu lange gedauert – sie wird neu gestartet.", 504)
            }
            if (now - began > WINDOW_MS) throw CloudPending()
            delay(POLL_MS)
        }
    }

    private suspend fun finish(app: AbiCoachApp, record: SessionRecord, job: CloudJob, grade: GeminiRest.CloudState, second: GeminiRest.CloudState?): Debrief {
        val label = AiHub.pretty(job.gradeModel)
        val json = GeminiRest.parseJsonLoose(grade.text)
        var debrief =
            Analyzer.keepOwnGoals(
                record,
                Prompts.parseDebrief(json, record.studentText, job.audio, null, label, if (job.audio) label else ""),
            )
        if (second != null && second.done) {
            runCatching { GeminiRest.parseJsonLoose(second.text) }.getOrNull()?.let { debrief = Analyzer.combine(debrief, it, AiHub.pretty(job.secondModel)) }
        } else if (job.secondId.isNotBlank()) {
            // Not ready in time: the result comes without the second grading.
            app.ai.gemini.cancelCloud(job.secondId)
        }
        app.cloudJobs.remove(record.id)
        return debrief
    }

    /** Stops the evaluation of a session in the cloud (the background worker is stopped separately). */
    suspend fun cancel(app: AbiCoachApp, sessionId: String) {
        val job = app.cloudJobs.get(sessionId) ?: return
        app.cloudJobs.remove(sessionId)
        app.ai.gemini.cancelCloud(job.gradeId)
        if (job.secondId.isNotBlank()) app.ai.gemini.cancelCloud(job.secondId)
    }
}
