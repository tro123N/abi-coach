package de.abicoach.session

import de.abicoach.ai.Sch
import de.abicoach.ai.arr
import de.abicoach.ai.enumOf
import de.abicoach.ai.int
import de.abicoach.ai.obj
import de.abicoach.ai.str
import de.abicoach.data.Debrief
import de.abicoach.data.ExamTask
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.LiveError
import de.abicoach.data.Phrase
import de.abicoach.data.PronTip
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import de.abicoach.data.TaskReview
import de.abicoach.data.Upgrade
import de.abicoach.data.objects
import de.abicoach.data.strings
import de.abicoach.profile.Skill
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** All prompts and response schemas in one place. */
object Prompts {
    // ---------- Examiner ----------

    private const val EXAMINER_RULES = """
Rules:
- Speak British English at a natural, calm pace. Keep each of your turns short: one to three sentences, usually one open question.
- Ask about larger contexts and connections, not isolated facts. Use questions that make the student explain, compare, evaluate, take a stand and give examples.
- Follow up on what the student says: ask why, ask for an example, present a counter-position, ask what follows from it.
- Do not lecture and do not answer your own questions. Do not praise every answer. Stay friendly and neutral.
- If the student talks for more than about a minute and a half without letting you in, or recites a prepared monologue, politely interrupt and turn it back into a dialogue – this is what real examiners do.
- If the student does not understand a question, rephrase it more simply.
- Never mention grades or points.
- Messages that start with "COACH:" come from the training app, not from the student. Follow them immediately and briefly, then continue.
- If you receive "TIME_UP", thank the student in one sentence and end."""

    private fun courseBlock(courseOverview: String, topicContext: String, teacherNotes: String): String {
        val sb = StringBuilder()
        if (courseOverview.isNotBlank()) sb.append("\n\n").append(courseOverview.trim())
        if (topicContext.isNotBlank()) {
            sb.append("\n\n").append(topicContext.trim())
            sb.append("\nUse this material: ask about texts, people, examples and positions the class actually dealt with (\"In class you read …\"), and expect the student to use them.")
        }
        if (teacherNotes.isNotBlank()) sb.append("\n\n").append(teacherNotes.trim())
        return sb.toString()
    }

    fun part2Examiner(task: ExamTask, part1Excerpt: String, courseOverview: String, topicContext: String, teacherNotes: String, minutes: Int): String {
        val topics = task.part2.ifEmpty { emptyList() }
        val topicA = topics.getOrNull(0)?.let { "${Knowledge.topic(it.topicId)?.english ?: "another topic area"} – ${it.question}" } ?: "a topic area from another term of the course"
        val topicB = topics.getOrNull(1)?.let { "${Knowledge.topic(it.topicId)?.english ?: "another topic area"} – ${it.question}" } ?: topicA
        val facts = topics.flatMap { it.courseFacts }.take(10)
        val factLine = if (facts.isEmpty()) "" else "\nWhat the course covered on these topics: " + facts.joinToString(" | ")
        return """
You are an experienced English teacher examining a student in the oral Abitur exam in North Rhine-Westphalia, Germany (English, basic course, 4th exam subject). This is PART 2, the exam conversation, about $minutes minutes. You start speaking.

In part 1 the student gave a prepared presentation on new material.
Material: "${task.title}" (topic field: ${Knowledge.topic(task.topicId)?.english ?: "mixed"}).
What the student said in part 1 (excerpt): $part1Excerpt

Your plan for part 2:
1. Thank the student in one short sentence and lead over from the presentation to a broader question.
2. Talk about: $topicA
3. After about half of the time, switch to: $topicB. Make the switch explicit and connect it to what was said before.$factLine${courseBlock(courseOverview, topicContext, teacherNotes)}
$EXAMINER_RULES
- Never correct the student's English and never comment on mistakes. This is an exam.
""".trim()
    }

