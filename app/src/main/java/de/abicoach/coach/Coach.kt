package de.abicoach.coach

import android.content.Context
import de.abicoach.AbiCoachApp
import de.abicoach.ai.arr
import de.abicoach.ai.obj
import de.abicoach.ai.str
import de.abicoach.data.Knowledge
import de.abicoach.data.objects
import de.abicoach.path.PathEngine
import de.abicoach.session.CoachAction
import de.abicoach.ui.Planner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Everything the AI coach should know about the student, as compact text for prompts. */
object CoachContext {
    fun student(app: AbiCoachApp, withLevels: Boolean = true): String {
        val profile = app.profiles.profile.value
        val sessions = app.store.sessions.value
        val sb = StringBuilder("Stand des Schülers:")
        Planner.daysUntilExam(app)?.let { sb.append(" Prüfung in ").append(it).append(" Tagen.") }
        profile.prognosis()?.let { sb.append(" Prognose ").append(it.points).append(" Punkte (Spanne ").append(it.lowPoints).append("–").append(it.highPoints).append(").") }
        if (profile.skills.isNotEmpty()) {
            sb.append("\nKompetenzen (0–15): ")
            sb.append(profile.skills.entries.sortedBy { it.value.score }.joinToString(", ") { "${it.key.label} ${"%.0f".format(it.value.score)}" })
            sb.append("\nGrößte Hebel: ").append(profile.levers(3).joinToString(", ") { it.label }).append('.')
        }
        val recent = sessions.filter { it.debrief != null }.take(4)
        if (recent.isNotEmpty()) {
            sb.append("\nLetzte Sitzungen: ")
            sb.append(recent.joinToString("; ") { s -> "${s.kind.label} (${Knowledge.topic(s.topicId)?.short ?: "frei"}) ${s.debrief!!.totalPoints} P." })
            val last = recent.first().debrief!!
            sb.append("\nLetzte Auswertung: Inhalt ").append(last.inhaltPoints).append(" P. – ").append(last.inhaltReason.take(220))
            sb.append("\nDarstellung ").append(last.darstellungPoints).append(" P. – ").append(last.darstellungReason.take(220))
            if (last.levers.isNotEmpty()) sb.append("\nEmpfohlene Hebel daraus: ").append(last.levers.joinToString(" | ").take(300))
        } else {
            sb.append("\nNoch keine ausgewertete Sitzung.")
        }
        val errors = app.store.categoryCounts().entries.sortedByDescending { it.value }.take(4)
        if (errors.isNotEmpty()) sb.append("\nHäufigste Fehler: ").append(errors.joinToString(", ") { "${Knowledge.categoryLabel(it.key)} (${it.value}×)" })
        val patterns = app.errorMemory.summary(5)
        if (patterns.isNotBlank()) {
            sb.append("\nFehlergedächtnis (wichtigste offene Fehlermuster):\n").append(patterns)
            val due = app.errorMemory.due().size
            if (due > 0) sb.append("\nIm Sprech-Training fällig: ").append(due).append(" Fehlermuster (Aktion: training).")
        }
        val covered = app.course.coveredTopics().map { it.first }
        if (covered.isNotEmpty() || profile.topics.isNotEmpty()) {
            val rated = profile.topics.entries.sortedBy { it.value.score }.joinToString(", ") { "${Knowledge.topicShort(it.key)} ${"%.0f".format(it.value.score)} P." }
            val untried = covered.filter { it !in profile.topics }.take(4).joinToString(", ") { Knowledge.topicShort(it) }
            sb.append("\nThemen-Kompass:")
            if (rated.isNotBlank()) sb.append(" geübt – ").append(rated).append('.')
            if (untried.isNotBlank()) sb.append(" Im Kurs, aber noch nie geübt: ").append(untried).append('.')
        }
        val dossiers = app.dossiers.all.value.keys.sorted()
        if (dossiers.isNotEmpty()) sb.append("\nDossiers vorhanden (Aktion dossier:NUMMER): ").append(dossiers.joinToString(", ") { "$it = ${Knowledge.topicShort(it)}" }).append('.')
        val real = de.abicoach.memory.ExamUnits.all(app)
        if (real.isNotEmpty()) {
            val open = real.count { it.id !in de.abicoach.memory.ExamUnits.used(app) }
            sb.append("\nEchte Prüfungsaufgaben im Gedächtnis: ").append(real.size).append(" (noch nicht geübt: ").append(open).append("; Aktion: echt).")
        }
        de.abicoach.session.ProgressMaker.note(app).takeIf { it.isNotBlank() }?.let { sb.append("\nFortschritt seit der Sitzung davor:\n").append(it) }
        if (profile.teacherNotes.isNotEmpty()) sb.append("\nKommentare der Lehrkraft: ").append(profile.teacherNotes.takeLast(3).joinToString(" | ").take(300))
        val due = app.vocab.due().size
        if (due > 0) sb.append("\nWortschatz: $due Karten fällig.")
        sb.append("\nTage in Folge geübt: ").append(app.store.streak()).append(", heute ").append(app.store.minutesToday()).append(" von ").append(app.settings.dailyMinutes).append(" Min.")
        // What the learning brain computed from all of this (plan, projection, habits): the coach builds on it.
        runCatching { de.abicoach.learn.LearningBrain.explain(app) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { sb.append("\n\n").append(it) }
        if (withLevels) {
            val worlds = app.worlds()
            val progress = app.path.progress.value
            val next = PathEngine.next(worlds, progress, profile)
            val dueLevels = PathEngine.due(worlds, progress).take(3)
            val ids = (listOfNotNull(next) + dueLevels).map { "${it.level.id} = ${it.level.title}" }
            if (ids.isNotEmpty()) sb.append("\nNächste Level (IDs für [[level:ID]]): ").append(ids.distinct().joinToString("; "))
        }
        return sb.toString()
    }
}

/** One step of the daily plan; [action] is a coach action like "aufgabe2" or "level:w3.perceive". */
data class BriefingStep(val title: String, val why: String, val action: String) {
    val parsed: CoachAction? get() = CoachAction.parse(action)
}

data class Briefing(val date: String, val signature: String, val headline: String, val focus: String, val steps: List<BriefingStep>, val by: String) {
    fun toJson(): JSONObject =
        JSONObject().put("date", date).put("signature", signature).put("headline", headline).put("focus", focus).put("by", by)
            .put("steps", JSONArray().also { a -> steps.forEach { a.put(JSONObject().put("title", it.title).put("why", it.why).put("action", it.action)) } })

