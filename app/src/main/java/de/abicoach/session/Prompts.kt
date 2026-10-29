package de.abicoach.session

import de.abicoach.data.Debrief
import de.abicoach.data.ExamTask
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.LiveError
import de.abicoach.data.PronTip
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import de.abicoach.data.Phrase
import de.abicoach.data.Upgrade
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** All prompts, setup messages and response schemas in one place. */
object Prompts {
    // ---------- Live setup messages ----------

    fun transcriberSetup(model: String, vocabulary: List<String>, minimal: Boolean): JSONObject {
        val transcription = JSONObject().put("mode", "VERBATIM")
        if (!minimal) {
            transcription.put("languageCodes", JSONArray().put("en-GB"))
            transcription.put("customVocabulary", JSONArray(vocabulary.distinct().take(100)))
        }
        val setup =
            JSONObject()
                .put("model", "models/" + model.removePrefix("models/"))
                .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT")))
                .put("inputAudioTranscription", transcription)
        return JSONObject().put("setup", setup)
    }

    fun examinerSetup(model: String, voice: String, system: String, handle: String?, minimal: Boolean): JSONObject {
        val generation =
            JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put(
                    "speechConfig",
                    JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))),
                )
        val setup =
            JSONObject()
                .put("model", "models/" + model.removePrefix("models/"))
                .put("generationConfig", generation)
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("outputAudioTranscription", JSONObject())
                .put("inputAudioTranscription", JSONObject())
        if (!minimal) {
            // Learners pause to think: wait longer before treating silence as the end of a turn.
            setup.put(
                "realtimeInputConfig",
                JSONObject().put(
                    "automaticActivityDetection",
                    JSONObject().put("endOfSpeechSensitivity", "END_SENSITIVITY_LOW").put("silenceDurationMs", 1400),
                ),
            )
            setup.put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            setup.put("sessionResumption", if (handle != null) JSONObject().put("handle", handle) else JSONObject())
        }
        return JSONObject().put("setup", setup)
    }

    // ---------- Examiner instructions ----------

    private const val EXAMINER_RULES = """
Rules:
- Speak British English at a natural, calm pace. Keep each of your turns short: one to three sentences, usually one open question.
- Ask about larger contexts and connections, not isolated facts. Use questions that make the student explain, compare, evaluate, take a stand and give examples.
- Follow up on what the student says: ask why, ask for an example, present a counter-position, ask what follows from it.
- Do not lecture and do not answer your own questions. Do not praise every answer. Stay friendly and neutral.
- If the student talks for more than about a minute and a half without letting you in, or starts reciting a prepared monologue, politely interrupt and turn it back into a dialogue.
- If the student does not understand a question, rephrase it more simply.
- Never mention grades or points.
- Messages that start with "COACH:" come from the training app, not from the student. Follow them immediately and briefly, then continue.
- If you receive "TIME_UP", thank the student in one sentence and end."""

    fun part2Examiner(task: ExamTask, part1Excerpt: String, courseTopics: String): String {
        val topics = task.part2Topics.ifEmpty { listOf("a topic area from another term of the course") }
        val topicA = topics.first()
        val topicB = topics.getOrElse(1) { topicA }
        val course = if (courseTopics.isBlank()) "" else "\nThe student's course covered: $courseTopics. Refer to this where it fits."
        return """
You are an experienced English teacher examining a student in the oral Abitur exam in North Rhine-Westphalia, Germany (English, basic course, 4th exam subject). This is PART 2, the exam conversation, about 10 minutes.

In part 1 the student gave a prepared presentation on new material.
Material: "${task.title}" (topic area: ${task.topicArea}).
What the student said in part 1 (excerpt): $part1Excerpt

Your plan for part 2:
1. Thank the student in one short sentence and lead over from the presentation to a broader question.
2. Talk about this topic area: $topicA
3. After about four to five minutes, switch to: $topicB. Make the switch explicit and connect it to what was said before.$course
$EXAMINER_RULES
- Never correct the student's English and never comment on mistakes. This is an exam.
""".trim()
    }

    fun trainingExaminer(topicArea: String, mode: FeedbackMode, focus: List<String>, courseTopics: String): String {
        val coach =
            if (mode == FeedbackMode.COACH) {
                "- When you get a COACH message, tell the student kindly and briefly what they said and the correct form, ask them to say the corrected sentence once, then continue."
            } else {
                "- Never correct the student's English yourself; the app handles feedback."
            }
        val focusLine =
            if (focus.isEmpty()) {
                ""
            } else {
                "\nThe student wants to practise these weak points: ${focus.joinToString(", ")}. Ask questions that naturally require these structures."
            }
        val course = if (courseTopics.isBlank()) "" else "\nThe student's course covered: $courseTopics. Refer to this where it fits."
        return """
You are an experienced English teacher in North Rhine-Westphalia, Germany, running a speaking practice that prepares a student for the oral Abitur exam in English (basic course). The conversation should feel like part 2 of the exam: an exam conversation about larger contexts.

Topic area: $topicArea$focusLine$course
Start with one short greeting and an open question about the topic. Move on to related aspects and, after a while, connect the topic to another area of the course (for example globalisation, the media, identity, the UK, the USA or Nigeria).
$EXAMINER_RULES
$coach
""".trim()
    }

    fun coachMessage(error: LiveError): String =
        "COACH: The student just said \"${error.quote}\". Correct form: \"${error.correction}\". " +
            "Tell them briefly and kindly, ask them to say the corrected sentence once, then continue."

    val IMPULSES =
        listOf(
            "Take your time. Perhaps you could go on with the next task.",
            "Would you like to say a bit more about the text?",
            "Maybe you could look at the next point on your notes.",
        )

    // ---------- Live error check ----------

    val CHECK_SYSTEM =
        """
You check a German student's spoken English during speaking practice for the oral Abitur exam.
The input is a verbatim transcript of what the student just said. Fillers (um, uh, er), repetitions and false starts are normal in speech and are NOT errors. If the student corrects themselves, judge only the final version.
Report only clear errors: tenses, verb forms, subject-verb agreement, prepositions, articles, singular/plural, pronouns, word order, word choice and collocations, German-English false friends, and statements about the topic that are factually wrong.
Do not report style preferences, acceptable British or American variants, or punctuation.
For each error:
- quote: the exact words from the input, copied character for character, at most 12 words
- correction: the corrected words in English
- explanation: a very short reason in German, at most 15 words
- category: one of ${Knowledge.ERROR_CATEGORIES.keys.joinToString(", ")}
- severity: "major" for a basic error a top student would not make or one that makes the meaning unclear, "minor" for a small slip, "content" for a factually wrong statement
Return an empty list if there are no errors.
        """.trimIndent()

    fun checkRequest(utterance: String, context: String): String = "Context: $context\nStudent said: $utterance"

    fun checkSchema(): JSONObject = obj("errors" to arr(errorSchema()))

    private fun errorSchema(): JSONObject =
        obj(
            "quote" to str(),
            "correction" to str(),
            "explanation" to str(),
            "category" to str(Knowledge.ERROR_CATEGORIES.keys.toList()),
            "severity" to str(listOf("minor", "major", "content")),
        )

    fun parseErrors(array: JSONArray?): List<LiveError> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { LiveError.fromJson(it) }
            .filter { it.quote.isNotBlank() && it.correction.isNotBlank() }
    }

    // ---------- Exam material ----------

    val MATERIAL_SYSTEM =
        """
Du erstellst Prüfungsmaterial für den ersten Teil der mündlichen Abiturprüfung Englisch in NRW (Grundkurs, 4. Abiturfach).
${Knowledge.EXAM_FACTS}
Das Material muss neu sein: Schreibe einen eigenen Originaltext, zitiere keine echten, urheberrechtlich geschützten Texte.
Text und Aufgaben sind auf Englisch, Niveau B2/C1. Die Aufgaben folgen den NRW-Operatoren: ${Knowledge.OPERATORS}
        """.trimIndent()

    fun materialRequest(topicArea: String, textType: String, courseTopics: String): String {
        val kind =
            if (textType == "Literarischer Text") {
                "an original excerpt from a short story or novel (a scene with characters, conflict and some dialogue)"
            } else {
                "an original non-fiction text such as a newspaper or online article, a blog post, a speech excerpt or a report"
            }
        val course = if (courseTopics.isBlank()) "" else "\nThe student's course covered: $courseTopics. You may connect to it."
        return """
Create the material for part 1 and the plan for part 2.
Topic area: $topicArea
Text type: $kind, 230 to 290 words, with a clear perspective or problem that invites discussion.$course
Return:
- title: the title of the text
- text_type: e.g. "newspaper article" or "extract from a short story"
- text: the full text
- source_note: in German, e.g. "Original-Übungstext (KI-generiert), Stil: Zeitungsartikel"
- tasks: exactly three tasks for a coherent 10-minute presentation: task 1 AFB I (outline/summarize/describe/point out), task 2 AFB II (analyse/examine/explain/characterize), task 3 AFB III (comment/discuss/assess/evaluate), worded like NRW exam tasks, e.g. "Outline the author's view on ...". Give operator and afb ("I", "II" or "III") separately.
- part2_topics: two OTHER topic areas for the exam conversation, each as one line in English with a focus question, e.g. "Nigeria – How does the colonial past still shape Nigerian society today?"
""".trim()
    }

    fun materialSchema(): JSONObject =
        obj(
            "title" to str(),
            "text_type" to str(),
            "text" to str(),
            "source_note" to str(),
            "tasks" to arr(obj("operator" to str(), "afb" to str(listOf("I", "II", "III")), "instruction" to str())),
            "part2_topics" to arr(str()),
        )

    // ---------- Debrief ----------

    val ANALYSIS_SYSTEM =
        """
Du bist eine erfahrene Englischlehrkraft und Prüferin im NRW-Abitur und wertest eine Übungssitzung für die mündliche Prüfung im 4. Fach aus.
${Knowledge.EXAM_FACTS}
Arbeitsweise:
- Das Transkript ist wortgetreu. Füllwörter, Wiederholungen und Selbstkorrekturen sind normal; zähle sie nicht als Grammatikfehler, berücksichtige sie aber bei der Flüssigkeit.
- Wenn eine Audiodatei des Prüflings beiliegt, höre sie an: Bewerte Aussprache und Intonation daraus und nutze sie, um das Transkript zu prüfen.
- Bewerte ehrlich und streng wie im echten Abitur, nicht freundlicher. Vergib Punkte (0–15) getrennt für die inhaltliche Leistung und die Darstellungsleistung; die App rechnet sie 40:60 zusammen.
- Eine kurze Sitzung bewertest du nach dem, was da ist, und sagst das in der Begründung.
Ausgabe (Begründungen und Erklärungen auf Deutsch, verbesserte Sätze auf Englisch):
- inhalt / darstellung: Punkte und eine Begründung in 2–3 Sätzen im Stil einer Notenbegründung.
- strengths: 2–4 konkrete Stärken.
- levers: die 3 Änderungen, die am meisten Punkte bringen würden, konkret und umsetzbar.
- errors: alle relevanten sprachlichen und inhaltlichen Fehler. quote muss ein exaktes Zitat aus den Zeilen des Prüflings (STUDENT) sein. Kategorie aus: ${Knowledge.ERROR_CATEGORIES.keys.joinToString(", ")}.
- upgrades: 3–6 der wichtigsten Sätze oder Antworten des Prüflings, umformuliert auf 15-Punkte-Niveau (natürliches, idiomatisches C1-Englisch, gleiche Gedanken); why erklärt auf Deutsch, was besser ist.
- pronunciation: konkrete Wörter mit Ausspracheproblemen und ein Tipp (typisch für Deutsche: th, w/v, Auslautverhärtung, Wortbetonung). Leer lassen, wenn kein Audio vorliegt.
- content_feedback: was inhaltlich fehlte (Aufgabenbezug, Tiefe im Anforderungsbereich III, Beispiele, Verknüpfung von Themen).
- phrases: 5–8 nützliche englische Redemittel passend zum Thema und zu den Lücken, mit deutscher Bedeutung und einem Beispielsatz.
- student_transcript: nur ausfüllen, wenn das mitgelieferte Transkript des Prüflings leer ist und Audio vorliegt: dann das Gesagte wortgetreu aus dem Audio.
        """.trimIndent()

    fun analysisContext(record: SessionRecord): String {
        val sb = StringBuilder()
        sb.append("Modus: ").append(record.kind.label)
        if (record.kind == SessionKind.SIMULATION) sb.append(" (Teil 1 Vortrag, Teil 2 Prüfungsgespräch)")
        sb.append("\nThemenbereich: ").append(record.topicArea)
        sb.append("\nDauer: ").append(record.durationSec / 60).append(" Min. ").append(record.durationSec % 60).append(" Sek.")
        if (record.part1Sec > 0) sb.append(" (davon Teil 1: ").append(record.part1Sec / 60).append(" Min.)")
        record.task?.let { t ->
            sb.append("\n\nMaterial für Teil 1: \"").append(t.title).append("\" (").append(t.textType).append(")\n").append(t.text)
            sb.append("\nAufgaben:\n")
            t.tasks.forEachIndexed { i, task -> sb.append(i + 1).append(". [AFB ").append(task.afb).append("] ").append(task.instruction).append('\n') }
            sb.append("Themen für Teil 2: ").append(t.part2Topics.joinToString(" | "))
        }
        if (record.notes.isNotBlank()) sb.append("\n\nNotizen des Prüflings aus der Vorbereitung:\n").append(record.notes)
        sb.append("\n\nWortgetreues Transkript (STUDENT = Prüfling, EXAMINER = Prüfer):\n")
        if (record.utterances.isEmpty()) sb.append("(leer – bitte aus dem Audio rekonstruieren)\n")
        record.utterances.forEach { u ->
            val part = when (u.part) { 1 -> "[Teil 1] "; 2 -> "[Teil 2] "; else -> "" }
            sb.append(part).append(if (u.speaker == Speaker.STUDENT) "STUDENT: " else "EXAMINER: ").append(u.text).append('\n')
        }
        if (record.liveErrors.isNotEmpty()) {
            sb.append("\nWährend der Sitzung bereits markiert (zur Orientierung):\n")
            record.liveErrors.take(30).forEach { sb.append("- \"").append(it.quote).append("\" → \"").append(it.correction).append("\"\n") }
        }
        return sb.toString()
    }

    fun analysisSchema(): JSONObject =
        obj(
            "inhalt" to obj("points" to int(), "reason" to str()),
            "darstellung" to obj("points" to int(), "reason" to str()),
            "strengths" to arr(str()),
            "levers" to arr(str()),
            "errors" to arr(errorSchema()),
            "upgrades" to arr(obj("original" to str(), "upgraded" to str(), "why" to str())),
            "pronunciation" to arr(obj("word" to str(), "issue" to str(), "tip" to str())),
            "content_feedback" to arr(str()),
            "phrases" to arr(obj("phrase" to str(), "meaning_de" to str(), "example" to str())),
            "student_transcript" to str(),
            required = listOf("inhalt", "darstellung", "strengths", "levers", "errors", "upgrades", "pronunciation", "content_feedback", "phrases"),
        )

    /** Turns the model's answer into a Debrief and drops corrections that quote words the student never said. */
    fun parseDebrief(o: JSONObject, studentText: String, audioAnalysed: Boolean): Debrief {
        val reference = normalize(studentText + " " + o.optString("student_transcript", ""))
        val all = parseErrors(o.optJSONArray("errors"))
        val verified = if (reference.isBlank()) all else all.filter { isQuoted(it.quote, reference) }
        val inhalt = o.optJSONObject("inhalt")
        val darstellung = o.optJSONObject("darstellung")
        val inhaltPoints = (inhalt?.optInt("points", 0) ?: 0).coerceIn(0, 15)
        val darstellungPoints = (darstellung?.optInt("points", 0) ?: 0).coerceIn(0, 15)
        val total = (0.4 * inhaltPoints + 0.6 * darstellungPoints).roundToInt().coerceIn(0, 15)
        return Debrief(
            inhaltPoints = inhaltPoints,
            inhaltReason = inhalt?.optString("reason", "").orEmpty(),
            darstellungPoints = darstellungPoints,
            darstellungReason = darstellung?.optString("reason", "").orEmpty(),
            totalPoints = total,
            strengths = strings(o.optJSONArray("strengths")),
            levers = strings(o.optJSONArray("levers")),
            errors = verified,
            upgrades = objects(o.optJSONArray("upgrades")).map { Upgrade(it.optString("original"), it.optString("upgraded"), it.optString("why")) },
            pronunciation = objects(o.optJSONArray("pronunciation")).map { PronTip(it.optString("word"), it.optString("issue"), it.optString("tip")) },
            contentFeedback = strings(o.optJSONArray("content_feedback")),
            phrases = objects(o.optJSONArray("phrases")).map { Phrase(it.optString("phrase"), it.optString("meaning_de"), it.optString("example")) },
            droppedUnverified = all.size - verified.size,
            audioAnalysed = audioAnalysed,
        )
    }

    fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** A quote counts as real if it appears verbatim, or if nearly all of its words were said. */
    fun isQuoted(quote: String, normalizedReference: String): Boolean {
        val q = normalize(quote)
        if (q.isBlank()) return false
        if (normalizedReference.contains(q)) return true
        val words = q.split(" ").filter { it.length > 1 }
        if (words.isEmpty()) return false
        val said = normalizedReference.split(" ").toHashSet()
        return words.count { it in said } >= (words.size * 0.85)
    }

    // ---------- small schema helpers ----------

    private fun str(enum: List<String>? = null): JSONObject {
        val o = JSONObject().put("type", "STRING")
        if (enum != null) o.put("enum", JSONArray(enum))
        return o
    }

    private fun int(): JSONObject = JSONObject().put("type", "INTEGER")

    private fun arr(items: JSONObject): JSONObject = JSONObject().put("type", "ARRAY").put("items", items)

    private fun obj(vararg properties: Pair<String, JSONObject>, required: List<String>? = null): JSONObject {
        val props = JSONObject()
        properties.forEach { (key, value) -> props.put(key, value) }
        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", props)
            .put("required", JSONArray(required ?: properties.map { it.first }))
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    }

    private fun objects(array: JSONArray?): List<JSONObject> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }
}