    fun trainingExaminer(
        topicId: Int,
        mode: FeedbackMode,
        focus: List<String>,
        courseOverview: String,
        topicContext: String,
        teacherNotes: String,
        part2Style: Boolean,
        extra: String = "",
    ): String {
        val coach =
            if (mode == FeedbackMode.COACH) {
                "- When you get a COACH message, interrupt the student kindly and briefly: say what they said and the correct form, ask them to say the corrected sentence once, then continue."
            } else {
                "- Never correct the student's English yourself; the app handles feedback."
            }
        val focusLine =
            if (focus.isEmpty()) "" else "\nThe student wants to practise these weak points: ${focus.joinToString(", ")}. Ask questions that naturally require these structures."
        val topic = Knowledge.topic(topicId)?.english ?: "topics from the whole course"
        val style =
            if (part2Style) {
                "The conversation should be exactly like part 2 of the exam: start with an open question about the topic field, follow up, and after a while connect it to another topic field of the course."
            } else {
                "This is a relaxed speaking practice: start with an open question about the topic, move on to related aspects and, after a while, connect the topic to another area of the course."
            }
        return """
You are an experienced English teacher in North Rhine-Westphalia, Germany, running a speaking practice that prepares a student for the oral Abitur exam in English (basic course). You start speaking with one short greeting and your first question.

Topic field: $topic$focusLine
$style${if (extra.isNotBlank()) "\nSpecial focus of this exercise: $extra" else ""}${courseBlock(courseOverview, topicContext, teacherNotes)}
$EXAMINER_RULES
$coach
""".trim()
    }

    const val KICKOFF_PART2 = "START: Begin part 2 now."
    const val KICKOFF_TRAINING = "START: Greet the student in one short sentence and ask your first open question."

    fun coachMessage(error: LiveError): String =
        "COACH: The student just said \"${error.quote}\". Correct form: \"${error.correction}\". " +
            "Interrupt now, tell them briefly and kindly, ask them to say the corrected sentence once, then continue."

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
Do not report style preferences, acceptable British or American variants, or punctuation. When in doubt, do not report.
For each error:
- quote: the exact words from the input, copied character for character, at most 12 words
- correction: the corrected words in English
- explanation: a very short reason in German, at most 15 words
- category: one of ${Knowledge.ERROR_CATEGORIES.keys.joinToString(", ")}
- severity: "major" for a basic error a top student would not make or one that makes the meaning unclear, "minor" for a small slip, "content" for a factually wrong statement
Return an empty list if there are no errors.
        """.trimIndent()

    fun checkRequest(utterance: String, context: String): String = "Context: $context\nStudent said: $utterance"

    private val errorSchema: Sch =
        obj(
            "quote" to str(),
            "correction" to str(),
            "explanation" to str(),
            "category" to enumOf(Knowledge.ERROR_CATEGORIES.keys.toList()),
            "severity" to enumOf(listOf("minor", "major", "content")),
        )

    val checkSchema: Sch = obj("errors" to arr(errorSchema))

    fun parseErrors(array: JSONArray?): List<LiveError> =
        array.objects().map { LiveError.fromJson(it) }
            .map { it.copy(category = it.category.uppercase().takeIf { c -> c in Knowledge.ERROR_CATEGORIES } ?: "OTHER", severity = it.severity.lowercase()) }
            .filter { it.quote.isNotBlank() && it.correction.isNotBlank() }

    // ---------- Exam material ----------

    val MATERIAL_SYSTEM =
        """
Du erstellst Prüfungsmaterial für den ersten Teil der mündlichen Abiturprüfung Englisch in NRW (Grundkurs, 4. Abiturfach).
${Knowledge.EXAM_FACTS}
Teil 1 besteht immer aus genau einem Text und genau drei Aufgaben in dieser Reihenfolge:
1. AFB I – meist zusammenfassen: "Summarise the text." oder "Outline the author's view on …" (auch point out, describe).
2. AFB II – analysieren/untersuchen, z. B. "Examine how the author perceives …", "Analyse the means the author uses to …", "Analyse how the main character is presented." (auch explain, characterise).
3. AFB III – meist ein Kommentar: "Comment on …" (auch discuss, assess, evaluate), oft mit Bezug auf das im Unterricht Gelernte.
Keine Bilder, Cartoons, Statistiken oder Tabellen – die kommen in dieser Prüfung nicht vor.
Die Aufgaben sind so formuliert wie in echten NRW-Prüfungen, präzise und auf den Text bezogen. Zu jeder Aufgabe gehört ein Erwartungshorizont: 3–6 Punkte (auf Englisch, kurz), die ein 15-Punkte-Vortrag enthalten würde.
Text und Aufgaben sind auf Englisch, Niveau B2/C1.
        """.trimIndent()

    private fun textTypeLine(textType: Knowledge.TextType): String =
        when (textType) {
            Knowledge.TextType.SACHTEXT -> Knowledge.TextType.SACHTEXT.english
            Knowledge.TextType.LITERARISCH -> Knowledge.TextType.LITERARISCH.english
            Knowledge.TextType.GEMISCHT -> if (Math.random() < 0.65) Knowledge.TextType.SACHTEXT.english else Knowledge.TextType.LITERARISCH.english
        }

    private const val MATERIAL_RETURN = """
Return:
- title: the title of the text
- text_type: e.g. "newspaper article", "commentary", "speech" or "extract from a short story"
- text: the full text
- source_note: in German, where the text comes from (e.g. "Original-Übungstext, Stil: Zeitungsartikel")
- tasks: exactly three tasks in the order AFB I, II, III; give operator, afb ("I", "II" or "III"), the instruction and the expectations (3–6 short points in English that an excellent answer contains)
- part2_topics: two OTHER topic fields for the exam conversation (part 2), each with topic_id (number from the list) and a focus question in English. Prefer topic fields the student's course covered.
- course_links: up to 5 short points (in German) from the student's own course that fit this text and could be used in task 3 or part 2 (empty if no course material is given)"""