    companion object {
        fun fromJson(o: JSONObject) =
            Briefing(
                o.optString("date"),
                o.optString("signature"),
                o.optString("headline"),
                o.optString("focus"),
                o.optJSONArray("steps").objects().map { BriefingStep(it.optString("title"), it.optString("why"), it.optString("action")) },
                o.optString("by"),
            )
    }
}

/** The AI plan for today, kept on the phone and renewed once a day or after a new evaluated session. */
class BriefingStore(context: Context) {
    private val file = File(context.filesDir, "briefing.json")
    private val _current = MutableStateFlow(load())
    val current: StateFlow<Briefing?> = _current.asStateFlow()

    private fun load(): Briefing? = runCatching { Briefing.fromJson(JSONObject(file.readText())) }.getOrNull()?.takeIf { it.steps.isNotEmpty() }

    fun save(b: Briefing) {
        _current.value = b
        runCatching { file.writeText(b.toJson().toString()) }
    }

    fun clear() {
        _current.value = null
        runCatching { file.delete() }
    }
}

object BriefingMaker {
    private val SYSTEM =
        """
Du bist der persönliche KI-Coach in einer App für die mündliche Abiturprüfung Englisch in NRW (Grundkurs, Ziel: 15 Punkte).
Du bekommst den aktuellen Stand des Schülers und erstellst seinen Tagesplan auf Deutsch, in einfacher, freundlicher Sprache.
Regeln:
- headline: ein motivierender, konkreter Satz (höchstens 14 Wörter), kein Floskel-Satz.
- focus: zwei Sätze: Woran liegt es gerade am meisten (Zahlen aus dem Stand nennen) und was bringt heute die meisten Punkte.
- steps: genau 3 Schritte. Im Stand steht unter „Lern-Gehirn der App“ der Plan, den die App aus allen Daten berechnet hat (erwarteter Punktgewinn pro Minute, Phase bis zur Prüfung). Übernimm seine Schritte und ihre Reihenfolge mit genau seinen Aktionen; weiche nur ab, wenn der Stand einen klaren Grund liefert (dann nimm einen der „weiteren Kandidaten“). Jeder Schritt hat title (höchstens 6 Wörter), why (ein Satz, warum das heute dran ist – mit den Zahlen und Gründen aus dem Plan, seinen Fehlern oder Gewohnheiten) und action.
- action ist genau einer dieser Werte: aufgabe1, aufgabe2, aufgabe3, vortrag, gespraech oder gespraech:NUMMER (Gespräch zu einem Themenfeld), sprint, simulation, wortschatz, aussprache, training (Sprech-Training seiner eigenen Fehlermuster), echt (eine echte Prüfungsaufgabe aus seinem Abi-Material, nur wenn vorhanden), dossier:NUMMER (ein Themen-Dossier lesen, nur vorhandene), einstufung oder level:ID (nur eine ID aus der Liste im Stand).
- focus nennt die Phase und das, was laut Plan heute am meisten Punkte bringt.
- Wenn er heute schon geübt hat, lobe ihn kurz.
        """.trimIndent()

    private val schema = obj("headline" to str(), "focus" to str(), "steps" to arr(obj("title" to str(), "why" to str(), "action" to str())))

    fun signature(app: AbiCoachApp): String =
        "${LocalDate.now()}|${app.store.sessions.value.count { it.debrief != null }}|${app.profiles.profile.value.placementDone}"

    fun worthIt(app: AbiCoachApp): Boolean =
        app.ai.hasGemini && (app.profiles.profile.value.placementDone || app.store.sessions.value.any { it.debrief != null })

    /** Asks the brain for today's plan. Invalid steps are dropped; fewer than two valid steps count as failure. */
    suspend fun make(app: AbiCoachApp): Briefing {
        val context = CoachContext.student(app) + "\nAktuelle Uhrzeit: " + java.time.LocalTime.now().withSecond(0).withNano(0)
        val answer = app.ai.brainJson(SYSTEM, context, schema, thinking = false)
        val o = answer.json
        val steps =
            o.optJSONArray("steps").objects().mapNotNull { s ->
                val action = s.optString("action").trim().removePrefix("[[").removeSuffix("]]")
                val parsed = CoachAction.parse(action) ?: return@mapNotNull null
                BriefingStep(s.optString("title").trim(), s.optString("why").trim(), if (parsed.arg.isBlank()) parsed.kind else "${parsed.kind}:${parsed.arg}")
            }.filter { it.title.isNotBlank() }.distinctBy { it.action }.take(3)
        if (steps.size < 2 || o.optString("headline").isBlank()) throw IllegalStateException("Der Tagesplan war unvollständig.")
        return Briefing(LocalDate.now().toString(), signature(app), o.optString("headline").trim(), o.optString("focus").trim(), steps, answer.by)
    }
}
