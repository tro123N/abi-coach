package de.abicoach.data

import de.abicoach.profile.Skill
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Speaker { STUDENT, EXAMINER }

enum class SessionKind(val label: String, val short: String) {
    SIMULATION("Prüfungssimulation", "Simulation"),
    PLACEMENT("Einstufung", "Einstufung"),
    PART1("Vortrag (Teil 1)", "Vortrag"),
    PART2("Prüfungsgespräch (Teil 2)", "Gespräch"),
    TRAINING("Gesprächstraining", "Training"),
    ;

    /** How much a session of this kind counts for the learner profile. */
    val profileWeight: Double
        get() =
            when (this) {
                SIMULATION, PLACEMENT -> 1.0
                PART1, PART2 -> 0.7
                TRAINING -> 0.45
            }

    val hasPart1: Boolean get() = this == SIMULATION || this == PLACEMENT || this == PART1
    val hasPart2: Boolean get() = this == SIMULATION || this == PLACEMENT || this == PART2
}

enum class FeedbackMode(val label: String, val description: String) {
    PRUEFUNG(
        "Prüfung",
        "Keine Korrekturen während des Sprechens – wie im echten Abi. Alles kommt in die Nachbesprechung.",
    ),
    FLUESTERN(
        "Flüstern",
        "Bei Fehlern vibriert das Handy und die Korrektur erscheint auf dem Bildschirm. Der Prüfer redet nicht dazwischen.",
    ),
    COACH(
        "Coach",
        "Bei wichtigen Fehlern unterbricht dich der Prüfer sofort, korrigiert dich und lässt dich den Satz wiederholen.",
    ),
}

data class Utterance(val speaker: Speaker, val text: String, val part: Int, val atMs: Long) {
    fun toJson(): JSONObject = JSONObject().put("speaker", speaker.name).put("text", text).put("part", part).put("atMs", atMs)

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

/** One task of part 1 with the points an excellent answer would contain (Erwartungshorizont). */
data class TaskItem(val operator: String, val afb: String, val instruction: String, val expectations: List<String> = emptyList()) {
    fun toJson(): JSONObject =
        JSONObject().put("operator", operator).put("afb", afb).put("instruction", instruction).put("expectations", expectations.toJsonArray())

    companion object {
        fun fromJson(o: JSONObject) = TaskItem(o.optString("operator"), o.optString("afb"), o.optString("instruction"), o.optJSONArray("expectations").strings())
    }
}

/** One planned topic for the exam conversation, with what the course covered on it. */
data class Part2Topic(val topicId: Int, val question: String, val courseFacts: List<String> = emptyList()) {
    fun toJson(): JSONObject = JSONObject().put("topicId", topicId).put("question", question).put("courseFacts", courseFacts.toJsonArray())

    companion object {
        fun fromJson(o: JSONObject) = Part2Topic(o.optInt("topicId", -1), o.optString("question"), o.optJSONArray("courseFacts").strings())
    }
}

/** Material and tasks for part 1 plus the plan for part 2. */
data class ExamTask(
    val topicId: Int,
    val title: String,
    val textType: String,
    val text: String,
    val sourceNote: String,
    val tasks: List<TaskItem>,
    val part2: List<Part2Topic>,
    /** Where the text came from: "course", "material" or "new". */
    val source: String = "new",
    /** Short facts from the student's own course that fit this text (for the debrief). */
    val courseLinks: List<String> = emptyList(),
) {
    val wordCount: Int get() = text.split(Regex("\\s+")).count { it.isNotBlank() }

    fun toJson(): JSONObject =
        JSONObject().put("topicId", topicId).put("title", title).put("textType", textType).put("text", text)
            .put("sourceNote", sourceNote).put("tasks", tasks.toJsonArray { it.toJson() })
            .put("part2", part2.toJsonArray { it.toJson() }).put("source", source).put("courseLinks", courseLinks.toJsonArray())

    companion object {
        fun fromJson(o: JSONObject) =
            ExamTask(
                topicId = o.optInt("topicId", -1),
                title = o.optString("title"),
                textType = o.optString("textType"),
                text = o.optString("text"),
                sourceNote = o.optString("sourceNote"),
                tasks = o.optJSONArray("tasks").objects().map { TaskItem.fromJson(it) },
                part2 = o.optJSONArray("part2").objects().map { Part2Topic.fromJson(it) },
                source = o.optString("source", "new"),
                courseLinks = o.optJSONArray("courseLinks").strings(),
            )
    }
}

data class Upgrade(val original: String, val upgraded: String, val why: String)

data class PronTip(val word: String, val issue: String, val tip: String)

data class Phrase(val phrase: String, val meaningDe: String, val example: String)

/** How well one task of part 1 was answered. */
data class TaskReview(val number: Int, val points: Int, val covered: List<String>, val missing: List<String>, val tip: String) {
    fun toJson(): JSONObject =
        JSONObject().put("number", number).put("points", points).put("covered", covered.toJsonArray()).put("missing", missing.toJsonArray()).put("tip", tip)

