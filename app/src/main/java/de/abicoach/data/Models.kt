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
        "Bei Fehlern vibriert das Handy und die Korrektur erscheint groß auf dem Bildschirm (mit Kopfhörern auch leise hörbar). Der Prüfer redet nicht dazwischen.",
    ),
    COACH(
        "Coach",
        "Der Prüfer ist dein Coach: Im Gespräch unterbricht er dich bei wichtigen Fehlern mit seiner eigenen Stimme, lässt dich den Satz richtig wiederholen und sagt dir dann, dass du weitermachen sollst. Im Vortrag korrigiert er dich kurz – danach übst du die wichtigsten Fehler in einer Korrekturrunde.",
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
    /**
     * The rule behind the mistake in a few English words ("third person -s", "since vs for"). The same wording for
     * the same kind of mistake lets the error memory group them across sessions.
     */
    val rule: String = "",
) {
    fun toJson(): JSONObject =
        JSONObject().put("quote", quote).put("correction", correction).put("explanation", explanation)
            .put("category", category).put("severity", severity).put("rule", rule)

    companion object {
        fun fromJson(o: JSONObject) =
            LiveError(
                quote = o.optString("quote"),
                correction = o.optString("correction"),
                explanation = o.optString("explanation"),
                category = o.optString("category", "OTHER"),
                severity = o.optString("severity", "minor"),
                rule = o.optString("rule"),
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
    /** Id of the original exam task from the student's uploaded Abitur material ("real"), empty otherwise. */
    val unitId: String = "",
    /** Key points of the original model solution (Lösungsvorschlag), if the material had one. */
    val solution: String = "",
    /** How the learning brain shaped this material for the student (German, for the preparation page). */
    val adapted: String = "",
) {
    val wordCount: Int get() = text.split(Regex("\\s+")).count { it.isNotBlank() }

    /** An original task from real Abitur material, not one the AI wrote. */
    val isReal: Boolean get() = unitId.isNotBlank()

    fun toJson(): JSONObject =
        JSONObject().put("topicId", topicId).put("title", title).put("textType", textType).put("text", text)
            .put("sourceNote", sourceNote).put("tasks", tasks.toJsonArray { it.toJson() })
            .put("part2", part2.toJsonArray { it.toJson() }).put("source", source).put("courseLinks", courseLinks.toJsonArray())
            .put("unitId", unitId).put("solution", solution).put("adapted", adapted)

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
                unitId = o.optString("unitId"),
                solution = o.optString("solution"),
                adapted = o.optString("adapted"),
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

/**
 * A concrete goal for one session, set by the learning brain from the student's profile (an error pattern to avoid,
 * the weakest criterion of the task, a habit to change). [check] tells the grader in English how to judge it.
 */
data class SessionGoal(val id: String, val text: String, val check: String, val kind: String, val target: String = "") {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("text", text).put("check", check).put("kind", kind).put("target", target)

    companion object {
        fun fromJson(o: JSONObject) = SessionGoal(o.optString("id"), o.optString("text"), o.optString("check"), o.optString("kind"), o.optString("target"))
    }
}

/** How the grader judged one session goal: "yes", "partly" or "no", with a short note. */
data class GoalResult(val id: String, val result: String, val note: String) {
    val reached: Boolean get() = result == "yes"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("result", result).put("note", note)

    companion object {
        fun fromJson(o: JSONObject) = GoalResult(o.optString("id"), o.optString("result", "no"), o.optString("note"))
    }
}

/** Something the grader noticed about the student's habits (not a single mistake): it goes into the learner memory. */
data class Observation(val text: String, val area: String, val polarity: String) {
    val strength: Boolean get() = polarity == "staerke"

    fun toJson(): JSONObject = JSONObject().put("text", text).put("area", area).put("polarity", polarity)

    companion object {
        fun fromJson(o: JSONObject) = Observation(o.optString("text"), o.optString("area"), o.optString("polarity", "schwaeche"))
    }
}

/** The independent second grading (like the second examiner in the Abitur) and how it was combined. */
data class SecondOpinion(val inhalt: Int, val darstellung: Int, val total: Int, val by: String, val firstTotal: Int, val reason: String) {
    val agrees: Boolean get() = kotlin.math.abs(total - firstTotal) <= 2

    fun toJson(): JSONObject =
        JSONObject().put("inhalt", inhalt).put("darstellung", darstellung).put("total", total).put("by", by).put("firstTotal", firstTotal).put("reason", reason)

    companion object {
        fun fromJson(o: JSONObject) =
            SecondOpinion(o.optInt("inhalt"), o.optInt("darstellung"), o.optInt("total"), o.optString("by"), o.optInt("firstTotal"), o.optString("reason"))
    }
}

/** One part of a model answer at 15-point level: a task of the presentation or an answer in the conversation. */
data class ModelPart(val title: String, val text: String, val notes: List<String> = emptyList()) {
    fun toJson(): JSONObject = JSONObject().put("title", title).put("text", text).put("notes", notes.toJsonArray())

    companion object {
        fun fromJson(o: JSONObject) = ModelPart(o.optString("title"), o.optString("text"), o.optJSONArray("notes").strings())
    }
}

/** What changed since an earlier session: points, mistake types that did not come back and those that did. */
data class Progress(
    val comparedTo: String,
    /** e.g. "Vortrag vom 02.11." */
    val comparedLabel: String,
    val comparedPoints: Int,
    /** Lines like "Zeitform: diesmal kein Fehler dieser Art." */
    val fixed: List<String>,
    /** Lines like "Präposition: kam wieder vor (2×) – schon in 3 Sitzungen in Folge." */
    val still: List<String>,
    /** For a second attempt: the repeated task and its points before and after (-1 = unknown). */
    val taskNumber: Int = 0,
    val taskBefore: Int = -1,
    val taskAfter: Int = -1,
) {
    fun toJson(): JSONObject =
        JSONObject().put("comparedTo", comparedTo).put("comparedLabel", comparedLabel).put("comparedPoints", comparedPoints)
            .put("fixed", fixed.toJsonArray()).put("still", still.toJsonArray())
            .put("taskNumber", taskNumber).put("taskBefore", taskBefore).put("taskAfter", taskAfter)

    companion object {
        fun fromJson(o: JSONObject) =
            Progress(
                comparedTo = o.optString("comparedTo"),
                comparedLabel = o.optString("comparedLabel"),
                comparedPoints = o.optInt("comparedPoints"),
                fixed = o.optJSONArray("fixed").strings(),
                still = o.optJSONArray("still").strings(),
                taskNumber = o.optInt("taskNumber"),
                taskBefore = o.optInt("taskBefore", -1),
                taskAfter = o.optInt("taskAfter", -1),
            )
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
    /** Which AI graded (e.g. "Gemini 4 Argon") and which one listened to the recording. */
    val gradedBy: String = "",
    val listenedBy: String = "",
    /** Where each task of the presentation begins: task number and the first words said for it. */
    val segments: List<Pair<Int, String>> = emptyList(),
    /** The recording suggests that the student read out the notes. */
    val readingAloud: Boolean = false,
    /** Fillers counted in the recording itself (the live transcript often leaves them out); -1 = not counted. */
    val fillers: Int = -1,
    /** The fillers he uses most ("ähm", "you know"). */
    val fillerWords: List<String> = emptyList(),
    /** Comparison with the last session (or with the first attempt, for a second attempt). */
    val progress: Progress? = null,
    /** The student's own presentation (or answers) rewritten at 15-point level, made on request. */
    val model: List<ModelPart> = emptyList(),
    /** How the goals of the session were reached. */
    val goalResults: List<GoalResult> = emptyList(),
    /** What the grader noticed about the student's habits. */
    val observations: List<Observation> = emptyList(),
    /** The independent second grading, if there was one. */
    val secondOpinion: SecondOpinion? = null,
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
            .put("segments", segments.toJsonArray { JSONObject().put("number", it.first).put("firstWords", it.second) })
            .put("readingAloud", readingAloud)
            .put("fillers", fillers)
            .put("fillerWords", fillerWords.toJsonArray())
            .put("progress", progress?.toJson() ?: JSONObject.NULL)
            .put("model", model.toJsonArray { it.toJson() })
            .put("goalResults", goalResults.toJsonArray { it.toJson() })
            .put("observations", observations.toJsonArray { it.toJson() })
            .put("secondOpinion", secondOpinion?.toJson() ?: JSONObject.NULL)
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
                segments = o.optJSONArray("segments").objects().map { it.optInt("number") to it.optString("firstWords") },
                readingAloud = o.optBoolean("readingAloud"),
                fillers = o.optInt("fillers", -1),
                fillerWords = o.optJSONArray("fillerWords").strings(),
                progress = o.optJSONObject("progress")?.let { Progress.fromJson(it) },
                model = o.optJSONArray("model").objects().map { ModelPart.fromJson(it) },
                goalResults = o.optJSONArray("goalResults").objects().map { GoalResult.fromJson(it) },
                observations = o.optJSONArray("observations").objects().map { Observation.fromJson(it) },
                secondOpinion = o.optJSONObject("secondOpinion")?.let { SecondOpinion.fromJson(it) },
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
    /** Seconds the live examiner was connected. */
    val examinerSeconds: Double = 0.0,
    /** The Abi-Weg level this session was played for (empty = free practice). */
    val levelId: String = "",
    /** Id of the session whose text and task are repeated here ("Zweiter Versuch"); empty for a normal session. */
    val retryOf: String = "",
    /** Technical log of the live parts (microphone, ear, transcript, coach), for the "Technik-Protokoll". */
    val tech: String = "",
    /** The goals the learning brain set for this session. */
    val goals: List<SessionGoal> = emptyList(),
    /** Further topic fields the conversation covered (part 2 links several, as in the Abitur). */
    val extraTopics: List<Int> = emptyList(),
    /** The competence this session trained on its own (Skill key); empty = the whole exam. */
    val focusSkill: String = "",
    /** How the examiner led the conversation (TalkStyle name). */
    val talkStyle: String = "",
    /** How hard the examiner was (Strictness name); empty = normal. */
    val strictness: String = "",
) {
    val studentText: String
        get() = utterances.filter { it.speaker == Speaker.STUDENT }.joinToString(" ") { it.text }

    val title: String
        get() {
            val base =
                when {
                    kind == SessionKind.PART1 && focusTask in 1..3 -> "Aufgabe $focusTask: " + (Knowledge.TASK_PATTERNS.getOrNull(focusTask - 1)?.germanName ?: "")
                    else -> kind.short
                }
            return if (retryOf.isNotBlank()) "Zweiter Versuch – $base" else base
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
            .put("levelId", levelId)
            .put("retryOf", retryOf)
            .put("tech", tech)
            .put("goals", goals.toJsonArray { it.toJson() })
            .put("extraTopics", org.json.JSONArray(extraTopics))
            .put("focusSkill", focusSkill)
            .put("talkStyle", talkStyle)
            .put("strictness", strictness)

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
                levelId = o.optString("levelId"),
                retryOf = o.optString("retryOf"),
                tech = o.optString("tech"),
                goals = o.optJSONArray("goals").objects().map { SessionGoal.fromJson(it) },
                extraTopics = o.optJSONArray("extraTopics")?.let { a -> (0 until a.length()).map { a.optInt(it, -1) }.filter { it in 0..13 } }.orEmpty(),
                focusSkill = o.optString("focusSkill"),
                talkStyle = o.optString("talkStyle"),
                strictness = o.optString("strictness"),
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
