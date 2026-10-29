package de.abicoach.profile

import android.content.Context
import de.abicoach.data.objects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The two parts of the NRW assessment: content counts 40 %, language and delivery 60 %. */
enum class Area(val label: String, val weight: Double) {
    INHALT("Inhalt", 0.4),
    DARSTELLUNG("Darstellungsleistung", 0.6),
}

/** The competences the profile tracks, modelled on the NRW assessment grid for the oral exam. */
enum class Skill(val key: String, val label: String, val area: Area, val description: String, val english: String) {
    AUFGABENBEZUG("task_focus", "Aufgabenbezug", Area.INHALT, "Alle Aufgaben vollständig und genau bearbeitet", "task focus: all tasks answered completely and precisely"),
    ZUSAMMENFASSEN("summary", "Zusammenfassen", Area.INHALT, "Aufgabe 1: Kernaussagen knapp und korrekt", "task 1 (AFB I): concise and accurate summary or outline of the main points"),
    ANALYSE("analysis", "Analyse", Area.INHALT, "Aufgabe 2: Mittel, Haltung, Wirkung, Textbelege", "task 2 (AFB II): analysis of means, perspective, attitude and effect, with evidence from the text"),
    KOMMENTAR("comment", "Stellungnahme", Area.INHALT, "Aufgabe 3: eigene, begründete Position", "task 3 (AFB III): a reasoned personal position with arguments and examples"),
    SACHWISSEN("knowledge", "Sachwissen & Bezüge", Area.INHALT, "Kurswissen, Beispiele, Themen verknüpfen", "subject knowledge from the course, examples and links between topic areas"),
    STRUKTUR("structure", "Aufbau & Vortrag", Area.DARSTELLUNG, "Gliederung, Überleitungen, frei sprechen", "structure, signposting and free delivery instead of reading out notes"),
    INTERAKTION("interaction", "Gesprächsführung", Area.DARSTELLUNG, "Eingehen, Impulse setzen, nachfragen", "interaction: responding to questions, taking initiative, clarifying"),
    FLUESSIGKEIT("fluency", "Flüssigkeit", Area.DARSTELLUNG, "Tempo, Pausen, Füllwörter", "fluency: pace, pauses and fillers"),
    AUSSPRACHE("pronunciation", "Aussprache & Intonation", Area.DARSTELLUNG, "Laute, Betonung, Satzmelodie", "pronunciation and intonation"),
    WORTSCHATZ("vocabulary", "Wortschatz", Area.DARSTELLUNG, "Themenwortschatz, Präzision, Redemittel", "range and precision of vocabulary, topic words and phrases for analysis and discussion"),
    GRAMMATIK("grammar", "Grammatik & Satzbau", Area.DARSTELLUNG, "Korrektheit und Komplexität", "grammatical accuracy and range of structures"),
    ;

    companion object {
        fun byKey(key: String): Skill? = entries.firstOrNull { it.key == key }
    }
}

data class SkillState(val score: Double, val confidence: Double, val samples: Int, val updatedAt: Long) {
    fun toJson(): JSONObject = JSONObject().put("score", score).put("confidence", confidence).put("samples", samples).put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject) = SkillState(o.optDouble("score", 7.0), o.optDouble("confidence", 0.2), o.optInt("samples"), o.optLong("updatedAt"))
    }
}

/** One point of the forecast history, for the chart in the statistics. */
data class ForecastPoint(val at: Long, val total: Double, val source: String)

/** The expected exam result with a range that narrows the more the student practises. */
data class Prognosis(val total: Double, val inhalt: Double?, val darstellung: Double?, val low: Double, val high: Double, val confidence: Double) {
    val points: Int get() = total.roundToInt().coerceIn(0, 15)
    val lowPoints: Int get() = low.roundToInt().coerceIn(0, 15)
    val highPoints: Int get() = high.roundToInt().coerceIn(0, 15)
}