    companion object {
        fun fromJson(o: JSONObject) = TaskReview(o.optInt("number"), o.optInt("points"), o.optJSONArray("covered").strings(), o.optJSONArray("missing").strings(), o.optString("tip"))
    }
}

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
    val skills: Map<Skill, Int> = emptyMap(),
    val taskReviews: List<TaskReview> = emptyList(),
    /** What from the student's own course would have fitted. */
    val courseLinks: List<String> = emptyList(),
    /** Which AI graded (e.g. "Claude Opus 5.5") and which one listened to the recording. */
    val gradedBy: String = "",
    val listenedBy: String = "",
) {
    val stars: Int get() = when {
        totalPoints >= 13 -> 3
        totalPoints >= 10 -> 2
        totalPoints >= 7 -> 1
        else -> 0
    }

    fun toJson(): JSONObject {
        val s = JSONObject()
        skills.forEach { (k, v) -> s.put(k.key, v) }
        return JSONObject()
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
            .put("skills", s)
            .put("taskReviews", taskReviews.toJsonArray { it.toJson() })
            .put("courseLinks", courseLinks.toJsonArray())
            .put("gradedBy", gradedBy)
            .put("listenedBy", listenedBy)
    }

    companion object {
        fun fromJson(o: JSONObject): Debrief {
            val skills = mutableMapOf<Skill, Int>()
            o.optJSONObject("skills")?.let { s -> s.keys().forEach { k -> Skill.byKey(k)?.let { skills[it] = s.optInt(k) } } }
            return Debrief(
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
                skills = skills,
                taskReviews = o.optJSONArray("taskReviews").objects().map { TaskReview.fromJson(it) },
                courseLinks = o.optJSONArray("courseLinks").strings(),
                gradedBy = o.optString("gradedBy"),
                listenedBy = o.optString("listenedBy"),
            )
        }
    }
}

data class SessionRecord(
    val id: String,
    val startedAt: Long,
    val kind: SessionKind,
    val feedback: FeedbackMode,
    val topicId: Int,
    val task: ExamTask?,
    val notes: String,
    val utterances: List<Utterance>,
    val liveErrors: List<LiveError>,
    val part1Sec: Int,
    val durationSec: Int,
    val debrief: Debrief?,
    /** 0 = all three tasks, 1–3 = only this task of part 1. */
    val focusTask: Int = 0,
    /** Error categories the training focused on. */
    val focusCategories: List<String> = emptyList(),
    val examiner: String = "",
    /** Billed seconds of the live examiner session (GPT-Live). */
    val examinerSeconds: Double = 0.0,
) {
    val studentText: String
        get() = utterances.filter { it.speaker == Speaker.STUDENT }.joinToString(" ") { it.text }

    val title: String
        get() =
            when {
                kind == SessionKind.PART1 && focusTask in 1..3 -> "Aufgabe $focusTask: " + (Knowledge.TASK_PATTERNS.getOrNull(focusTask - 1)?.germanName ?: "")
                else -> kind.short
            }

    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id).put("startedAt", startedAt).put("kind", kind.name).put("feedback", feedback.name)
            .put("topicId", topicId).put("task", task?.toJson() ?: JSONObject.NULL).put("notes", notes)
            .put("utterances", utterances.toJsonArray { it.toJson() })
            .put("liveErrors", liveErrors.toJsonArray { it.toJson() })
            .put("part1Sec", part1Sec).put("durationSec", durationSec)
            .put("debrief", debrief?.toJson() ?: JSONObject.NULL)
            .put("focusTask", focusTask)
            .put("focusCategories", focusCategories.toJsonArray())
            .put("examiner", examiner)
            .put("examinerSeconds", examinerSeconds)

    companion object {
        fun create(kind: SessionKind, feedback: FeedbackMode, topicId: Int, task: ExamTask?, focusTask: Int = 0, focusCategories: List<String> = emptyList()) =
            SessionRecord(
                id = UUID.randomUUID().toString(),
                startedAt = System.currentTimeMillis(),
                kind = kind,
                feedback = feedback,
                topicId = topicId,
                task = task,
                notes = "",
                utterances = emptyList(),
                liveErrors = emptyList(),
                part1Sec = 0,
                durationSec = 0,
                debrief = null,
                focusTask = focusTask,
                focusCategories = focusCategories,
            )

        fun fromJson(o: JSONObject) =
            SessionRecord(
                id = o.optString("id"),
                startedAt = o.optLong("startedAt"),
                kind = runCatching { SessionKind.valueOf(o.optString("kind")) }.getOrDefault(SessionKind.TRAINING),
                feedback = runCatching { FeedbackMode.valueOf(o.optString("feedback")) }.getOrDefault(FeedbackMode.PRUEFUNG),
                topicId = o.optInt("topicId", -1),
                task = o.optJSONObject("task")?.let { ExamTask.fromJson(it) },
                notes = o.optString("notes"),
                utterances = o.optJSONArray("utterances").objects().map { Utterance.fromJson(it) },
                liveErrors = o.optJSONArray("liveErrors").objects().map { LiveError.fromJson(it) },
                part1Sec = o.optInt("part1Sec"),
                durationSec = o.optInt("durationSec"),
                debrief = o.optJSONObject("debrief")?.let { Debrief.fromJson(it) },
                focusTask = o.optInt("focusTask"),
                focusCategories = o.optJSONArray("focusCategories").strings(),
                examiner = o.optString("examiner"),
                examinerSeconds = o.optDouble("examinerSeconds", 0.0),
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

internal fun JSONArray?.ints(): List<Int> {
    if (this == null) return emptyList()
    return (0 until length()).map { optInt(it, Int.MIN_VALUE) }.filter { it != Int.MIN_VALUE }
}

internal fun List<String>.toJsonArray(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

internal fun List<Int>.toIntJsonArray(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

internal fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray = JSONArray().also { array -> forEach { array.put(mapper(it)) } }
