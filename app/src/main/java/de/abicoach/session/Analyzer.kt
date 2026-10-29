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
import kotlin.math.roundToInt

/** Creates new, unknown exam material for part 1 (like the examiner's "Prüfungsvorlage"), from the student's course. */
object MaterialGenerator {
    data class Request(
        val topicId: Int,
        val textType: Knowledge.TextType,
        val source: Knowledge.Source,
        val short: Boolean = false,
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
        val finalPrompt =
            if (prompt.isNotEmpty()) {
                prompt
            } else {
                val anchored = source == Knowledge.Source.COURSE && course.hasMaterial()
                if (source == Knowledge.Source.COURSE && !course.hasMaterial()) {
                    note = note ?: "Noch kein Kursmaterial hochgeladen – der Text passt zum Themenfeld, aber noch nicht speziell zu deinem Unterricht."
                }
                onProgress(if (anchored) "Lese nach, was ihr dazu im Unterricht hattet …" else "Schreibe einen neuen Text …")
                val context = if (anchored) course.forTopic(request.topicId, maxChars = 5_000) else ""
                Prompts.materialRequest(request.topicId, request.textType, if (anchored) "" else overview, context, anchored)
            }
        val shortLine =
            if (request.short) "\nIMPORTANT: This is a short placement test. The text must have only 170 to 220 words; keep the three tasks simple." else ""
        onProgress("${app.ai.brainLabel()} schreibt Text und Aufgaben …")
        val answer = app.ai.brainJson(Prompts.MATERIAL_SYSTEM, finalPrompt + shortLine, Prompts.materialSchema, maxTokens = 12_000, thinking = true)
        val task = parse(app, answer.json, request.topicId, source.name.lowercase())
        return task to note
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
 * The full debrief after a session. With a Claude key: Gemini listens to the recording, Claude grades.
 * Without: Gemini does both in one call (it hears the audio itself).
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
        val audio = audioParts.isNotEmpty()

        if (hub.useClaude) {
            var listening: JSONObject? = null
            var listenedBy = ""
            if (audio) {
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
            val context = Prompts.analysisContext(record, courseContext, teacherNotes, listening?.let { Prompts.listeningSummary(it) }, profileNote)
            val answer = hub.brainJson(Prompts.ANALYSIS_SYSTEM, context, Prompts.analysisSchema, maxTokens = 20_000, thinking = true)
            return Prompts.parseDebrief(answer.json, record.studentText, listening != null, listening, answer.by, listenedBy)
        }

        onProgress("${hub.brainLabel()} hört zu und bewertet deine Leistung …")
        val context = Prompts.analysisContext(record, courseContext, teacherNotes, null, profileNote)
        val answer = hub.listenJson(Prompts.ANALYSIS_SYSTEM, listOf(GeminiRest.textPart(context)) + audioParts, Prompts.analysisSchema)
        return Prompts.parseDebrief(answer.json, record.studentText, audio, null, answer.by, if (audio) answer.by else "")
    }

    private fun profileNote(): String {
        val profile = app.profiles.profile.value
        if (profile.skills.isEmpty()) return ""
        val weak = profile.levers(3).joinToString(", ") { s -> "${s.label} (${profile.skills[s]?.score?.roundToInt() ?: "?"} P.)" }
        val strong = profile.strengths(2).joinToString(", ") { it.label }
        return "Größte Hebel bisher: $weak" + if (strong.isNotBlank()) ". Stärken: $strong." else "."
    }
}

/** "Frag dein Material": answers a question with passages from the student's own course. */
object MaterialQuestion {
    data class Answer(val text: String, val hits: List<Hit>, val by: String)

    suspend fun ask(app: AbiCoachApp, question: String, history: List<Pair<String, String>> = emptyList()): Answer {
        val (passages, hits) = app.course.forQuery(question)
        val request = Prompts.askRequest(question, passages, app.course.overview(1_200))
        val turns = history.takeLast(6) + ("user" to request)
        val (text, by) = app.ai.brainText(Prompts.ASK_SYSTEM, turns)
        return Answer(text, hits, by)
    }
}

/** Which skills a debrief rated, as German text for screens. */
fun Debrief.skillSummary(): List<Pair<Skill, Int>> = Skill.entries.mapNotNull { s -> skills[s]?.let { s to it } }

@Suppress("unused")
private fun labelFor(hub: AiHub): String = hub.brainLabel()
