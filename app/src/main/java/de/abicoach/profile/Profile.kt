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
import kotlin.math.pow
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

/** How ready the student is for each part of the exam (0–15), from the skills each part needs. */
data class Readiness(val part1: Double?, val part2: Double?)

/** One point of the forecast history, for the chart in the statistics. */
data class ForecastPoint(val at: Long, val total: Double, val source: String)

/**
 * A real grade for speaking that the student got at school (a mock oral exam, the Kommunikationsprüfung, the oral
 * mark of a term), with what the app forecast on that day. The app's points are an AI's estimate – real grades show
 * whether that estimate tends to be too friendly or too strict.
 */
data class RealGrade(val at: Long, val points: Int, val label: String, val forecast: Double?) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("points", points).put("label", label).put("forecast", forecast ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) =
            RealGrade(o.optLong("at"), o.optInt("points").coerceIn(0, 15), o.optString("label"), o.optDouble("forecast").takeIf { !it.isNaN() })
    }
}

/**
 * How the app's forecast compares with real grades. [offset] is what the real grades were on average above (+) or
 * below (−) the forecast, pulled towards zero while there are only a few grades; [adjusted] is the forecast with it.
 */
data class Calibration(val offset: Double, val count: Int, val adjusted: Double) {
    val adjustedPoints: Int get() = adjusted.roundToInt().coerceIn(0, 15)

