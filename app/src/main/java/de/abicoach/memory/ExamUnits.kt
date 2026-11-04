package de.abicoach.memory

import de.abicoach.AbiCoachApp
import de.abicoach.ai.AiException
import de.abicoach.ai.arr
import de.abicoach.ai.bool
import de.abicoach.ai.enumOf
import de.abicoach.ai.int
import de.abicoach.ai.obj
import de.abicoach.ai.str
import de.abicoach.data.ExamTask
import de.abicoach.data.Knowledge
import de.abicoach.data.Part2Topic
import de.abicoach.data.TaskItem
import de.abicoach.data.objects
import de.abicoach.data.strings
import de.abicoach.data.toJsonArray
import org.json.JSONObject

/**
 * One original exam task from real Abitur material the student uploaded (past papers, official sample tasks,
 * Abi preparation books): the material text, the original tasks with their Erwartungshorizont and, if the
 * material had one, the model solution.
 */
data class ExamUnit(
    val id: String,
    val docId: String,
    val title: String,
    /** e.g. "Abitur NRW 2022, Grundkurs, mündliche Prüfung" or "Abi-Buch, Übungsaufgabe 4". */
    val source: String,
    val startPage: Int,
    val endPage: Int,
    val topic: Int,
    val literary: Boolean,
    /** First and last words of the material text, to cut it exactly out of the pages. */
    val textStart: String,
    val textEnd: String,
    val tasks: List<TaskItem>,
    val part2: List<String>,
    val solution: String,
    /** "oral" (mündliche Prüfung), "written" (Klausur/schriftliches Abitur) or "practice". */
    val format: String,
) {
    fun toJson(): JSONObject =
        JSONObject().put("id", id).put("docId", docId).put("title", title).put("source", source).put("startPage", startPage)
            .put("endPage", endPage).put("topic", topic).put("literary", literary).put("textStart", textStart).put("textEnd", textEnd)
            .put("tasks", tasks.toJsonArray { it.toJson() }).put("part2", part2.toJsonArray()).put("solution", solution).put("format", format)

    companion object {
        fun fromJson(o: JSONObject) =
            ExamUnit(
                id = o.optString("id"),
                docId = o.optString("docId"),
                title = o.optString("title"),
                source = o.optString("source"),
                startPage = o.optInt("startPage"),
                endPage = o.optInt("endPage"),
                topic = o.optInt("topic", -1),
                literary = o.optBoolean("literary"),
                textStart = o.optString("textStart"),
                textEnd = o.optString("textEnd"),
                tasks = o.optJSONArray("tasks").objects().map { TaskItem.fromJson(it) },
                part2 = o.optJSONArray("part2").strings(),
                solution = o.optString("solution"),
                format = o.optString("format", "practice"),
            )
    }
}

/** Finds the original exam tasks in Abitur material and turns them into practice sessions. */
object ExamUnits {
    private val topicList = Knowledge.TOPICS.joinToString("\n") { "${it.id}: ${it.english}" }

    private val SYSTEM =
        """
Du durchsuchst Abitur-Übungsmaterial für das Fach Englisch (NRW): echte Prüfungsaufgaben früherer Jahre, offizielle Beispielaufgaben oder ein Abi-Vorbereitungsbuch. Finde jede vollständige Prüfungsaufgabe, die aus EINEM Text (Sachtext oder literarischer Auszug, ca. 150–900 Wörter) und dazugehörigen Aufgaben besteht – besonders Aufgaben für die mündliche Prüfung (Teil 1 mit drei Aufgaben AFB I–III, oft auch Impulse für Teil 2), aber auch schriftliche Aufgaben mit Textaufgaben (comprehension, analysis, evaluation/comment). Aufgaben nur mit Bildern, Cartoons, Statistiken oder Hörverstehen lässt du weg.
Für jede Prüfungsaufgabe:
- title: Titel des Textes
- source: woher sie stammt, auf Deutsch (z. B. „Abitur NRW 2022, Grundkurs, mündliche Prüfung“, „Abi-Buch, Übungsaufgabe 4“)
- format: "oral" (mündliche Prüfung), "written" (schriftlich) oder "practice" (Übungsaufgabe)
- start_page und end_page: die Seiten, auf denen der TEXT steht (Seitenangaben wie im Dokument, „S. n“)
- text_start: die ersten 6–10 Wörter des Textes genau wie im Dokument; text_end: die letzten 6–10 Wörter des Textes genau wie im Dokument
- topic: Nummer des Themenfelds (siehe Liste), literary: true bei Literatur
- tasks: die Original-Aufgaben in Reihenfolge, wörtlich auf Englisch, mit operator, afb ("I", "II" oder "III") und expectations: der Original-Erwartungshorizont bzw. die Kernpunkte der Musterlösung als kurze englische Punkte (3–8 je Aufgabe). Steht im Material keiner, lass expectations leer.
- part2_questions: Original-Impulse oder Fragen für das Prüfungsgespräch (Teil 2), sonst leer
- solution: die Kernpunkte eines Lösungsvorschlags im Material (auf Englisch oder Deutsch, höchstens 1.500 Zeichen), sonst leer
Themenfelder:
$topicList
Wenn das Dokument keine solchen Aufgaben enthält, gib eine leere Liste zurück.
        """.trimIndent()