    private fun topicNumbers(): String = Knowledge.TOPICS.joinToString("\n") { "${it.id}: ${it.english}" }

    /** A new text that fits what the course covered (or just the topic field when there is no material). */
    fun materialRequest(topicId: Int, textType: Knowledge.TextType, courseOverview: String, topicContext: String, anchored: Boolean): String {
        val topic = Knowledge.topic(topicId)?.english ?: "any topic field"
        val anchor =
            if (anchored && topicContext.isNotBlank()) {
                "\nThe text must fit exactly what the student's course covered on this topic field (see below): pick up its themes, perspectives and vocabulary, but write a NEW text with a new angle, as a real exam would.\n\n$topicContext"
            } else {
                ""
            }
        return """
Create the material for part 1 and the plan for part 2.
Topic field: $topic
Text type: ${textTypeLine(textType)}, 230 to 300 words, original (do not quote real copyrighted texts), with a clear perspective or problem that invites analysis and comment.$anchor
${if (courseOverview.isNotBlank()) "\n$courseOverview" else ""}
Topic fields (numbers):
${topicNumbers()}
$MATERIAL_RETURN
""".trim()
    }

    /** Tasks for a text from the student's own material. */
    fun materialFromText(sourceTitle: String, pages: String, sourceText: String, topicId: Int, courseOverview: String): String =
        """
The following pages come from the student's own course material ("$sourceTitle", $pages). Choose ONE coherent passage of 220 to 380 words that works as exam material (a complete article, a scene or a self-contained part), copy it into "text" exactly as it is (only leave out task numbers, vocabulary notes and page furniture), and write the three tasks for it.
Topic field: ${Knowledge.topic(topicId)?.english ?: "as fits the text"}
${if (courseOverview.isNotBlank()) "\n$courseOverview" else ""}
Topic fields (numbers):
${topicNumbers()}
$MATERIAL_RETURN
For source_note write in German: "Aus deinem Material: $sourceTitle, $pages".

--- PAGES ---
$sourceText
""".trim()

    val materialSchema: Sch =
        obj(
            "title" to str(),
            "text_type" to str(),
            "text" to str(),
            "source_note" to str(),
            "tasks" to arr(obj("operator" to str(), "afb" to enumOf(listOf("I", "II", "III")), "instruction" to str(), "expectations" to arr(str()))),
            "part2_topics" to arr(obj("topic_id" to int(), "question" to str())),
            "course_links" to arr(str()),
        )

    // ---------- Debrief ----------

    private fun skillLines(): String = Skill.entries.joinToString("\n") { "  - ${it.key}: ${it.english}" }

