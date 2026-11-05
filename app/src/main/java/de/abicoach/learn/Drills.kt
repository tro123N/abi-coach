package de.abicoach.learn

import de.abicoach.AbiCoachApp
import de.abicoach.R
import de.abicoach.ai.GeminiRest
import de.abicoach.ai.arr
import de.abicoach.ai.bool
import de.abicoach.ai.enumOf
import de.abicoach.ai.int
import de.abicoach.ai.obj
import de.abicoach.ai.str
import de.abicoach.data.Knowledge
import de.abicoach.data.objects
import java.util.UUID

/** The kinds of short speaking exercises. */
enum class DrillType(val key: String, val label: String, val icon: Int) {
    SAY_RIGHT("say_right", "Richtig sagen", R.drawable.spellcheck_24dp),
    TRANSFORM("transform", "Umformen", R.drawable.sync_24dp),
    COMPLETE("complete", "Satz vollenden", R.drawable.edit_note_24dp),
    USE_PHRASE("use_phrase", "Redemittel einsetzen", R.drawable.translate_24dp),
    ANSWER("answer", "Kurz antworten", R.drawable.forum_24dp),
    ;

    companion object {
        fun byKey(key: String): DrillType = entries.firstOrNull { it.key == key.trim().lowercase() } ?: SAY_RIGHT
    }
}

/** One speaking exercise of 10–30 seconds that targets one of the student's error patterns. */
data class Drill(
    val id: String,
    val type: DrillType,
    val patternKey: String,
    /** What to do, in German ("Sag es richtig – achte auf die 3. Person -s."). */
    val instruction: String,
    /** What the student sees (English): the faulty sentence, a sentence to change, a question … */
    val cue: String,
    /** A model answer (English). */
    val target: String,
    /** The words that matter (English), e.g. "describes". */
    val focus: String,
    val topic: Int = -1,
)

data class DrillOutcome(val drill: Drill, val ok: Boolean, val heard: String, val tip: String)

/**
 * Writes speaking exercises from the student's own error patterns, with sentences about his own course topics:
 * deliberate practice of exactly the mistakes that cost him points, combined with exam content.
 */
object DrillMaker {
    private val SYSTEM =
        """
Du erstellst kurze Sprech-Übungen (je 10–30 Sekunden) für einen deutschen Schüler, der sich auf die mündliche Englisch-Abiturprüfung in NRW vorbereitet (Grundkurs, Ziel 15 Punkte). Ziel: seine typischen Fehler dauerhaft abstellen und dabei Prüfungsinhalte wiederholen.
Du bekommst seine Fehlermuster (Schlüssel, Regel, Beispiele falsch → richtig) und Inhalte aus seinem Kurs.
Erstelle genau die gewünschte Anzahl Übungen. Jede Übung trainiert genau ein Fehlermuster (pattern = der Schlüssel aus der Liste, unverändert); wichtigere Muster (weiter oben) bekommen mehr Übungen. Mische die Arten:
- say_right: cue ist ein Satz MIT genau diesem Fehler, so wie er ihn sagen würde; target ist der richtige Satz. Er soll ihn richtig sagen.
- transform: cue ist ein richtiger englischer Satz plus in Klammern, was er ändern soll (z. B. „(start with: Since 2015 …)“ oder „(report it: The author says that …)“); target ist der umgeformte Satz.
- complete: cue ist ein Satzanfang, der genau diese Struktur verlangt (z. B. „Over the last decade, Lagos …“); target ist ein vollständiger Satz.
- use_phrase: cue nennt ein Redemittel für Analyse oder Kommentar und ein Thema; target ist ein Beispielsatz mit dem Redemittel.
- answer: cue ist eine kurze Prüferfrage zu seinem Kursthema, deren natürliche Antwort die Struktur braucht; target ist eine Musterantwort in 1–2 Sätzen.
Regeln:
- Sätze über seine Kursthemen und Abiturthemen (nutze die Kursinhalte), Niveau B2/C1, natürliches Englisch, kein Kinderbuch-Englisch.
- instruction auf Deutsch, höchstens 12 Wörter, klar und konkret (z. B. „Sag es richtig – achte auf die 3. Person -s.“).
- focus: die 1–4 englischen Wörter, auf die es ankommt (z. B. „has grown“).
- topic: Nummer des Themenfelds oder -1.
        """.trimIndent()

    private val schema =
        obj(
            "drills" to
                arr(
                    obj(
                        "type" to enumOf(DrillType.entries.map { it.key }),
                        "pattern" to str(),
                        "instruction" to str(),
                        "cue" to str(),
                        "target" to str(),
                        "focus" to str(),
                        "topic" to int(),
                    ),
                ),
        )

