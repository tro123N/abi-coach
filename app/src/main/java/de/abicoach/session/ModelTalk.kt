package de.abicoach.session

import de.abicoach.AbiCoachApp
import de.abicoach.ai.arr
import de.abicoach.ai.obj
import de.abicoach.ai.str
import de.abicoach.data.Knowledge
import de.abicoach.data.ModelPart
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import de.abicoach.data.objects
import de.abicoach.data.strings

/**
 * "So klingt deine Leistung auf 15 Punkte": the student's own presentation (or his answers in the conversation),
 * rewritten at the top level – his ideas and structure kept, language and content raised, missing points added.
 * A worked example of exactly his performance is one of the strongest ways to learn what 15 points sound like.
 */
object ModelTalk {
    private val SYSTEM =
        """
Du bist eine sehr gute Englischlehrerin im NRW-Abitur (mündliche Prüfung, Grundkurs). Du zeigst einem Schüler, wie SEINE Leistung auf 15-Punkte-Niveau klingen würde: Behalte seine Gedanken, Beispiele und seinen Aufbau, hebe Sprache und Inhalt auf das höchste Niveau und ergänze, was für 15 Punkte fehlte (Punkte des Erwartungshorizonts, Textbelege, Bezüge zum Unterricht, Verknüpfungen von Themenfeldern, eigene begründete Position im AFB III).
Regeln:
- Gesprochene Sprache wie ein frei gehaltener Vortrag, kein Aufsatz: klare Signposts („To begin with …“, „Moving on to the second task …“, „To sum up …“), natürliche Satzlänge, C1-Niveau, britisches Englisch, keine Füllwörter.
- Bei einem Vortrag (Teil 1): genau ein Teil pro bearbeiteter Aufgabe, title wie „Aufgabe 1 – Summary“, „Aufgabe 2 – Analysis“, „Aufgabe 3 – Comment“. Länge wie im Abitur: Aufgabe 1 etwa 220–280 Wörter, Aufgabe 2 etwa 380–480, Aufgabe 3 etwa 300–380.
- Bei einem Gespräch (Teil 2 oder Training): 3 Antworten auf die wichtigsten Fragen des Prüfers (title = die Frage, auf Englisch), je 80–140 Wörter, mit Begründung, Beispiel und – wo es passt – einem Bezug zu einem anderen Themenfeld.
- notes: zu jedem Teil 2–4 kurze Stichpunkte auf Deutsch, was hier konkret besser ist als beim Schüler (z. B. „Zitat mit Zeilenangabe als Beleg“, „present perfect bei der Entwicklung bis heute“).
        """.trimIndent()

    private val schema = obj("parts" to arr(obj("title" to str(), "text" to str(), "notes" to arr(str()))))

    suspend fun make(app: AbiCoachApp, record: SessionRecord): List<ModelPart> {
        val d = record.debrief
        val presentation = record.kind.hasPart1 && record.utterances.any { it.part == 1 && it.speaker == Speaker.STUDENT }
        val sb = StringBuilder()
        sb.append("Art: ").append(if (presentation) "Vortrag (Teil 1)" else "Gespräch").append(" · ").append(record.kind.label)
        if (record.focusTask in 1..3) sb.append(" – nur Aufgabe ").append(record.focusTask)
        sb.append("\nThemenfeld: ").append(Knowledge.topic(record.topicId)?.english ?: "gemischt").append('\n')
        record.task?.let { t ->
            sb.append("\nMaterial: \"").append(t.title).append("\"\n").append(t.text.take(4_000)).append("\n\nAufgaben mit Erwartungshorizont:\n")
            t.tasks.forEachIndexed { i, task ->
                if (record.focusTask == 0 || record.focusTask == i + 1) {
                    sb.append(i + 1).append(". [AFB ").append(task.afb).append("] ").append(task.instruction).append('\n')
                    task.expectations.forEach { sb.append("   – ").append(it).append('\n') }
                }
            }
            if (t.solution.isNotBlank()) sb.append("Lösungsvorschlag aus dem Original:\n").append(t.solution.take(2_000)).append('\n')
        }
        if (d != null) {
            if (d.levers.isNotEmpty()) sb.append("\nWas laut Auswertung am meisten Punkte bringen würde: ").append(d.levers.joinToString(" | ")).append('\n')
            if (d.contentFeedback.isNotEmpty()) sb.append("Inhaltlich fehlte: ").append(d.contentFeedback.joinToString(" | ")).append('\n')
            if (d.courseLinks.isNotEmpty()) sb.append("Aus seinem Kurs hätte gepasst: ").append(d.courseLinks.joinToString(" | ")).append('\n')
        }
        app.dossiers.get(record.topicId)?.let { sb.append('\n').append(it.compact(2_000)).append('\n') }
        sb.append("\nWas der Schüler gesagt hat (STUDENT) und der Prüfer (EXAMINER):\n")
        val lines = record.utterances.filter { !presentation || it.part == 1 }.joinToString("\n") { u ->
            (if (u.speaker == Speaker.STUDENT) "STUDENT: " else "EXAMINER: ") + u.text
        }
        sb.append(lines.take(12_000))
        val answer = app.ai.brainJson(SYSTEM, sb.toString(), schema, thinking = true)
        val parts =
            answer.json.optJSONArray("parts").objects().map { o -> ModelPart(o.optString("title").trim(), o.optString("text").trim(), o.optJSONArray("notes").strings()) }
                .filter { it.text.isNotBlank() }
        if (parts.isEmpty()) throw IllegalStateException("Die Musterfassung war leer.")
        return parts
    }
}