    val ANALYSIS_SYSTEM =
        """
Du bist eine erfahrene Englischlehrkraft und Prüferin im NRW-Abitur und wertest eine Übungssitzung für die mündliche Prüfung im 4. Fach aus.
${Knowledge.EXAM_FACTS}
Arbeitsweise:
- Das Transkript ist wortgetreu. Füllwörter, Wiederholungen und Selbstkorrekturen sind normal; zähle sie nicht als Grammatikfehler, berücksichtige sie aber bei der Flüssigkeit.
- Wenn eine Audiodatei des Prüflings beiliegt, höre sie an: Bewerte Aussprache, Intonation und Flüssigkeit daraus und prüfe damit das Transkript. Liegt stattdessen eine Audio-Auswertung als Text bei, übernimm deren Befunde.
- Bewerte ehrlich und streng wie im echten Abitur, nicht freundlicher. Vergib Punkte (0–15) getrennt für die inhaltliche Leistung und die Darstellungsleistung; die App rechnet sie 40:60 zusammen.
- Nutze den Erwartungshorizont der Aufgaben und das Kursmaterial des Schülers: Was davon hat er genutzt, was hätte gepasst?
- Eine kurze Sitzung bewertest du nach dem, was da ist, und sagst das in der Begründung.
Ausgabe (Begründungen und Erklärungen auf Deutsch, verbesserte Sätze auf Englisch):
- inhalt / darstellung: Punkte und eine Begründung in 2–3 Sätzen im Stil einer Notenbegründung.
- skills: für jede Kompetenz 0–15 Punkte, -1 wenn sie in dieser Sitzung nicht beurteilbar war (z. B. Analyse in einem freien Gespräch):
${skillLines()}
- task_reviews: nur bei einem Vortrag (Teil 1): für jede bearbeitete Aufgabe Nummer, Punkte (0–15), covered (welche Punkte des Erwartungshorizonts vorkamen), missing (was fehlte), tip (ein konkreter Tipp). Sonst leer.
- strengths: 2–4 konkrete Stärken.
- levers: die 3 Änderungen, die am meisten Punkte bringen würden, konkret und umsetzbar.
- errors: alle relevanten sprachlichen und inhaltlichen Fehler. quote muss ein exaktes Zitat aus den Zeilen des Prüflings (STUDENT) sein. Kategorie aus: ${Knowledge.ERROR_CATEGORIES.keys.joinToString(", ")}.
- upgrades: 3–6 der wichtigsten Sätze oder Antworten des Prüflings, umformuliert auf 15-Punkte-Niveau (natürliches, idiomatisches C1-Englisch, gleiche Gedanken); why erklärt auf Deutsch, was besser ist.
- pronunciation: konkrete Wörter mit Ausspracheproblemen und ein Tipp (typisch für Deutsche: th, w/v, Auslautverhärtung, Wortbetonung). Leer, wenn kein Audio und keine Audio-Auswertung vorliegt.
- content_feedback: was inhaltlich fehlte (Aufgabenbezug, Tiefe im Anforderungsbereich III, Beispiele, Verknüpfung von Themen).
- course_links: bis zu 4 Inhalte aus dem eigenen Kursmaterial des Schülers, die gepasst hätten, mit Fundstelle in Klammern (leer, wenn kein Material vorliegt).
- phrases: 5–8 nützliche englische Redemittel passend zum Thema und zu den Lücken, mit deutscher Bedeutung und einem Beispielsatz.
- student_transcript: nur ausfüllen, wenn das mitgelieferte Transkript des Prüflings leer ist und Audio vorliegt: dann das Gesagte wortgetreu aus dem Audio. Sonst leer.
        """.trimIndent()

