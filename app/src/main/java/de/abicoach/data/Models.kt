package de.abicoach.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Speaker { STUDENT, EXAMINER }

enum class SessionKind(val label: String) {
    SIMULATION("Prüfungssimulation"),
    TRAINING("Gesprächstraining"),
}

enum class FeedbackMode(val label: String, val description: String) {
    PRUEFUNG(
        "Prüfung (realistisch)",
        "Keine Korrekturen während des Sprechens – wie im echten Abi. Alles kommt in die Nachbesprechung.",
    ),
    FLUESTERN(
        "Flüstern",
        "Bei Fehlern vibriert das Handy und die Korrektur erscheint auf dem Bildschirm. Der Prüfer redet nicht dazwischen.",
    ),
    COACH(
        "Coach",
        "Bei wichtigen Fehlern korrigiert dich der Prüfer sofort und lässt dich den Satz wiederholen.",
    ),
}

data class Utterance(val speaker: Speaker, val text: String, val part: Int, val atMs: Long) {
    fun toJson(): JSONObject =
        JSONObject().put("speaker", speaker.name).put("text", text).put("part", part).put("atMs", atMs)

    companion object {
        fun fromJson(o: JSONObject) =
            Utterance(
                speaker = runCatching { Speaker.valueOf(o.optString("speaker")) }.getOrDefault(Speaker.STUDENT),
                text = o.optString("text"),
                part = o.optInt("part"),
                atMs = o.optLong("atMs"),
            )
    }
}

/** One language or content error, with the student's exact words. */
data class LiveError(
    val quote: String,
    val correction: String,
    val explanation: String,
    val category: String,
    val severity: String,
) {
    fun toJson(): JSONObject =
        JSONObject().put("quote", quote).put("correction", correction).put("explanation", explanation)
            .put("category", category).put("severity", severity)

    companion object {
        fun fromJson(o: JSONObject) =
            LiveError(
                quote = o.optString("quote"),
                correction = o.optString("correction"),
                explanation = o.optString("explanation"),
                category = o.optString("category", "OTHER"),
                severity = o.optString("severity", "minor"),
            )
    }
}

data class TaskItem(val operator: String, val afb: String, val instruction: String) {
    fun toJson(): JSONObject = JSONObject().put("operator", operator).put("afb", afb).put("instruction", instruction)

    companion object {
        fun fromJson(o: JSONObject) = TaskItem(o.optString("operator"), o.optString("afb"), o.optString("instruction"))
    }
}

/** Material and tasks for part 1 plus the planned topics for part 2. */
data class ExamTask(
    val topicArea: String,
    val title: String,
    val textType: String,
    val text: String,
    val sourceNote: String,
    val tasks: List<TaskItem>,
    val part2Topics: List<String>,
) {
    fun toJson(): JSONObject =
        JSONObject().put("topicArea", topicArea).put("title", title).put("textType", textType).put("text", text)
            .put("sourceNote", sourceNote).put("tasks", tasks.toJsonArray { it.toJson() })
            .put("part2Topics", part2Topics.toJsonArray())

    companion object {
        fun fromJson(o: JSONObject) =
            ExamTask(
                topicArea = o.optString("topicArea"),
                title = o.optString("title"),
                textType = o.optString("textType"),
                text = o.optString("text"),
                sourceNote = o.optString("sourceNote"),
                tasks = o.optJSONArray("tasks").objects().map { TaskItem.fromJson(it) },
                part2Topics = o.optJSONArray("part2Topics").strings(),
            )
    }
}

data class Upgrade(val original: String, val upgraded: String, val why: String)

data class PronTip(val word: String, val issue: String, val tip: String)

data class Phrase(val phrase: String, val meaningDe: String, val example: String)