    private val schema =
        obj(
            "units" to
                arr(
                    obj(
                        "title" to str(),
                        "source" to str(),
                        "format" to enumOf(listOf("oral", "written", "practice")),
                        "start_page" to int(),
                        "end_page" to int(),
                        "text_start" to str(),
                        "text_end" to str(),
                        "topic" to int(),
                        "literary" to bool(),
                        "tasks" to arr(obj("operator" to str(), "afb" to enumOf(listOf("I", "II", "III")), "instruction" to str(), "expectations" to arr(str()))),
                        "part2_questions" to arr(str()),
                        "solution" to str(),
                    ),
                ),
        )

    /** Reads the original exam tasks of one document (Abi-Prüfungsaufgaben or an Abi preparation book). */
    suspend fun extract(app: AbiCoachApp, doc: CourseDoc, documentText: String): List<ExamUnit> {
        val user = "Dokument: \"${doc.title}\" (${doc.kind.label}, ${doc.pageCount} Seiten)\n\n$documentText"
        val json =
            try {
                app.ai.brainJson(SYSTEM, user, schema, thinking = false).json
            } catch (e: AiException) {
                if (e.isPermanent) return emptyList() else throw e
            }
        return json.optJSONArray("units").objects().mapIndexedNotNull { i, o ->
            val tasks =
                o.optJSONArray("tasks").objects().map {
                    TaskItem(it.optString("operator"), it.optString("afb"), it.optString("instruction"), it.optJSONArray("expectations").strings())
                }.filter { it.instruction.isNotBlank() }
            val start = o.optInt("start_page")
            if (tasks.isEmpty() || start !in 1..doc.pageCount) return@mapIndexedNotNull null
            ExamUnit(
                id = "${doc.id}#$i",
                docId = doc.id,
                title = o.optString("title").ifBlank { "Prüfungsaufgabe ${i + 1}" },
                source = o.optString("source").ifBlank { doc.title },
                startPage = start,
                endPage = o.optInt("end_page", start).coerceIn(start, doc.pageCount),
                topic = o.optInt("topic", -1).takeIf { it in 0..13 } ?: -1,
                literary = o.optBoolean("literary"),
                textStart = o.optString("text_start").trim(),
                textEnd = o.optString("text_end").trim(),
                tasks = tasks,
                part2 = o.optJSONArray("part2_questions").strings(),
                solution = o.optString("solution").trim().take(2_000),
                format = o.optString("format", "practice"),
            )
        }
    }

    /** All original exam tasks in the memory, optionally for one topic field. */
    fun all(app: AbiCoachApp, topicId: Int? = null): List<ExamUnit> =
        app.memory.allDigests().flatMap { (_, d) -> d.examUnits }
            .filter { topicId == null || topicId < 0 || it.topic == topicId }

    /** Ids of the original tasks the student has already practised. */
    fun used(app: AbiCoachApp): Set<String> = app.store.sessions.value.mapNotNull { it.task?.unitId?.takeIf { id -> id.isNotBlank() } }.toSet()

    /** An original task, preferring the topic field and tasks not practised yet. */
    fun pick(app: AbiCoachApp, topicId: Int, fixedId: String? = null): ExamUnit? {
        val all = all(app)
        if (fixedId != null) return all.firstOrNull { it.id == fixedId }
        if (all.isEmpty()) return null
        val used = used(app)
        val forTopic = all.filter { topicId < 0 || it.topic == topicId }
        val pool = forTopic.ifEmpty { all }
        return pool.filter { it.id !in used }.ifEmpty { pool }.shuffled().first()
    }

    /** The material text of a unit, cut exactly between its first and last words; the whole pages if that fails. */
    fun text(app: AbiCoachApp, unit: ExamUnit): String {
        val pages = app.course.pageText(unit.docId, unit.startPage, unit.endPage)
        if (pages.isBlank()) return ""
        val from = anchor(pages, unit.textStart, 0)
        val endMatch = if (from != null) anchorEnd(pages, unit.textEnd, from.first) else null
        val cut = if (from != null && endMatch != null && endMatch > from.first) pages.substring(from.first, endMatch) else pages
        return cut.replace(Regex("\\[(Bild|Kommentar|Korrektur|Fehlerzeichen|Schüler):[^\\]]*\\]"), "").trim()
    }