    fun analysisContext(record: SessionRecord, courseContext: String, teacherNotes: String, listening: String?, profileNote: String): String {
        val sb = StringBuilder()
        sb.append("Modus: ").append(record.kind.label)
        if (record.kind.hasPart1 && record.kind.hasPart2) sb.append(" (Teil 1 Vortrag, Teil 2 Prüfungsgespräch)")
        if (record.focusTask in 1..3) sb.append(" – nur Aufgabe ").append(record.focusTask)
        sb.append("\nThemenfeld: ").append(Knowledge.topic(record.topicId)?.german ?: "gemischt")
        sb.append("\nDauer: ").append(record.durationSec / 60).append(" Min. ").append(record.durationSec % 60).append(" Sek.")
        if (record.part1Sec > 0) sb.append(" (davon Teil 1: ").append(record.part1Sec / 60).append(" Min. ").append(record.part1Sec % 60).append(" Sek.)")
        record.task?.let { t ->
            sb.append("\n\nMaterial für Teil 1: \"").append(t.title).append("\" (").append(t.textType).append(", ").append(t.wordCount).append(" Wörter)\n").append(t.text)
            sb.append("\n\nAufgaben mit Erwartungshorizont:\n")
            t.tasks.forEachIndexed { i, task ->
                if (record.focusTask == 0 || record.focusTask == i + 1) {
                    sb.append(i + 1).append(". [AFB ").append(task.afb).append("] ").append(task.instruction).append('\n')
                    task.expectations.forEach { sb.append("   – ").append(it).append('\n') }
                }
            }
            if (record.kind.hasPart2) sb.append("Themen für Teil 2: ").append(t.part2.joinToString(" | ") { "${Knowledge.topicShort(it.topicId)}: ${it.question}" }).append('\n')
            if (t.courseLinks.isNotEmpty()) sb.append("Passende Inhalte aus dem Kurs: ").append(t.courseLinks.joinToString(" | ")).append('\n')
        }
        if (record.focusCategories.isNotEmpty()) sb.append("\nGeübte Schwachstellen: ").append(record.focusCategories.joinToString(", ") { Knowledge.categoryLabel(it) })
        if (record.notes.isNotBlank()) sb.append("\n\nNotizen des Prüflings aus der Vorbereitung:\n").append(record.notes)
        if (courseContext.isNotBlank()) sb.append("\n\n").append(courseContext)
        if (teacherNotes.isNotBlank()) sb.append("\n\n").append(teacherNotes)
        if (profileNote.isNotBlank()) sb.append("\n\nBisheriges Lernprofil: ").append(profileNote)
        if (listening != null) sb.append("\n\nAudio-Auswertung der Aufnahme (von Gemini):\n").append(listening)
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

    val analysisSchema: Sch =
        obj(
            "inhalt" to obj("points" to int(), "reason" to str()),
            "darstellung" to obj("points" to int(), "reason" to str()),
            "skills" to obj(*Skill.entries.map { it.key to int() }.toTypedArray()),
            "task_reviews" to arr(obj("number" to int(), "points" to int(), "covered" to arr(str()), "missing" to arr(str()), "tip" to str())),
            "strengths" to arr(str()),
            "levers" to arr(str()),
            "errors" to arr(errorSchema),
            "upgrades" to arr(obj("original" to str(), "upgraded" to str(), "why" to str())),
            "pronunciation" to arr(obj("word" to str(), "issue" to str(), "tip" to str())),
            "content_feedback" to arr(str()),
            "course_links" to arr(str()),
            "phrases" to arr(obj("phrase" to str(), "meaning_de" to str(), "example" to str())),
            "student_transcript" to str(),
        )

    // ---------- Listening to the recording (Gemini, before Claude grades) ----------

    val LISTEN_SYSTEM =
        """
Du hörst die Aufnahme eines deutschen Schülers, der für die mündliche Englisch-Abiturprüfung übt (nur sein Mikrofon). Beurteile streng wie eine Prüferin:
- pronunciation: konkrete Wörter mit Ausspracheproblemen und ein kurzer Tipp auf Deutsch (typisch für Deutsche: th, w/v, Auslautverhärtung, Wortbetonung, kurze/lange Vokale).
- pronunciation_points und fluency_points: 0–15 wie im Abitur.
- intonation: 1–2 Sätze auf Deutsch zu Satzmelodie und Betonung.
- fluency: 1–2 Sätze auf Deutsch zu Tempo, Pausen und Füllwörtern (mit grober Anzahl der Füllwörter).
- reading_aloud: true, wenn der Schüler offensichtlich abliest statt frei zu sprechen.
- transcript: wortgetreu, was der Schüler sagt (mit seinen Fehlern), nur wenn kein Transkript mitgeliefert wurde; sonst leer.
        """.trimIndent()

    val listenSchema: Sch =
        obj(
            "pronunciation" to arr(obj("word" to str(), "issue" to str(), "tip" to str())),
            "pronunciation_points" to int(),
            "fluency_points" to int(),
            "intonation" to str(),
            "fluency" to str(),
            "reading_aloud" to de.abicoach.ai.bool(),
            "transcript" to str(),
        )

    fun listeningSummary(o: JSONObject): String {
        val sb = StringBuilder()
        sb.append("Aussprache: ").append(o.optInt("pronunciation_points", -1)).append(" P., Flüssigkeit: ").append(o.optInt("fluency_points", -1)).append(" P.\n")
        sb.append("Intonation: ").append(o.optString("intonation")).append('\n')
        sb.append("Flüssigkeit: ").append(o.optString("fluency")).append('\n')
        if (o.optBoolean("reading_aloud")) sb.append("Achtung: liest offenbar ab.\n")
        o.optJSONArray("pronunciation").objects().forEach { sb.append("- ").append(it.optString("word")).append(": ").append(it.optString("issue")).append(" – ").append(it.optString("tip")).append('\n') }
        return sb.toString()
    }

    /** Turns the model's answer into a Debrief and drops corrections that quote words the student never said. */
    fun parseDebrief(o: JSONObject, studentText: String, audioAnalysed: Boolean, listening: JSONObject?, gradedBy: String, listenedBy: String): Debrief {
        val reference = normalize(studentText + " " + o.optString("student_transcript", "") + " " + (listening?.optString("transcript").orEmpty()))
        val all = parseErrors(o.optJSONArray("errors"))
        val verified = if (reference.isBlank()) all else all.filter { isQuoted(it.quote, reference) }
        val inhalt = o.optJSONObject("inhalt")
        val darstellung = o.optJSONObject("darstellung")
        val inhaltPoints = (inhalt?.optInt("points", 0) ?: 0).coerceIn(0, 15)
        val darstellungPoints = (darstellung?.optInt("points", 0) ?: 0).coerceIn(0, 15)
        val total = (0.4 * inhaltPoints + 0.6 * darstellungPoints).roundToInt().coerceIn(0, 15)
        val skillsJson = o.optJSONObject("skills") ?: JSONObject()
        val skills = mutableMapOf<Skill, Int>()
        Skill.entries.forEach { s ->
            val v = skillsJson.optInt(s.key, -1)
            if (v in 0..15) skills[s] = v
        }
        // The listening step knows pronunciation and fluency best.
        listening?.let { l ->
            l.optInt("pronunciation_points", -1).takeIf { it in 0..15 }?.let { skills[Skill.AUSSPRACHE] = it }
            l.optInt("fluency_points", -1).takeIf { it in 0..15 }?.let { v -> skills[Skill.FLUESSIGKEIT] = skills[Skill.FLUESSIGKEIT]?.let { (it + v + 1) / 2 } ?: v }
        }
        val ownTips = o.optJSONArray("pronunciation").objects().map { PronTip(it.optString("word"), it.optString("issue"), it.optString("tip")) }
        val listenTips = listening?.optJSONArray("pronunciation").objects().map { PronTip(it.optString("word"), it.optString("issue"), it.optString("tip")) }
        return Debrief(
            inhaltPoints = inhaltPoints,
            inhaltReason = inhalt?.optString("reason", "").orEmpty(),
            darstellungPoints = darstellungPoints,
            darstellungReason = darstellung?.optString("reason", "").orEmpty(),
            totalPoints = total,
            strengths = o.optJSONArray("strengths").strings(),
            levers = o.optJSONArray("levers").strings(),
            errors = verified,
            upgrades = o.optJSONArray("upgrades").objects().map { Upgrade(it.optString("original"), it.optString("upgraded"), it.optString("why")) },
            pronunciation = (listenTips + ownTips).filter { it.word.isNotBlank() }.distinctBy { it.word.lowercase() },
            contentFeedback = o.optJSONArray("content_feedback").strings(),
            phrases = o.optJSONArray("phrases").objects().map { Phrase(it.optString("phrase"), it.optString("meaning_de"), it.optString("example")) },
            droppedUnverified = all.size - verified.size,
            audioAnalysed = audioAnalysed,
            skills = skills,
            taskReviews = o.optJSONArray("task_reviews").objects().map { TaskReview.fromJson(it) }.filter { it.number in 1..3 },
            courseLinks = o.optJSONArray("course_links").strings(),
            gradedBy = gradedBy,
            listenedBy = listenedBy,
        )
    }

    fun normalize(text: String): String = text.lowercase().replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()

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

    // ---------- Questions about the material ----------

    val ASK_SYSTEM =
        """
Du bist der KI-Coach in einer App, die einen Schüler auf die mündliche Abiturprüfung Englisch in NRW (Grundkurs) vorbereitet.
Beantworte seine Frage auf Deutsch (englische Fachbegriffe und Beispielsätze auf Englisch), knapp und konkret, mit Markdown.
Stütze dich zuerst auf die mitgelieferten Auszüge aus seinem eigenen Kursmaterial und nenne die Fundstelle als [1], [2] … Wenn das Material die Frage nicht beantwortet, sag das und antworte aus deinem Wissen (dann ohne Fundstelle).
Wenn es zur Prüfung passt, schließe mit einem kurzen Tipp, wie er das im Vortrag oder im Gespräch nutzen kann.
        """.trimIndent()

    fun askRequest(question: String, passages: String, overview: String): String =
        buildString {
            if (overview.isNotBlank()) append(overview).append("\n\n")
            append("Auszüge aus dem Material:\n").append(passages.ifBlank { "(keine passenden Auszüge gefunden)" })
            append("\n\nFrage: ").append(question)
        }

    @Suppress("unused")
    private fun kindLabel(kind: SessionKind): String = kind.label
}
