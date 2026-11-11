package de.abicoach.learn

import android.content.Context
import de.abicoach.AbiCoachApp
import de.abicoach.ai.GeminiRest
import de.abicoach.ai.arr
import de.abicoach.ai.bool
import de.abicoach.ai.enumOf
import de.abicoach.ai.int
import de.abicoach.ai.obj
import de.abicoach.ai.str
import de.abicoach.data.Knowledge
import de.abicoach.data.objects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** "Erkennen": one sentence – is it right? */
data class RecognizeItem(val sentence: String, val correct: Boolean, val fix: String, val why: String)

/** "Frei sprechen": an examiner question whose natural answer needs the structure. */
data class TalkPrompt(val question: String, val hint: String)

/** One round in the Fehler-Werkstatt on one error pattern: understand, recognise, say it, use it freely. */
data class WorkshopLesson(
    val patternKey: String,
    val title: String,
    /** The rule in simple German, built on the student's own sentences. */
    val explanation: String,
    /** A memory hook ("Eselsbrücke"). */
    val hook: String,
    /** Wrong → right, his own sentences first. */
    val contrast: List<Pair<String, String>>,
    val recognize: List<RecognizeItem>,
    val drills: List<Drill>,
    val talk: List<TalkPrompt>,
    /** What the free answers are checked for (English, e.g. "present perfect with since/for"). */
    val focus: String,
    /** 1 = the basic rule, 2 = mixed cases, 3 = difficult cases and fast speaking. */
    val level: Int,
)

/** The result of one free answer. */
data class TalkCheck(val heard: String, val used: Boolean, val ok: Boolean, val tip: String, val better: String)

/** How one round went. */
data class WorkshopRun(val at: Long, val level: Int, val recognize: Pair<Int, Int>, val speak: Pair<Int, Int>, val talk: Pair<Int, Int>) {
    /** Share of right answers, with the free answers counting most (they show whether it is really fixed). */
    val score: Int
        get() {
            fun share(p: Pair<Int, Int>) = if (p.second == 0) null else p.first.toDouble() / p.second
            val parts = listOfNotNull(share(recognize)?.let { it to 1.0 }, share(speak)?.let { it to 1.5 }, share(talk)?.let { it to 2.0 })
            if (parts.isEmpty()) return 0
            return (100 * parts.sumOf { it.first * it.second } / parts.sumOf { it.second }).toInt()
        }

    fun toJson(): JSONObject =
        JSONObject().put("at", at).put("level", level).put("rOk", recognize.first).put("rAll", recognize.second)
            .put("sOk", speak.first).put("sAll", speak.second).put("tOk", talk.first).put("tAll", talk.second)

    companion object {
        fun fromJson(o: JSONObject) =
            WorkshopRun(o.optLong("at"), o.optInt("level", 1), o.optInt("rOk") to o.optInt("rAll"), o.optInt("sOk") to o.optInt("sAll"), o.optInt("tOk") to o.optInt("tAll"))
    }
}

/**
 * What the tutor has learnt about the student and one error pattern, kept from round to round: what he understood,
 * what is still hard, what helped him – the next round starts exactly there.
 */
