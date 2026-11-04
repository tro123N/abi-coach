package de.abicoach.session

import de.abicoach.AbiCoachApp
import de.abicoach.ai.AiHub
import de.abicoach.ai.GeminiRest
import de.abicoach.data.Debrief
import de.abicoach.data.ExamTask
import de.abicoach.data.Knowledge
import de.abicoach.data.Part2Topic
import de.abicoach.data.SessionRecord
import de.abicoach.data.TaskItem
import de.abicoach.data.objects
import de.abicoach.data.strings
import de.abicoach.memory.Hit
import de.abicoach.profile.Skill
import org.json.JSONObject

/** Creates new, unknown exam material for part 1 (like the examiner's "Prüfungsvorlage"), from the student's course. */
object MaterialGenerator {
    data class Request(
        val topicId: Int,
        val textType: Knowledge.TextType,
        val source: Knowledge.Source,
        val short: Boolean = false,
        /** Requirement of an Abi-Weg level, e.g. "Task 2 must be 'Examine how the author perceives …'". */
        val instruction: String = "",
        /** A specific original exam task (Source.REAL); empty = pick one. */
        val unitId: String = "",
    )

    /** Returns the material and, if the wished source was not possible, a note for the student. */
    suspend fun generate(
        app: AbiCoachApp,
        request: Request,
        fixedDocId: String? = null,
        fixedPage: Int = 0,
        onProgress: (String) -> Unit = {},
    ): Pair<ExamTask, String?> {
        val course = app.course
        var note: String? = null
        var source = request.source
        // An original exam task from the student's Abitur material: its own text, tasks and Erwartungshorizont.
        if (source == Knowledge.Source.REAL) {
            val unit = de.abicoach.memory.ExamUnits.pick(app, request.topicId, request.unitId.ifBlank { null })
            if (unit != null) {
                onProgress("Hole die Original-Aufgabe „${unit.title}“ …")
                try {
                    return de.abicoach.memory.ExamUnits.toTask(app, unit, onProgress) to null
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    note = "Die Original-Aufgabe ließ sich nicht lesen (${e.message}) – hier ein neuer Text im gleichen Stil."
                }
            } else {
                note = "Noch keine echten Prüfungsaufgaben im Gedächtnis – lade dein Abi-Material unter „Mein Kurs“ hoch. Hier ein neuer Text im Prüfungsstil."
            }
            source = Knowledge.Source.COURSE
        }
        val overview = course.overview(1_500)
        val prompt: String
        if (source == Knowledge.Source.MATERIAL) {
            val pick =
                if (fixedDocId != null) {
                    course.examTexts(null).firstOrNull { (doc, ref) -> doc.id == fixedDocId && ref.startPage == fixedPage }
                } else {
                    course.examTexts(request.topicId).randomOrNull()
                }
            if (pick == null) {
                note = "In deinem Material habe ich zu diesem Themenfeld noch keinen passenden Text gefunden – der Text ist neu, passt aber zu deinem Kurs."
                source = Knowledge.Source.COURSE
                prompt = ""
            } else {
                val (doc, ref) = pick
                onProgress("Suche den Text in „${doc.title}“ …")
                val pages = course.pageText(doc.id, ref.startPage, ref.endPage).take(14_000)
                val pageLabel = if (ref.endPage > ref.startPage) "S. ${ref.startPage}–${ref.endPage}" else "S. ${ref.startPage}"
                prompt = Prompts.materialFromText(doc.title, pageLabel, pages, if (ref.topic >= 0) ref.topic else request.topicId, overview)
            }
        } else {
            prompt = ""
        }
        // The learning brain shapes ordinary practice material for this student: text type, operators, level.
        // Not for the placement test and not for an Abi-Weg level, which has its own requirement.
        val ownText = prompt.isNotEmpty()
        val adapt =
            if (!request.short && request.instruction.isBlank()) {
                runCatching { de.abicoach.learn.LearningBrain.adaptMaterial(app, request.textType, withText = !ownText) }.getOrNull()
            } else {
                null
            }
        val finalPrompt =
            if (ownText) {
                prompt
            } else {
                val anchored = source == Knowledge.Source.COURSE && course.hasMaterial()
                if (source == Knowledge.Source.COURSE && !course.hasMaterial()) {
                    note = note ?: "Noch kein Kursmaterial hochgeladen – der Text passt zum Themenfeld, aber noch nicht speziell zu deinem Unterricht."
                }
                onProgress(if (anchored) "Lese nach, was ihr dazu im Unterricht hattet …" else "Schreibe einen neuen Text …")
                val context = if (anchored) course.forTopic(request.topicId, maxChars = 5_000) else ""
                Prompts.materialRequest(
                    request.topicId,
                    adapt?.textType ?: request.textType,
                    if (anchored) "" else overview,
                    context,
                    anchored,
                    de.abicoach.memory.ExamUnits.styleExamples(app, request.topicId),
                )
            }
        val shortLine =
            if (request.short) "\nIMPORTANT: This is a short placement test. The text must have only 170 to 220 words; keep the three tasks simple." else ""
        val levelLine = if (request.instruction.isNotBlank()) "\nIMPORTANT – requirement of this exercise: ${request.instruction}" else ""
        val adaptLine = adapt?.let { "\n${it.instruction}" }.orEmpty()
        // The exam conversation goes to the topic fields the student needs most (weak, unsure or never practised).
        val p2a = de.abicoach.learn.LearningBrain.pickTopic(app, exclude = setOf(request.topicId))
        val p2b = de.abicoach.learn.LearningBrain.pickTopic(app, exclude = setOf(request.topicId, p2a))
        val part2Line = "\nFor part2_topics use these two topic fields, which the student needs most: $p2a = ${Knowledge.topic(p2a)?.english ?: "any"}; $p2b = ${Knowledge.topic(p2b)?.english ?: "any"}."
        onProgress("${app.ai.brainLabel()} schreibt Text und Aufgaben …")
        val answer = app.ai.brainJson(Prompts.MATERIAL_SYSTEM, finalPrompt + shortLine + levelLine + adaptLine + part2Line, Prompts.materialSchema)
        val task = parse(app, answer.json, request.topicId, source.name.lowercase())
        return task.copy(adapted = adapt?.summary.orEmpty()) to note
    }