/** The full feedback after a session. Points follow the NRW weighting: content 40 %, language 60 %. */
data class Debrief(
    val inhaltPoints: Int,
    val inhaltReason: String,
    val darstellungPoints: Int,
    val darstellungReason: String,
    val totalPoints: Int,
    val strengths: List<String>,
    val levers: List<String>,
    val errors: List<LiveError>,
    val upgrades: List<Upgrade>,
    val pronunciation: List<PronTip>,
    val contentFeedback: List<String>,
    val phrases: List<Phrase>,
    val droppedUnverified: Int,
    val audioAnalysed: Boolean,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("inhaltPoints", inhaltPoints).put("inhaltReason", inhaltReason)
            .put("darstellungPoints", darstellungPoints).put("darstellungReason", darstellungReason)
            .put("totalPoints", totalPoints)
            .put("strengths", strengths.toJsonArray()).put("levers", levers.toJsonArray())
            .put("errors", errors.toJsonArray { it.toJson() })
            .put("upgrades", upgrades.toJsonArray { JSONObject().put("original", it.original).put("upgraded", it.upgraded).put("why", it.why) })
            .put("pronunciation", pronunciation.toJsonArray { JSONObject().put("word", it.word).put("issue", it.issue).put("tip", it.tip) })
            .put("contentFeedback", contentFeedback.toJsonArray())
            .put("phrases", phrases.toJsonArray { JSONObject().put("phrase", it.phrase).put("meaningDe", it.meaningDe).put("example", it.example) })
            .put("droppedUnverified", droppedUnverified)
            .put("audioAnalysed", audioAnalysed)

    companion object {
        fun fromJson(o: JSONObject) =
            Debrief(
                inhaltPoints = o.optInt("inhaltPoints"),
                inhaltReason = o.optString("inhaltReason"),
                darstellungPoints = o.optInt("darstellungPoints"),
                darstellungReason = o.optString("darstellungReason"),
                totalPoints = o.optInt("totalPoints"),
                strengths = o.optJSONArray("strengths").strings(),
                levers = o.optJSONArray("levers").strings(),
                errors = o.optJSONArray("errors").objects().map { LiveError.fromJson(it) },
                upgrades = o.optJSONArray("upgrades").objects().map { Upgrade(it.optString("original"), it.optString("upgraded"), it.optString("why")) },
                pronunciation = o.optJSONArray("pronunciation").objects().map { PronTip(it.optString("word"), it.optString("issue"), it.optString("tip")) },
                contentFeedback = o.optJSONArray("contentFeedback").strings(),
                phrases = o.optJSONArray("phrases").objects().map { Phrase(it.optString("phrase"), it.optString("meaningDe"), it.optString("example")) },
                droppedUnverified = o.optInt("droppedUnverified"),
                audioAnalysed = o.optBoolean("audioAnalysed"),
            )
    }
}

data class SessionRecord(
    val id: String,
    val startedAt: Long,
    val kind: SessionKind,
    val feedback: FeedbackMode,
    val topicArea: String,
    val task: ExamTask?,
    val notes: String,
    val utterances: List<Utterance>,
    val liveErrors: List<LiveError>,
    val part1Sec: Int,
    val durationSec: Int,
    val debrief: Debrief?,
) {
    val studentText: String
        get() = utterances.filter { it.speaker == Speaker.STUDENT }.joinToString(" ") { it.text }

    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id).put("startedAt", startedAt).put("kind", kind.name).put("feedback", feedback.name)
            .put("topicArea", topicArea).put("task", task?.toJson() ?: JSONObject.NULL).put("notes", notes)
            .put("utterances", utterances.toJsonArray { it.toJson() })
            .put("liveErrors", liveErrors.toJsonArray { it.toJson() })
            .put("part1Sec", part1Sec).put("durationSec", durationSec)
            .put("debrief", debrief?.toJson() ?: JSONObject.NULL)

    companion object {
        fun create(kind: SessionKind, feedback: FeedbackMode, topicArea: String, task: ExamTask?) =
            SessionRecord(
                id = UUID.randomUUID().toString(),
                startedAt = System.currentTimeMillis(),
                kind = kind,
                feedback = feedback,
                topicArea = topicArea,
                task = task,
                notes = "",
                utterances = emptyList(),
                liveErrors = emptyList(),
                part1Sec = 0,
                durationSec = 0,
                debrief = null,
            )

        fun fromJson(o: JSONObject) =
            SessionRecord(
                id = o.optString("id"),
                startedAt = o.optLong("startedAt"),
                kind = runCatching { SessionKind.valueOf(o.optString("kind")) }.getOrDefault(SessionKind.TRAINING),
                feedback = runCatching { FeedbackMode.valueOf(o.optString("feedback")) }.getOrDefault(FeedbackMode.PRUEFUNG),
                topicArea = o.optString("topicArea"),
                task = o.optJSONObject("task")?.let { ExamTask.fromJson(it) },
                notes = o.optString("notes"),
                utterances = o.optJSONArray("utterances").objects().map { Utterance.fromJson(it) },
                liveErrors = o.optJSONArray("liveErrors").objects().map { LiveError.fromJson(it) },
                part1Sec = o.optInt("part1Sec"),
                durationSec = o.optInt("durationSec"),
                debrief = o.optJSONObject("debrief")?.let { Debrief.fromJson(it) },
            )
    }
}

internal fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}

internal fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

internal fun List<String>.toJsonArray(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

internal fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(mapper(it)) } }