    /** Asks the AI for [count] exercises; without a key or on failure, simple "say it right" exercises from the examples. */
    suspend fun make(app: AbiCoachApp, patterns: List<ErrorPattern>, count: Int = 8): List<Drill> {
        if (patterns.isEmpty()) return emptyList()
        if (!app.ai.ready) return local(patterns, count)
        val sb = StringBuilder()
        sb.append("Anzahl Übungen: ").append(count).append("\n\nFehlermuster (wichtigste zuerst):\n")
        patterns.forEach { p ->
            sb.append("- Schlüssel: ").append(p.key).append(" | Regel: ").append(p.title).append(" | Kategorie: ").append(p.categoryLabel)
            val ex = p.examples.take(3).joinToString("; ") { "\"${it.wrong}\" → \"${it.right}\"" }
            if (ex.isNotBlank()) sb.append(" | Beispiele: ").append(ex)
            // What the tutor of the Fehler-Werkstatt found still hard: the exercises go exactly there.
            app.workshops.stillHard(p.key).takeIf { it.isNotBlank() }?.let { sb.append(" | Fällt noch schwer: ").append(it) }
            sb.append('\n')
        }
        val topics = app.course.coveredTopics().take(4).map { it.first }
        if (topics.isNotEmpty()) {
            sb.append("\nKursinhalte des Schülers (für die Sätze):\n")
            topics.forEach { t ->
                val dossier = app.dossiers.get(t)
                val facts = dossier?.keyPoints?.take(5) ?: app.course.factsFor(t, 5)
                sb.append(t).append(" – ").append(Knowledge.topic(t)?.english ?: "").append(": ").append(facts.joinToString(" | ").take(700)).append('\n')
            }
        } else if (app.settings.courseTopics.isNotBlank()) {
            sb.append("\nThemen aus dem Unterricht: ").append(app.settings.courseTopics).append('\n')
        }
        sb.append("\nThemenfelder:\n").append(Knowledge.TOPICS.joinToString("\n") { "${it.id}: ${it.english}" })
        return runCatching {
            val answer = app.ai.brainJson(SYSTEM, sb.toString(), schema, thinking = false)
            val keys = patterns.map { it.key }.toSet()
            answer.json.optJSONArray("drills").objects().mapNotNull { o ->
                val key = o.optString("pattern").trim().takeIf { it in keys } ?: patterns.first().key
                val cue = o.optString("cue").trim()
                val target = o.optString("target").trim()
                if (cue.isBlank() || target.isBlank()) return@mapNotNull null
                Drill(
                    id = UUID.randomUUID().toString(),
                    type = DrillType.byKey(o.optString("type")),
                    patternKey = key,
                    instruction = o.optString("instruction").trim().ifBlank { "Sag es richtig." },
                    cue = cue,
                    target = target,
                    focus = o.optString("focus").trim(),
                    topic = o.optInt("topic", -1).takeIf { it in 0..13 } ?: -1,
                )
            }.take(count)
        }.getOrNull()?.takeIf { it.size >= 2 } ?: local(patterns, count)
    }

    /** "Say it right" from the student's own examples – works without the AI. */
    fun local(patterns: List<ErrorPattern>, count: Int): List<Drill> {
        val out = ArrayList<Drill>()
        var round = 0
        while (out.size < count && round < 3) {
            patterns.forEach { p ->
                val example = p.examples.getOrNull(round) ?: return@forEach
                if (out.size >= count) return@forEach
                out +=
                    Drill(
                        id = UUID.randomUUID().toString(),
                        type = DrillType.SAY_RIGHT,
                        patternKey = p.key,
                        instruction = "Sag es richtig – " + p.title + ".",
                        cue = example.wrong,
                        target = example.right,
                        focus = example.right,
                    )
            }
            round++
        }
        return out
    }
}

/** Gemini listens to the answer of a speaking exercise and says at once whether it was right. */
object DrillCheck {
    data class Result(val ok: Boolean, val heard: String, val tip: String, val by: String)

    private val SYSTEM =
        """
Du bist Englischlehrerin. Ein deutscher Schüler macht eine kurze Sprech-Übung für die mündliche Abiturprüfung. Du bekommst die Aufgabe, die Vorgabe, eine Musterlösung, die entscheidenden Wörter und seine Aufnahme.
Entscheide:
- ok: true, wenn er die Aufgabe gelöst hat – vor allem die entscheidende Stelle ist richtig – und gut verständlich ist. Andere richtige Formulierungen sind in Ordnung, kleine Aussprache-Abweichungen zählen nicht. false, wenn der trainierte Fehler wieder vorkommt, ein neuer klarer Grammatikfehler dazukommt, die Aufnahme still ist oder etwas ganz anderes gesagt wird.
- heard: was du verstanden hast, wortgetreu (mit seinen Fehlern, nicht verbessert)
- tip: ein kurzer Satz auf Deutsch – bei ok ein konkretes Lob, sonst genau, was noch falsch ist und wie es richtig heißt.
        """.trimIndent()

    private val schema = obj("ok" to bool(), "heard" to str(), "tip" to str())

    suspend fun check(app: AbiCoachApp, drill: Drill, wav: ByteArray): Result {
        val parts =
            listOf(
                GeminiRest.textPart(
                    "Übungsart: ${drill.type.label}\nAufgabe: ${drill.instruction}\nVorgabe: \"${drill.cue}\"\n" +
                        "Musterlösung: \"${drill.target}\"\nEntscheidend: \"${drill.focus}\"\nDie Aufnahme folgt.",
                ),
                GeminiRest.inlinePart("audio/wav", wav),
            )
        val answer = app.ai.quickListenJson(SYSTEM, parts, schema)
        val o = answer.json
        return Result(o.optBoolean("ok"), o.optString("heard"), o.optString("tip"), answer.by)
    }
}