    private fun parse(app: AbiCoachApp, json: JSONObject, topicId: Int, source: String): ExamTask {
        val tasks =
            json.optJSONArray("tasks").objects().map {
                TaskItem(it.optString("operator"), it.optString("afb"), it.optString("instruction"), it.optJSONArray("expectations").strings())
            }.filter { it.instruction.isNotBlank() }.take(3)
        val text = json.optString("text").trim()
        if (text.isBlank() || tasks.size < 3) throw IllegalStateException("Das Material ist unvollständig – bitte nochmal versuchen.")
        val part2 =
            json.optJSONArray("part2_topics").objects()
                .map { Part2Topic(it.optInt("topic_id", -1), it.optString("question")) }
                .filter { it.question.isNotBlank() }
                .map { p ->
                    val id = if (p.topicId in 0..13 && p.topicId != topicId) p.topicId else app.course.pickTopicFromCourse(exclude = setOf(topicId))
                    p.copy(topicId = id, courseFacts = app.course.factsFor(id, 6))
                }
                .distinctBy { it.topicId }
                .take(2)
        return ExamTask(
            topicId = topicId,
            title = json.optString("title").ifBlank { "Text" },
            textType = json.optString("text_type"),
            text = text,
            sourceNote = json.optString("source_note"),
            tasks = tasks,
            part2 = part2,
            source = source,
            courseLinks = json.optJSONArray("course_links").strings(),
        )
    }
}

/**
 * The full debrief after a session, all by Gemini: first the recording is listened to (pronunciation, intonation,
 * fluency), then the strongest model grades the transcript with the NRW scheme and that listening result.
 * The grader never sees earlier points, so every session is judged on its own.
 */