data class TutorMemory(
    val patternKey: String,
    val level: Int = 1,
    val understood: String = "",
    val stillHard: String = "",
    val helps: String = "",
    val nextFocus: String = "",
    val runs: List<WorkshopRun> = emptyList(),
) {
    val rounds: Int get() = runs.size
    val lastAt: Long get() = runs.lastOrNull()?.at ?: 0L

    /** One block for the prompts. */
    fun promptBlock(): String {
        if (runs.isEmpty() && understood.isBlank() && stillHard.isBlank()) return ""
        val sb = StringBuilder("Dein Gedächtnis über ihn bei diesem Fehler (").append(rounds).append(if (rounds == 1) " Runde" else " Runden").append("):\n")
        if (understood.isNotBlank()) sb.append("- verstanden: ").append(understood).append('\n')
        if (stillHard.isNotBlank()) sb.append("- fällt noch schwer: ").append(stillHard).append('\n')
        if (helps.isNotBlank()) sb.append("- hilft ihm: ").append(helps).append('\n')
        if (nextFocus.isNotBlank()) sb.append("- nächster Schwerpunkt: ").append(nextFocus).append('\n')
        runs.takeLast(4).forEach { r -> sb.append("- Runde (Niveau ").append(r.level).append("): ").append(r.score).append(" % richtig\n") }
        return sb.toString()
    }

    fun toJson(): JSONObject =
        JSONObject().put("patternKey", patternKey).put("level", level).put("understood", understood).put("stillHard", stillHard)
            .put("helps", helps).put("nextFocus", nextFocus).put("runs", JSONArray().also { a -> runs.forEach { a.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject) =
            TutorMemory(
                patternKey = o.optString("patternKey"),
                level = o.optInt("level", 1).coerceIn(1, 3),
                understood = o.optString("understood"),
                stillHard = o.optString("stillHard"),
                helps = o.optString("helps"),
                nextFocus = o.optString("nextFocus"),
                runs = o.optJSONArray("runs").objects().map { WorkshopRun.fromJson(it) },
            )
    }
}

/** The tutor's memory for every error pattern (workshop.json). */
class WorkshopStore(context: Context) {
    private val file = File(context.filesDir, "workshop.json")
    private val _memories = MutableStateFlow(load())
    val memories: StateFlow<Map<String, TutorMemory>> = _memories.asStateFlow()

    private fun load(): Map<String, TutorMemory> =
        de.abicoach.data.SafeFile.load(file) { text -> JSONArray(text).objects().map { TutorMemory.fromJson(it) }.filter { it.patternKey.isNotBlank() }.associateBy { it.patternKey } }
            ?: emptyMap()

    fun get(key: String): TutorMemory? = _memories.value[key]

    @Synchronized
    fun put(memory: TutorMemory) {
        val next = _memories.value + (memory.patternKey to memory)
        _memories.value = next
        de.abicoach.data.SafeFile.write(file, JSONArray().also { a -> next.values.forEach { a.put(it.toJson()) } }.toString())
    }

    @Synchronized
    fun reset() {
        _memories.value = emptyMap()
        de.abicoach.data.SafeFile.delete(file)
    }
}

/** Writes one workshop round for one error pattern – adapted to what the tutor already knows about the student. */
object WorkshopMaker {
    private val SYSTEM =
        """
Du bist eine geduldige Englisch-Nachhilfelehrerin. Ein deutscher Schüler bereitet sich auf die mündliche Englisch-Abiturprüfung in NRW vor (Grundkurs, Ziel 15 Punkte). In einer kurzen Lerneinheit (8–10 Minuten) behebt ihr EINEN seiner typischen Fehler dauerhaft.
Du bekommst das Fehlermuster (Regel, Kategorie, seine eigenen falschen Sätze mit Verbesserung), Inhalte aus seinem Kurs und dein Gedächtnis über ihn aus früheren Runden (was er verstanden hat, was noch schwer fällt, was ihm hilft) sowie das Niveau.
Schreibe:
- title: die Regel als kurzer deutscher Titel (höchstens 6 Wörter)
- explanation: 2–4 einfache deutsche Sätze. Was läuft bei ihm falsch, und wie geht die Regel? So konkret wie möglich an SEINEN Sätzen. Wenn dein Gedächtnis zeigt, was noch schwer fällt, setze genau dort an; erkläre nicht noch einmal, was er schon kann. Nutze, was ihm hilft.
- hook: eine Eselsbrücke oder Faustregel, ein deutscher Satz
- contrast: 2–3 Paare (wrong, right) – zuerst seine eigenen Sätze, dann einer zu seinem Kursthema
- recognize: genau 5 englische Sätze zu seinen Kursthemen. Etwa die Hälfte richtig, die anderen mit genau diesem Fehler. correct = ob der Satz richtig ist; fix = die richtige Fassung (bei richtigen Sätzen derselbe Satz); why = sehr kurze deutsche Begründung (höchstens 12 Wörter)
- drills: genau 3 Sprechübungen. type: say_right (cue = Satz mit dem Fehler, target = richtig), transform (cue = Satz + in Klammern, was er ändern soll), complete (cue = Satzanfang, der die Struktur verlangt) oder answer (cue = kurze Prüferfrage, deren Antwort die Struktur braucht); instruction auf Deutsch (höchstens 12 Wörter); focus = die 1–4 entscheidenden englischen Wörter
- talk: genau 2 offene Prüferfragen auf Englisch zu seinen Kursthemen, deren natürliche Antwort die Struktur mehrfach braucht (z. B. Entwicklungen bis heute für das present perfect); hint = kurzer deutscher Tipp, ohne die Lösung vorzusagen
- focus: worauf beim freien Sprechen geachtet wird, auf Englisch (z. B. "present perfect with since/for")
Niveau 1: Grundregel mit klaren Fällen. Niveau 2: gemischte Fälle und Signalwörter. Niveau 3: knifflige Fälle, Ausnahmen, längere Sätze, schnelles Sprechen.
Sprache der Sätze: natürliches Englisch auf B2/C1-Niveau, kein Kinderbuch-Englisch.
        """.trimIndent()

    private val schema =
        obj(
            "title" to str(),
            "explanation" to str(),
            "hook" to str(),
            "contrast" to arr(obj("wrong" to str(), "right" to str())),
            "recognize" to arr(obj("sentence" to str(), "correct" to bool(), "fix" to str(), "why" to str())),
            "drills" to arr(obj("type" to enumOf(DrillType.entries.map { it.key }), "instruction" to str(), "cue" to str(), "target" to str(), "focus" to str())),
            "talk" to arr(obj("question" to str(), "hint" to str())),
            "focus" to str(),
        )

    suspend fun make(app: AbiCoachApp, pattern: ErrorPattern, memory: TutorMemory?): WorkshopLesson {
        val level = memory?.level ?: 1
        if (!app.ai.ready) return local(pattern, level)
        val sb = StringBuilder()
        sb.append("Fehlermuster: ").append(pattern.title).append(" (Kategorie: ").append(pattern.categoryLabel).append(", ").append(pattern.total).append("× bisher, ")
            .append(pattern.state.label).append(")\n")
        val examples = pattern.examples.take(4)
        if (examples.isNotEmpty()) {
            sb.append("Seine eigenen Sätze:\n")
            examples.forEach { sb.append("- \"").append(it.wrong).append("\" → \"").append(it.right).append("\"\n") }
        }
        sb.append("Niveau: ").append(level).append('\n')
        memory?.promptBlock()?.takeIf { it.isNotBlank() }?.let { sb.append('\n').append(it) }
        val topics = app.course.coveredTopics().take(3).map { it.first }
        if (topics.isNotEmpty()) {
            sb.append("\nKursinhalte (für die Sätze):\n")
            topics.forEach { t ->
                val facts = app.dossiers.get(t)?.keyPoints?.take(4) ?: app.course.factsFor(t, 4)
                sb.append(Knowledge.topic(t)?.english ?: "").append(": ").append(facts.joinToString(" | ").take(500)).append('\n')
            }
        } else if (app.settings.courseTopics.isNotBlank()) {
            sb.append("\nThemen aus dem Unterricht: ").append(app.settings.courseTopics).append('\n')
        }
        return runCatching {
            val o = app.ai.brainJson(SYSTEM, sb.toString(), schema, thinking = false).json
            parse(o, pattern, level)
        }.getOrNull()?.takeIf { it.recognize.size >= 3 && it.drills.isNotEmpty() } ?: local(pattern, level)
    }

    private fun parse(o: JSONObject, pattern: ErrorPattern, level: Int): WorkshopLesson =
        WorkshopLesson(
            patternKey = pattern.key,
            title = o.optString("title").trim().ifBlank { pattern.title },
            explanation = o.optString("explanation").trim(),
            hook = o.optString("hook").trim(),
            contrast = o.optJSONArray("contrast").objects().map { it.optString("wrong").trim() to it.optString("right").trim() }.filter { it.first.isNotBlank() && it.second.isNotBlank() }.take(3),
            recognize =
                o.optJSONArray("recognize").objects().map {
                    RecognizeItem(it.optString("sentence").trim(), it.optBoolean("correct"), it.optString("fix").trim(), it.optString("why").trim())
                }.filter { it.sentence.isNotBlank() }.take(5),
            drills =
                o.optJSONArray("drills").objects().mapNotNull {
                    val cue = it.optString("cue").trim()
                    val target = it.optString("target").trim()
                    if (cue.isBlank() || target.isBlank()) return@mapNotNull null
                    Drill(UUID.randomUUID().toString(), DrillType.byKey(it.optString("type")), pattern.key, it.optString("instruction").trim().ifBlank { "Sag es richtig." }, cue, target, it.optString("focus").trim())
                }.take(3),
            talk = o.optJSONArray("talk").objects().map { TalkPrompt(it.optString("question").trim(), it.optString("hint").trim()) }.filter { it.question.isNotBlank() }.take(2),
            focus = o.optString("focus").trim().ifBlank { pattern.rule },
            level = level,
        )

    /** Without the AI: a round from the student's own sentences. */
    fun local(pattern: ErrorPattern, level: Int = 1): WorkshopLesson {
        val examples = pattern.examples.take(4)
        val recognize =
            examples.flatMap { listOf(RecognizeItem(it.wrong, false, it.right, pattern.title), RecognizeItem(it.right, true, it.right, "So ist es richtig.")) }
                .shuffled().take(5)
        return WorkshopLesson(
            patternKey = pattern.key,
            title = pattern.title,
            explanation = "Bei dir passiert immer wieder derselbe Fehler (${pattern.categoryLabel}). Schau dir deine eigenen Sätze an: links falsch, rechts richtig. Sag die richtige Fassung danach laut.",
            hook = "Erst denken, dann sprechen: Achte genau auf diese Stelle.",
            contrast = examples.take(3).map { it.wrong to it.right },
            recognize = recognize,
            drills = DrillMaker.local(listOf(pattern), 3),
            talk = listOf(TalkPrompt("Tell me about something that has changed in your life over the last few years.", "Erzähl in 3–4 Sätzen und achte auf die Regel.")),
            focus = pattern.rule.ifBlank { pattern.categoryLabel },
            level = level,
        )
    }
}

/** Listens to a free answer in the workshop: was the structure used, and used correctly? */
object WorkshopCheck {
    private val SYSTEM =
        """
Du bist Englischlehrerin. Ein deutscher Schüler übt in einer Lerneinheit genau eine Regel. Du bekommst die Prüferfrage, die Regel und seine gesprochene Antwort.
Entscheide:
- heard: was er gesagt hat, wortgetreu (mit seinen Fehlern, nicht verbessert)
- used: kam die Struktur der Regel überhaupt vor?
- ok: true, wenn er die Struktur an allen Stellen richtig verwendet hat und der trainierte Fehler nicht vorkam. Andere kleine Fehler zählen hier nicht.
- tip: ein kurzer deutscher Satz – bei ok ein konkretes Lob, sonst genau die Stelle, die falsch war, und wie es richtig heißt; kam die Struktur nicht vor, wie er sie hätte einbauen können
- better: einer seiner Sätze in richtiger, natürlicher Fassung mit der Struktur (Englisch)
        """.trimIndent()

    private val schema = obj("heard" to str(), "used" to bool(), "ok" to bool(), "tip" to str(), "better" to str())

    suspend fun check(app: AbiCoachApp, lesson: WorkshopLesson, prompt: TalkPrompt, wav: ByteArray): TalkCheck {
        val parts =
            listOf(
                GeminiRest.textPart("Regel: ${lesson.title} – ${lesson.focus}\nPrüferfrage: \"${prompt.question}\"\nSeine Antwort (Aufnahme) folgt."),
                GeminiRest.inlinePart("audio/wav", wav),
            )
        val o = app.ai.quickListenJson(SYSTEM, parts, schema).json
        return TalkCheck(o.optString("heard").trim(), o.optBoolean("used"), o.optBoolean("ok"), o.optString("tip").trim(), o.optString("better").trim())
    }
}

/**
 * After a round the tutor writes down what it learnt about the student – the next round, the speaking training and
 * the coach build on it. The level goes up after a good round and down after a weak one.
 */
object WorkshopReflect {
    private val SYSTEM =
        """
Du bist die Nachhilfelehrerin aus der Lerneinheit und führst ein kurzes Gedächtnis über den Schüler und diesen einen Fehler. Du bekommst das Fehlermuster, dein bisheriges Gedächtnis, alle Antworten dieser Runde (richtig/falsch, was er gesagt hat) und das Niveau.
Schreibe kurz und konkret auf Deutsch (je höchstens 20 Wörter):
- understood: was er jetzt sicher kann
- still_hard: was ihm noch schwer fällt (genaue Fälle, Signalwörter, Situationen) – leer, wenn nichts
- helps: was ihm geholfen hat (z. B. eine Eselsbrücke, Kontrastpaare, langsames Sprechen)
- next_focus: womit die nächste Runde anfangen soll
- level: 1, 2 oder 3 für die nächste Runde (rauf bei mindestens 80 % richtig, runter bei unter 50 %)
        """.trimIndent()

    private val schema = obj("understood" to str(), "still_hard" to str(), "helps" to str(), "next_focus" to str(), "level" to int())

    suspend fun reflect(app: AbiCoachApp, pattern: ErrorPattern, lesson: WorkshopLesson, run: WorkshopRun, answers: List<String>, memory: TutorMemory?): TutorMemory {
        val base = memory ?: TutorMemory(pattern.key)
        val ruleLevel = (lesson.level + if (run.score >= 80) 1 else if (run.score < 50) -1 else 0).coerceIn(1, 3)
        val fallback = base.copy(level = ruleLevel, runs = (base.runs + run).takeLast(12))
        if (!app.ai.ready) return fallback
        val user =
            "Fehlermuster: ${pattern.title} (${pattern.categoryLabel})\nNiveau dieser Runde: ${lesson.level}\nErgebnis: ${run.score} % richtig " +
                "(Erkennen ${run.recognize.first}/${run.recognize.second}, Sagen ${run.speak.first}/${run.speak.second}, frei sprechen ${run.talk.first}/${run.talk.second})\n\n" +
                base.promptBlock() + "\nAntworten dieser Runde:\n" + answers.joinToString("\n") { "- $it" }
        return runCatching {
            val o = app.ai.brainJson(SYSTEM, user, schema, thinking = false).json
            fallback.copy(
                level = o.optInt("level", ruleLevel).coerceIn(1, 3),
                understood = o.optString("understood").trim().ifBlank { base.understood },
                stillHard = o.optString("still_hard").trim(),
                helps = o.optString("helps").trim().ifBlank { base.helps },
                nextFocus = o.optString("next_focus").trim(),
            )
        }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else fallback }
    }
}

/** What the tutor noted as still hard for these patterns (for the prompts of the speaking training). */
fun WorkshopStore.stillHard(key: String): String = get(key)?.stillHard.orEmpty()