    /** Within one point the forecast counts as confirmed. */
    val confirmed: Boolean get() = abs(offset) < 1.0
}

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
    /** How well the student masters the content of each topic field (0–15), from the sessions on it. */
    val topics: Map<Int, SkillState> = emptyMap(),
    /** The last ratings of each skill (oldest first), for the trend. */
    val skillLog: Map<Skill, List<Int>> = emptyMap(),
    /** Real grades for speaking from school, to check the forecast against. */
    val realGrades: List<RealGrade> = emptyList(),
) {
    /** Adds a real grade and remembers what the app forecast at that moment. */
    fun withRealGrade(points: Int, label: String, at: Long): Profile =
        copy(realGrades = (realGrades + RealGrade(at, points.coerceIn(0, 15), label.trim().take(60), prognosis()?.total)).takeLast(20))

    fun withoutRealGrade(at: Long): Profile = copy(realGrades = realGrades.filter { it.at != at })

    /**
     * The comparison of forecast and real grades; null without a grade that has a forecast next to it. One grade is
     * weak evidence, so the difference only counts in part: with n grades it counts n/(n+1).
     */
    fun calibration(): Calibration? {
        val pairs = realGrades.filter { it.forecast != null }
        val now = prognosis() ?: return null
        if (pairs.isEmpty()) return null
        val offset = pairs.sumOf { it.points - it.forecast!! } / (pairs.size + 1)
        return Calibration(offset, pairs.size, (now.total + offset).coerceIn(0.0, 15.0))
    }

    /**
     * How sure the app still is about a rating: knowledge fades when a skill is not practised (half-life 30 days),
     * so the forecast range widens again and the skill comes back into the plan.
     */
    fun effectiveConfidence(state: SkillState, now: Long = System.currentTimeMillis()): Double {
        val days = max(0.0, (now - state.updatedAt) / 86_400_000.0)
        return state.confidence * 0.5.pow(days / 30.0)
    }

    fun prognosis(): Prognosis? {
        if (skills.isEmpty()) return null
        val now = System.currentTimeMillis()
        fun areaScore(area: Area): Double? {
            val states = skills.filterKeys { it.area == area }.values
            if (states.isEmpty()) return null
            // Better-known skills count more.
            val weights = states.map { 0.3 + effectiveConfidence(it, now) }
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
        val known = Skill.entries.map { skills[it]?.let { s -> effectiveConfidence(s, now) } ?: 0.0 }
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
        val log = skillLog.toMutableMap()
        ratings.forEach { (skill, raw) -> log[skill] = (log[skill].orEmpty() + raw.coerceIn(0, 15)).takeLast(8) }
        val updated = copy(skills = next, skillLog = log)
        val total = updated.prognosis()?.total
        val history = if (total == null) history else (history + ForecastPoint(at, total, source)).takeLast(200)
        return updated.copy(history = history)
    }

    /** Folds the content rating of one session into the topic field it was about. */
    fun withTopic(topicId: Int, raw: Int, weight: Double, at: Long): Profile {
        if (topicId !in 0..13) return this
        val value = raw.coerceIn(0, 15).toDouble()
        val old = topics[topicId]
        val next =
            if (old == null) {
                SkillState(value, (0.25 + 0.25 * weight).coerceAtMost(0.6), 1, at)
            } else {
                val rate = max(0.3, 1.0 / (old.samples + 1)) * weight.coerceIn(0.2, 1.0)
                SkillState(old.score + rate * (value - old.score), min(0.95, old.confidence + 0.15 * weight), old.samples + 1, at)
            }
        return copy(topics = topics + (topicId to next))
    }

    /** Change per session over the last ratings (positive = better), null with fewer than three ratings. */
    fun skillTrend(skill: Skill): Double? {
        val log = skillLog[skill] ?: return null
        if (log.size < 3) return null
        val xs = log.indices.map { it.toDouble() }
        val mx = xs.average()
        val my = log.average()
        val num = xs.zip(log).sumOf { (x, y) -> (x - mx) * (y - my) }
        val den = xs.sumOf { (it - mx) * (it - mx) }
        return if (den == 0.0) 0.0 else num / den
    }

    /** Readiness for part 1 (presentation) and part 2 (conversation), weighted 40 % content and 60 % language. */
    fun readiness(): Readiness {
        fun combine(content: List<Skill>, language: List<Skill>): Double? {
            val c = content.mapNotNull { skills[it]?.score }
            val l = language.mapNotNull { skills[it]?.score }
            return when {
                c.isNotEmpty() && l.isNotEmpty() -> 0.4 * c.average() + 0.6 * l.average()
                c.isNotEmpty() -> c.average()
                l.isNotEmpty() -> l.average()
                else -> null
            }
        }
        val language = listOf(Skill.FLUESSIGKEIT, Skill.AUSSPRACHE, Skill.WORTSCHATZ, Skill.GRAMMATIK)
        return Readiness(
            part1 = combine(listOf(Skill.AUFGABENBEZUG, Skill.ZUSAMMENFASSEN, Skill.ANALYSE, Skill.KOMMENTAR), language + Skill.STRUKTUR),
            part2 = combine(listOf(Skill.SACHWISSEN, Skill.KOMMENTAR), language + Skill.INTERAKTION),
        )
    }

    /** The skills where practice brings the most points: low score × weight of its area, more if the rating is old or unsure. */
    fun levers(limit: Int = 3): List<Skill> {
        if (skills.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val perArea = Skill.entries.groupBy { it.area }.mapValues { it.value.size }
        return Skill.entries
            .map { s ->
                val state = skills[s]
                val score = state?.score ?: 9.0
                val unsure = 1.0 - (state?.let { effectiveConfidence(it, now) } ?: 0.0)
                val gain = (15.0 - score) * s.area.weight / (perArea[s.area] ?: 1) * (1.0 + 0.4 * unsure)
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
            .put("topics", JSONObject().also { t -> topics.forEach { (k, v) -> t.put(k.toString(), v.toJson()) } })
            .put("skillLog", JSONObject().also { l -> skillLog.forEach { (k, v) -> l.put(k.key, JSONArray(v)) } })
            .put("realGrades", JSONArray().also { a -> realGrades.forEach { a.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Profile {
            val s = o.optJSONObject("skills") ?: JSONObject()
            val skills = mutableMapOf<Skill, SkillState>()
            s.keys().forEach { key -> Skill.byKey(key)?.let { skill -> s.optJSONObject(key)?.let { skills[skill] = SkillState.fromJson(it) } } }
            val errors = mutableMapOf<String, Int>()
            o.optJSONObject("klausurErrors")?.let { e -> e.keys().forEach { k -> errors[k] = e.optInt(k) } }
            val notes = o.optJSONArray("teacherNotes")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            val topics = mutableMapOf<Int, SkillState>()
            o.optJSONObject("topics")?.let { t -> t.keys().forEach { k -> k.toIntOrNull()?.takeIf { it in 0..13 }?.let { id -> t.optJSONObject(k)?.let { topics[id] = SkillState.fromJson(it) } } } }
            val log = mutableMapOf<Skill, List<Int>>()
            o.optJSONObject("skillLog")?.let { l ->
                l.keys().forEach { k -> Skill.byKey(k)?.let { skill -> l.optJSONArray(k)?.let { a -> log[skill] = (0 until a.length()).map { a.optInt(it) } } } }
            }
            return Profile(
                skills = skills,
                placementDone = o.optBoolean("placementDone"),
                placementAt = o.optLong("placementAt"),
                // A point without a number (a damaged file) would break the chart.
                history = o.optJSONArray("history").objects().map { ForecastPoint(it.optLong("at"), it.optDouble("total"), it.optString("source")) }.filter { !it.total.isNaN() },
                klausurErrors = errors,
                teacherNotes = notes,
                topics = topics,
                skillLog = log,
                realGrades = o.optJSONArray("realGrades").objects().map { RealGrade.fromJson(it) },
            )
        }
    }
}

/** Keeps the learner profile in one JSON file. */
class ProfileStore(root: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(root, "profile.json")
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private fun load(): Profile = de.abicoach.data.SafeFile.load(file) { Profile.fromJson(JSONObject(it)) } ?: Profile()

    @Synchronized
    fun update(change: (Profile) -> Profile) {
        val next = change(_profile.value)
        _profile.value = next
        de.abicoach.data.SafeFile.write(file, next.toJson().toString())
    }

    fun reset() {
        update { Profile() }
        de.abicoach.data.SafeFile.dropBackup(file)
    }
}