class Analyzer(private val app: AbiCoachApp) {
    suspend fun analyze(record: SessionRecord, onProgress: (String) -> Unit): Debrief {
        val hub = app.ai
        onProgress("Sammle dein Kursmaterial zum Thema …")
        val courseContext = if (record.topicId >= 0) runCatching { app.course.forTopic(record.topicId, maxChars = 4_000) }.getOrDefault("") else ""
        val teacherNotes = app.course.teacherNotes()
        val profileNote = profileNote()

        val audioParts = mutableListOf<JSONObject>()
        if (app.settings.uploadAudio && hub.hasGemini) {
            for (part in listOf(1, 2, 0)) {
                val file = app.store.audioFile(record.id, part)
                if (!file.exists() || file.length() < 64_000) continue
                val label = when (part) { 1 -> "Teil 1"; 2 -> "Teil 2"; else -> "Training" }
                onProgress("Lade die Aufnahme hoch ($label) …")
                try {
                    val uri = hub.gemini.uploadFile(file, "audio/wav", "abicoach-${record.id}-$part")
                    audioParts += GeminiRest.textPart("Audioaufnahme des Prüflings ($label), nur sein Mikrofon:")
                    audioParts += GeminiRest.filePart("audio/wav", uri)
                } catch (e: Exception) {
                    onProgress("Aufnahme übersprungen: ${e.message}")
                }
            }
        }

        var listening: JSONObject? = null
        var listenedBy = ""
        if (audioParts.isNotEmpty()) {
            onProgress("${hub.listenerLabel()} hört deine Aufnahme an …")
            runCatching {
                val intro =
                    if (record.studentText.isBlank()) "Es liegt kein Transkript vor – schreibe das Gesagte in transcript mit." else "Ein Transkript liegt bereits vor; transcript bleibt leer."
                hub.listenJson(Prompts.LISTEN_SYSTEM, listOf(GeminiRest.textPart(intro)) + audioParts, Prompts.listenSchema)
            }.onSuccess {
                listening = it.json
                listenedBy = it.by
            }.onFailure { onProgress("Aufnahme konnte nicht angehört werden: ${it.message}") }
        }
        onProgress("${hub.brainLabel()} bewertet deine Leistung …")
        val listened = listening?.let { Prompts.listeningSummary(it) }
        val context = Prompts.analysisContext(record, courseContext, teacherNotes, listened, profileNote, app.insights.knownTexts())
        val answer = hub.brainJson(Prompts.ANALYSIS_SYSTEM, context, Prompts.analysisSchema)
        val parsed = Prompts.parseDebrief(answer.json, record.studentText, listening != null, listening, answer.by, listenedBy)
        val goalIds = record.goals.map { it.id }.toSet()
        val first = parsed.copy(goalResults = parsed.goalResults.filter { it.id in goalIds })
        if (!needsSecondOpinion(record)) return first
        // Like the committee in the Abitur: an independent second grading, the result is the mean of both.
        onProgress("Zweitbewertung: eine zweite Prüferin bewertet unabhängig …")
        return try {
            // The second examiner sees the same session, but neither the first grade nor the goals or the profile.
            val second = hub.brainJson(Prompts.SECOND_SYSTEM, Prompts.analysisContext(record.copy(goals = emptyList()), courseContext, teacherNotes, listened, ""), Prompts.secondSchema)
            combine(first, second.json, second.by)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onProgress("Zweitbewertung übersprungen: ${e.message}")
            first
        }
    }

    /** Sessions that weigh much in the profile and are long enough for a fair second look. */
    private fun needsSecondOpinion(record: SessionRecord): Boolean {
        val words = record.studentText.split(Regex("\\s+")).count { it.isNotBlank() }
        if (words < 150 || !app.ai.hasGemini) return false
        return when (record.kind) {
            de.abicoach.data.SessionKind.SIMULATION, de.abicoach.data.SessionKind.PLACEMENT -> true
            de.abicoach.data.SessionKind.PART1 -> record.focusTask == 0 && record.retryOf.isBlank()
            de.abicoach.data.SessionKind.PART2 -> record.durationSec >= 300
            de.abicoach.data.SessionKind.TRAINING -> false
        }
    }

    /** Averages both gradings (points and skills); the reasons of the first grading stay, the second is kept for display. */
    private fun combine(first: Debrief, o: JSONObject, by: String): Debrief {
        val inhalt = o.optJSONObject("inhalt")
        val darstellung = o.optJSONObject("darstellung")
        val i2 = inhalt?.optInt("points", -1) ?: -1
        val d2 = darstellung?.optInt("points", -1) ?: -1
        if (i2 !in 0..15 || d2 !in 0..15) return first
        val total2 = Math.round(0.4 * i2 + 0.6 * d2).toInt().coerceIn(0, 15)
        val i = Math.round((first.inhaltPoints + i2) / 2.0).toInt().coerceIn(0, 15)
        val d = Math.round((first.darstellungPoints + d2) / 2.0).toInt().coerceIn(0, 15)
        val total = Math.round(0.4 * i + 0.6 * d).toInt().coerceIn(0, 15)
        val skillsJson = o.optJSONObject("skills") ?: JSONObject()
        val skills = first.skills.toMutableMap()
        Skill.entries.forEach { s ->
            val v = skillsJson.optInt(s.key, -1)
            // Pronunciation comes from listening to the recording: that measurement stays.
            if (v in 0..15 && s != Skill.AUSSPRACHE) skills[s] = skills[s]?.let { Math.round((it + v) / 2.0).toInt() } ?: v
        }
        val reason = listOf(inhalt?.optString("reason").orEmpty(), darstellung?.optString("reason").orEmpty()).filter { it.isNotBlank() }.joinToString(" ").take(500)
        return first.copy(
            inhaltPoints = i,
            darstellungPoints = d,
            totalPoints = total,
            skills = skills,
            secondOpinion = de.abicoach.data.SecondOpinion(i2, d2, total2, by, first.totalPoints, reason),
        )
    }