data class Profile(
    val skills: Map<Skill, SkillState> = emptyMap(),
    val placementDone: Boolean = false,
    val placementAt: Long = 0,
    val history: List<ForecastPoint> = emptyList(),
    /** Error categories found in the student's own Klausuren. */
    val klausurErrors: Map<String, Int> = emptyMap(),
    /** Comments of the teacher found in the Klausuren (what she values and criticises). */
    val teacherNotes: List<String> = emptyList(),
) {
    fun prognosis(): Prognosis? {
        if (skills.isEmpty()) return null
        fun areaScore(area: Area): Double? {
            val states = skills.filterKeys { it.area == area }.values
            if (states.isEmpty()) return null
            // Better-known skills count more.
            val weights = states.map { 0.3 + it.confidence }
            return states.zip(weights).sumOf { (s, w) -> s.score * w } / weights.sum()
        }
        val inhalt = areaScore(Area.INHALT)
        val darstellung = areaScore(Area.DARSTELLUNG)
        val total =
            when {
                inhalt != null && darstellung != null -> Area.INHALT.weight * inhalt + Area.DARSTELLUNG.weight * darstellung
                inhalt != null -> inhalt
                else -> darstellung ?: return null
            }
        val known = Skill.entries.map { skills[it]?.confidence ?: 0.0 }
        val confidence = known.average()
        val spread = 1.0 + 3.5 * (1.0 - confidence)
        return Prognosis(total, inhalt, darstellung, max(0.0, total - spread), min(15.0, total + spread), confidence)
    }

    /**
     * Folds the ratings of one session into the profile. [ratings] holds 0–15 per skill (missing = not assessable),
     * [weight] says how much the session counts (full simulation 1.0, short training less).
     */
    fun withRatings(ratings: Map<Skill, Int>, weight: Double, at: Long, source: String): Profile {
        if (ratings.isEmpty()) return this
        val next = skills.toMutableMap()
        ratings.forEach { (skill, raw) ->
            val value = raw.coerceIn(0, 15).toDouble()
            val old = next[skill]
            next[skill] =
                if (old == null) {
                    SkillState(value, (0.2 + 0.2 * weight).coerceAtMost(0.6), 1, at)
                } else {
                    val rate = max(0.25, 1.0 / (old.samples + 1)) * weight.coerceIn(0.2, 1.0)
                    SkillState(
                        score = old.score + rate * (value - old.score),
                        confidence = min(0.95, old.confidence + 0.15 * weight),
                        samples = old.samples + 1,
                        updatedAt = at,
                    )
                }
        }
        val updated = copy(skills = next)
        val total = updated.prognosis()?.total
        val history = if (total == null) history else (history + ForecastPoint(at, total, source)).takeLast(200)
        return updated.copy(history = history)
    }

    /** The skills where practice brings the most points: low score × weight of its area. */
    fun levers(limit: Int = 3): List<Skill> {
        if (skills.isEmpty()) return emptyList()
        val perArea = Skill.entries.groupBy { it.area }.mapValues { it.value.size }
        return Skill.entries
            .map { s ->
                val state = skills[s]
                val score = state?.score ?: 9.0
                val gain = (15.0 - score) * s.area.weight / (perArea[s.area] ?: 1)
                s to gain
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun strengths(limit: Int = 3): List<Skill> =
        skills.entries.filter { it.value.score >= 10.5 }.sortedByDescending { it.value.score }.take(limit).map { it.key }

    /** Has the forecast changed noticeably over the last [days] days? Positive = better. */
    fun trend(days: Int = 14): Double? {
        if (history.size < 2) return null
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val before = history.lastOrNull { it.at < cutoff } ?: history.first()
        val now = history.last()
        val diff = now.total - before.total
        return if (abs(diff) < 0.05) 0.0 else diff
    }

    fun toJson(): JSONObject {
        val s = JSONObject()
        skills.forEach { (k, v) -> s.put(k.key, v.toJson()) }
        return JSONObject()
            .put("skills", s)
            .put("placementDone", placementDone)
            .put("placementAt", placementAt)
            .put("history", JSONArray().also { a -> history.forEach { a.put(JSONObject().put("at", it.at).put("total", it.total).put("source", it.source)) } })
            .put("klausurErrors", JSONObject(klausurErrors as Map<*, *>))
            .put("teacherNotes", JSONArray(teacherNotes))
    }

    companion object {
        fun fromJson(o: JSONObject): Profile {
            val s = o.optJSONObject("skills") ?: JSONObject()
            val skills = mutableMapOf<Skill, SkillState>()
            s.keys().forEach { key -> Skill.byKey(key)?.let { skill -> s.optJSONObject(key)?.let { skills[skill] = SkillState.fromJson(it) } } }
            val errors = mutableMapOf<String, Int>()
            o.optJSONObject("klausurErrors")?.let { e -> e.keys().forEach { k -> errors[k] = e.optInt(k) } }
            val notes = o.optJSONArray("teacherNotes")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            return Profile(
                skills = skills,
                placementDone = o.optBoolean("placementDone"),
                placementAt = o.optLong("placementAt"),
                history = o.optJSONArray("history").objects().map { ForecastPoint(it.optLong("at"), it.optDouble("total"), it.optString("source")) },
                klausurErrors = errors,
                teacherNotes = notes,
            )
        }
    }
}

/** Keeps the learner profile in one JSON file. */
class ProfileStore(context: Context) {
    private val file = File(context.filesDir, "profile.json")
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private fun load(): Profile = runCatching { Profile.fromJson(JSONObject(file.readText())) }.getOrElse { Profile() }

    @Synchronized
    fun update(change: (Profile) -> Profile) {
        val next = change(_profile.value)
        _profile.value = next
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(next.toJson().toString())
            tmp.renameTo(file)
        }
    }

    fun reset() = update { Profile() }
}