    private fun pattern(words: String): Regex? {
        val parts = words.split(Regex("\\s+")).map { it.trim('"', '„', '“', '”', '\'', '(', ')', ',', '.', ';', ':', '!', '?') }.filter { it.isNotBlank() }
        if (parts.size < 3) return null
        return Regex(parts.joinToString("[\\s\\W]+") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    }

    private fun anchor(text: String, words: String, from: Int): Pair<Int, Int>? {
        val r = pattern(words) ?: return null
        val m = r.find(text, from) ?: return null
        return m.range.first to m.range.last + 1
    }

    private fun anchorEnd(text: String, words: String, from: Int): Int? {
        val r = pattern(words) ?: return null
        val m = r.find(text, from) ?: return null
        // Include the punctuation right after the last word.
        var end = m.range.last + 1
        while (end < text.length && text[end] in ".!?\"”“'’)") end++
        return end
    }

    private val EXPECT_SYSTEM =
        """
Du bist Prüferin im NRW-Abitur Englisch. Zu einer ORIGINAL-Prüfungsaufgabe fehlt teilweise der Erwartungshorizont. Schreibe für jede Aufgabe ohne Erwartungshorizont 3–6 kurze englische Punkte, die ein 15-Punkte-Vortrag enthalten würde (bezogen auf genau diesen Text), und – falls keine Impulse für Teil 2 da sind – zwei andere Themenfelder (topic_id) mit je einer Fokusfrage für das Prüfungsgespräch. Verändere die Original-Aufgaben nicht.
        """.trimIndent()

    private val expectSchema =
        obj(
            "expectations" to arr(obj("number" to int(), "points" to arr(str()))),
            "part2_topics" to arr(obj("topic_id" to int(), "question" to str())),
        )

    /** Turns an original task into the material of a practice session; missing parts are filled in by the AI. */
    suspend fun toTask(app: AbiCoachApp, unit: ExamUnit, onProgress: (String) -> Unit = {}): ExamTask {
        val text = text(app, unit)
        if (text.split(Regex("\\s+")).count { it.isNotBlank() } < 60) throw IllegalStateException("Der Text der Original-Aufgabe konnte nicht gelesen werden.")
        // The exam has three tasks; written papers sometimes have four: keep the first three in their order.
        var tasks = unit.tasks.take(3)
        val topic = if (unit.topic in 0..13) unit.topic else app.course.pickTopicFromCourse()
        var part2 =
            unit.part2.take(2).mapIndexed { i, q ->
                val id = app.course.pickTopicFromCourse(exclude = setOf(topic))
                Part2Topic(if (i == 0) id else app.course.pickTopicFromCourse(exclude = setOf(topic, id)), q, app.course.factsFor(id, 6))
            }
        val missing = tasks.withIndex().filter { it.value.expectations.isEmpty() }
        if ((missing.isNotEmpty() || part2.isEmpty()) && app.ai.hasGemini) {
            onProgress("Ergänze den Erwartungshorizont …")
            val sb = StringBuilder("Text: \"${unit.title}\"\n$text\n\nOriginal-Aufgaben:\n")
            tasks.forEachIndexed { i, t ->
                sb.append(i + 1).append(". [AFB ").append(t.afb).append("] ").append(t.instruction)
                if (t.expectations.isNotEmpty()) sb.append(" (Erwartungshorizont vorhanden)")
                sb.append('\n')
            }
            if (part2.isNotEmpty()) sb.append("Impulse für Teil 2 sind vorhanden.\n")
            sb.append("\nThemenfelder:\n").append(topicList)
            runCatching {
                val o = app.ai.brainJson(EXPECT_SYSTEM, sb.toString(), expectSchema, thinking = false).json
                val points = o.optJSONArray("expectations").objects().associate { it.optInt("number") to it.optJSONArray("points").strings() }
                tasks = tasks.mapIndexed { i, t -> if (t.expectations.isEmpty()) t.copy(expectations = points[i + 1].orEmpty()) else t }
                if (part2.isEmpty()) {
                    part2 =
                        o.optJSONArray("part2_topics").objects().map { Part2Topic(it.optInt("topic_id", -1), it.optString("question")) }
                            .filter { it.question.isNotBlank() }
                            .map { p ->
                                val id = if (p.topicId in 0..13 && p.topicId != topic) p.topicId else app.course.pickTopicFromCourse(exclude = setOf(topic))
                                p.copy(topicId = id, courseFacts = app.course.factsFor(id, 6))
                            }.distinctBy { it.topicId }.take(2)
                }
            }
        }
        return ExamTask(
            topicId = topic,
            title = unit.title,
            textType = if (unit.literary) "literary text" else "non-fiction text",
            text = text,
            sourceNote = "Original: ${unit.source}",
            tasks = tasks,
            part2 = part2,
            source = "real",
            unitId = unit.id,
            solution = unit.solution,
        )
    }

    /** Two original task sets as style examples for newly written exam material. */
    fun styleExamples(app: AbiCoachApp, topicId: Int): String {
        val all = all(app).filter { it.tasks.size >= 3 }
        if (all.isEmpty()) return ""
        val picks = (all.filter { it.topic == topicId } + all).distinctBy { it.id }.take(2)
        val sb = StringBuilder("Real exam tasks from the student's uploaded Abitur material – write the three tasks in exactly this style, with this precision and difficulty:\n")
        picks.forEach { u ->
            sb.append("• \"").append(u.title).append("\" (").append(u.source).append("): ")
            sb.append(u.tasks.take(3).joinToString(" / ") { it.instruction }).append('\n')
        }
        return sb.toString()
    }
}