    /**
     * What the grader may know about the student: only the weak areas (to make the tips personal), never scores.
     * Earlier points would anchor the new grade; the progress is compared afterwards, outside the grading.
     */
    private fun profileNote(): String {
        val profile = app.profiles.profile.value
        if (profile.skills.isEmpty()) return ""
        val weak = profile.levers(3).joinToString(", ") { it.label }
        val strong = profile.strengths(2).joinToString(", ") { it.label }
        return "Aus früheren Sitzungen bekannte Schwerpunkte (nur für Hebel und Tipps nutzen, nie für die Punkte): Schwächer: $weak" +
            if (strong.isNotBlank()) ". Stärker: $strong." else "."
    }
}

/** "Frag dein Material": answers a question with passages from the student's own course. */
object MaterialQuestion {
    data class Answer(val text: String, val hits: List<Hit>, val by: String, val actions: List<CoachAction>)

    suspend fun ask(app: AbiCoachApp, question: String, history: List<Pair<String, String>> = emptyList()): Answer {
        val (passages, hits) = app.course.forQuery(question)
        val request = Prompts.askRequest(question, passages, app.course.overview(1_200), pathNote(app))
        val turns = history.takeLast(6) + ("user" to request)
        val (raw, by) = app.ai.brainText(Prompts.ASK_SYSTEM, turns)
        val (text, actions) = CoachAction.extract(raw)
        return Answer(text, hits, by, actions)
    }

    private fun pathNote(app: AbiCoachApp): String = de.abicoach.coach.CoachContext.student(app)
}

/** A button under a coach answer that starts an exercise. */
data class CoachAction(val kind: String, val arg: String = "") {
    val label: String
        get() =
            when (kind) {
                "aufgabe1" -> "Zusammenfassen üben"
                "aufgabe2" -> "Analysieren üben"
                "aufgabe3" -> "Kommentieren üben"
                "vortrag" -> "Ganzen Vortrag üben"
                "gespraech" -> arg.toIntOrNull()?.let { "Gespräch: " + Knowledge.topicShort(it) } ?: "Prüfungsgespräch üben"
                "einstufung" -> "Einstufung machen"
                "sprint" -> "Gesprächs-Sprint"
                "simulation" -> "Prüfungssimulation"
                "wortschatz" -> "Wortschatz wiederholen"
                "aussprache" -> "Aussprache-Labor"
                "training" -> "Sprech-Training (deine Fehler)"
                "dossier" -> "Dossier: " + (arg.toIntOrNull()?.let { Knowledge.topicShort(it) } ?: arg)
                "echt" -> "Echte Prüfungsaufgabe"
                "level" -> "Level: " + (de.abicoach.path.Curriculum.level(arg)?.title ?: arg)
                else -> kind
            }

    companion object {
        /** Reads "aufgabe2", "gespraech:7" or "level:w3.perceive"; null for anything unknown. */
        fun parse(value: String): CoachAction? = valid(value.substringBefore(':').trim().lowercase(), value.substringAfter(':', "").trim())

        /** Checks kind and argument; an argument is kept only where it means something (level, dossier, topic of a conversation). */
        private fun valid(kind: String, arg: String): CoachAction? =
            when {
                kind !in KINDS -> null
                kind == "level" -> if (de.abicoach.path.Curriculum.level(arg) == null) null else CoachAction(kind, arg)
                kind == "dossier" -> if ((arg.toIntOrNull() ?: -1) !in 0..13) null else CoachAction(kind, arg)
                kind == "gespraech" -> CoachAction(kind, arg.takeIf { (it.toIntOrNull() ?: -1) in 0..13 }.orEmpty())
                else -> CoachAction(kind)
            }

        private val KINDS = setOf("aufgabe1", "aufgabe2", "aufgabe3", "vortrag", "gespraech", "sprint", "simulation", "wortschatz", "aussprache", "training", "dossier", "echt", "level", "einstufung")
        private val TAG = Regex("\\[\\[\\s*([a-zA-Z0-9]+)\\s*(?::\\s*([\\w.]+))?\\s*]]")

        /** Removes the action markers from the text and returns the valid actions. */
        fun extract(raw: String): Pair<String, List<CoachAction>> {
            val actions =
                TAG.findAll(raw).mapNotNull { m -> valid(m.groupValues[1].lowercase(), m.groupValues[2]) }.distinct().take(3).toList()
            val text = raw.replace(TAG, "").replace(Regex("\n{3,}"), "\n\n").trim()
            return text to actions
        }
    }
}

/** Which skills a debrief rated, as German text for screens. */
fun Debrief.skillSummary(): List<Pair<Skill, Int>> = Skill.entries.mapNotNull { s -> skills[s]?.let { s to it } }

@Suppress("unused")
private fun labelFor(hub: AiHub): String = hub.brainLabel()
