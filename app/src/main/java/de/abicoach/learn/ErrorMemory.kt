package de.abicoach.learn

import android.content.Context
import de.abicoach.data.Knowledge
import de.abicoach.data.LiveError
import de.abicoach.data.objects
import de.abicoach.data.strings
import de.abicoach.data.toJsonArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** How far a kind of mistake is from being fixed. */
enum class PatternState(val label: String) {
    NEU("neu"),
    AKTIV("aktiv"),
    BESSER("wird besser"),
    BEHOBEN("behoben"),
}

data class PatternExample(val wrong: String, val right: String, val at: Long, val source: String) {
    fun toJson(): JSONObject = JSONObject().put("wrong", wrong).put("right", right).put("at", at).put("source", source)

    companion object {
        fun fromJson(o: JSONObject) = PatternExample(o.optString("wrong"), o.optString("right"), o.optLong("at"), o.optString("source"))
    }
}

/**
 * One kind of mistake the student makes, across all sessions and Klausuren: "third person -s", "since vs for" …
 * It remembers examples, how often and how recently it happened, how many sessions in a row it stayed away, and when
 * it is due again in the speaking training (spaced repetition).
 */
data class ErrorPattern(
    val key: String,
    val rule: String,
    val category: String,
    val examples: List<PatternExample>,
    val total: Int,
    val sessions: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    /** Sessions with enough speech in a row in which this mistake did not come back. */
    val cleanStreak: Int,
    val lastSession: String,
    val fromKlausur: Int = 0,
    val dueAt: Long = 0,
    val intervalDays: Double = 0.0,
    val ease: Double = 2.3,
    val drills: Int = 0,
    val drillsOk: Int = 0,
    val lastDrillAt: Long = 0,
    /** Answers in the speaking training in a row that were right. */
    val drillStreak: Int = 0,
) {
    val state: PatternState
        get() =
            when {
                cleanStreak >= 3 || (cleanStreak >= 2 && drillStreak >= 2) -> PatternState.BEHOBEN
                cleanStreak >= 1 || drillStreak >= 2 -> PatternState.BESSER
                total <= 1 -> PatternState.NEU
                else -> PatternState.AKTIV
            }

    /** The name shown in the app: the rule, or the German category if the AI gave none. */
    val title: String get() = rule.ifBlank { Knowledge.categoryLabel(category) }

    val categoryLabel: String get() = Knowledge.categoryLabel(category)

    /** How many points this mistake costs right now: frequent, recent and not yet fixed counts most. */
    fun weight(now: Long): Double {
        val days = max(0.0, (now - lastSeen) / DAY.toDouble())
        val recency = 0.5.pow(days / 21.0)
        val stateFactor =
            when (state) {
                PatternState.AKTIV -> 1.0
                PatternState.NEU -> 0.6
                PatternState.BESSER -> 0.45
                PatternState.BEHOBEN -> 0.08
            }
        val basic = if (category in BASIC) 1.2 else 1.0
        return (1.0 + ln(1.0 + total)) * (0.35 + 0.65 * recency) * stateFactor * basic
    }

    fun isDue(now: Long): Boolean = state != PatternState.BEHOBEN && dueAt <= now

    /** One line for prompts: rule, how often, examples. */
    fun promptLine(): String {
        val ex = examples.take(2).joinToString("; ") { "\"${it.wrong}\" → \"${it.right}\"" }
        return "$title (${categoryLabel}, ${total}× in $sessions Sitzungen, ${state.label})" + if (ex.isNotBlank()) ": $ex" else ""
    }

    fun toJson(): JSONObject =
        JSONObject().put("key", key).put("rule", rule).put("category", category).put("examples", examples.toJsonArray { it.toJson() })
            .put("total", total).put("sessions", sessions).put("firstSeen", firstSeen).put("lastSeen", lastSeen)
            .put("cleanStreak", cleanStreak).put("lastSession", lastSession).put("fromKlausur", fromKlausur)
            .put("dueAt", dueAt).put("intervalDays", intervalDays).put("ease", ease).put("drills", drills)
            .put("drillsOk", drillsOk).put("lastDrillAt", lastDrillAt).put("drillStreak", drillStreak)

    companion object {
        const val DAY = 86_400_000L

        /** Mistakes an examiner notices at once: they cost the most in the Darstellungsleistung. */
        private val BASIC = setOf("SVA", "VERB_TENSE", "VERB_FORM", "WORD_ORDER")

        fun fromJson(o: JSONObject) =
            ErrorPattern(
                key = o.optString("key"),
                rule = o.optString("rule"),
                category = o.optString("category", "OTHER"),
                examples = o.optJSONArray("examples").objects().map { PatternExample.fromJson(it) },
                total = o.optInt("total"),
                sessions = o.optInt("sessions"),
                firstSeen = o.optLong("firstSeen"),
                lastSeen = o.optLong("lastSeen"),
                cleanStreak = o.optInt("cleanStreak"),
                lastSession = o.optString("lastSession"),
                fromKlausur = o.optInt("fromKlausur"),
                dueAt = o.optLong("dueAt"),
                intervalDays = o.optDouble("intervalDays", 0.0),
                ease = o.optDouble("ease", 2.3),
                drills = o.optInt("drills"),
                drillsOk = o.optInt("drillsOk"),
                lastDrillAt = o.optLong("lastDrillAt"),
                drillStreak = o.optInt("drillStreak"),
            )
    }
}

/**
 * The error memory: groups every mistake of the student (live hints, debriefs, Klausuren) by its rule and follows each
 * kind over time. The coach interrupts for active patterns, the examiner asks questions that need these structures,
 * the speaking training drills them with spaced repetition, and the plan and the statistics show what is still open.
 */
class ErrorMemoryStore(root: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(root, "error_memory.json")
    private val ingestedFile = File(root, "error_memory_sessions.json")
    private val _patterns = MutableStateFlow(load())
    val patterns: StateFlow<List<ErrorPattern>> = _patterns.asStateFlow()
    private val ingested: MutableSet<String> = loadIngested()

    private fun load(): List<ErrorPattern> =
        de.abicoach.data.SafeFile.load(file) { text -> JSONArray(text).objects().map { ErrorPattern.fromJson(it) }.filter { it.key.isNotBlank() } } ?: emptyList()

    private fun loadIngested(): MutableSet<String> = de.abicoach.data.SafeFile.load(ingestedFile) { JSONArray(it).strings().toMutableSet() } ?: mutableSetOf()

    @Synchronized
    private fun save(list: List<ErrorPattern>) {
        _patterns.value = list
        de.abicoach.data.SafeFile.write(file, JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString())
    }

    /**
     * Adds the mistakes of one session (or one Klausur). [assessableWords] is how much the student said: only a session
     * with enough speech counts as "clean" for the patterns that did not come back.
     */
    @Synchronized
    fun ingest(errors: List<LiveError>, sessionId: String, source: String, at: Long, assessableWords: Int) {
        if (sessionId.isNotBlank() && !ingested.add(sessionId)) return
        de.abicoach.data.SafeFile.write(ingestedFile, JSONArray(ingested.toList().takeLast(500)).toString())
        val list = _patterns.value.toMutableList()
        val seen = HashSet<String>()
        val clean =
            errors.filter { it.severity != "content" && it.category != "CONTENT" && it.correction.isNotBlank() }
                .distinctBy { norm(it.quote) + "|" + norm(it.correction) }
        clean.forEach { e ->
            val index = findIndex(list, e)
            val example = PatternExample(e.quote.take(120), e.correction.take(120), at, source)
            if (index >= 0) {
                val p = list[index]
                val newSession = p.lastSession != sessionId
                list[index] =
                    p.copy(
                        rule = p.rule.ifBlank { e.rule.trim().take(60) },
                        examples = (listOf(example) + p.examples).distinctBy { norm(it.right) }.take(6),
                        total = p.total + 1,
                        sessions = p.sessions + if (newSession) 1 else 0,
                        lastSeen = max(p.lastSeen, at),
                        cleanStreak = 0,
                        lastSession = sessionId,
                        fromKlausur = p.fromKlausur + if (source == SOURCE_KLAUSUR) 1 else 0,
                        // A fresh mistake makes the pattern due again in the speaking training.
                        dueAt = min(p.dueAt, at),
                        intervalDays = if (newSession) p.intervalDays * 0.5 else p.intervalDays,
                        drillStreak = 0,
                    )
                seen += p.key
            } else {
                val key = keyOf(e.category, e.rule)
                list +=
                    ErrorPattern(
                        key = key,
                        rule = e.rule.trim().take(60),
                        category = e.category,
                        examples = listOf(example),
                        total = 1,
                        sessions = 1,
                        firstSeen = at,
                        lastSeen = at,
                        cleanStreak = 0,
                        lastSession = sessionId,
                        fromKlausur = if (source == SOURCE_KLAUSUR) 1 else 0,
                        dueAt = at,
                    )
                seen += key
            }
        }
        if (assessableWords >= MIN_WORDS && source != SOURCE_KLAUSUR) {
            for (i in list.indices) {
                val p = list[i]
                if (p.key !in seen && p.lastSeen < at) list[i] = p.copy(cleanStreak = p.cleanStreak + 1)
            }
        }
        save(list.sortedByDescending { it.weight(at) }.take(MAX_PATTERNS))
    }

    /** The pattern a mistake belongs to (same rule, or a very similar one in the same category). */
    fun matching(e: LiveError): ErrorPattern? = _patterns.value.getOrNull(findIndex(_patterns.value, e))

    /** A known, not yet fixed pattern: the coach interrupts for it even if the mistake looks small. */
    fun isActive(e: LiveError, now: Long = System.currentTimeMillis()): Boolean {
        val p = matching(e) ?: return false
        return when (p.state) {
            PatternState.AKTIV -> true
            PatternState.BESSER -> p.total >= 3
            PatternState.NEU -> now - p.lastSeen < 14 * ErrorPattern.DAY
            PatternState.BEHOBEN -> false
        }
    }

    /** The patterns that cost the most points right now (fixed ones excluded). */
    fun top(limit: Int, now: Long = System.currentTimeMillis()): List<ErrorPattern> =
        _patterns.value.filter { it.state != PatternState.BEHOBEN }.sortedByDescending { it.weight(now) }.take(limit)

    /** Patterns due in the speaking training, most important first. */
    fun due(now: Long = System.currentTimeMillis()): List<ErrorPattern> =
        _patterns.value.filter { it.isDue(now) }.sortedByDescending { it.weight(now) }

    fun get(key: String): ErrorPattern? = _patterns.value.firstOrNull { it.key == key }

    /** Result of the speaking training for one pattern: the interval grows when it was right, starts again when not. */
    @Synchronized
    fun reviewDrill(key: String, ok: Boolean, now: Long = System.currentTimeMillis()) {
        val list = _patterns.value.map { p ->
            if (p.key != key) return@map p
            if (ok) {
                val interval = (if (p.intervalDays < 1.0) 2.0 else p.intervalDays * p.ease).coerceAtMost(60.0)
                p.copy(
                    intervalDays = interval,
                    ease = min(2.8, p.ease + 0.05),
                    dueAt = now + (interval * ErrorPattern.DAY).roundToLong(),
                    drills = p.drills + 1,
                    drillsOk = p.drillsOk + 1,
                    lastDrillAt = now,
                    drillStreak = p.drillStreak + 1,
                )
            } else {
                p.copy(
                    intervalDays = 0.0,
                    ease = max(1.4, p.ease - 0.2),
                    dueAt = now + 18 * 3_600_000L,
                    drills = p.drills + 1,
                    lastDrillAt = now,
                    drillStreak = 0,
                )
            }
        }
        save(list)
    }

    /** Short text for the coach, the examiner and the plan. */
    fun summary(limit: Int = 5, now: Long = System.currentTimeMillis()): String {
        val top = top(limit, now)
        if (top.isEmpty()) return ""
        return top.joinToString("\n") { "- " + it.promptLine() }
    }

    /** English lines for the examiner prompt. */
    /** The top patterns as lines for the examiner and the coach; [stillHard] adds the Fehler-Werkstatt tutor's note. */
    fun examinerLines(limit: Int = 5, now: Long = System.currentTimeMillis(), stillHard: (String) -> String = { "" }): List<String> =
        top(limit, now).map { p ->
            val ex = p.examples.firstOrNull()?.let { " (e.g. \"${it.wrong}\" instead of \"${it.right}\")" }.orEmpty()
            val hard = stillHard(p.key).takeIf { it.isNotBlank() }?.let { " – still hard for him (tutor's note, German): $it" }.orEmpty()
            p.rule.ifBlank { Knowledge.categoryEnglish(p.category) } + ex + hard
        }

    @Synchronized
    fun reset() {
        ingested.clear()
        de.abicoach.data.SafeFile.delete(ingestedFile)
        save(emptyList())
        de.abicoach.data.SafeFile.dropBackup(file)
    }

    /** Puts prepared patterns in (emulator test). */
    @Synchronized
    fun replaceAll(list: List<ErrorPattern>) = save(list)

    private fun findIndex(list: List<ErrorPattern>, e: LiveError): Int {
        val key = keyOf(e.category, e.rule)
        val exact = list.indexOfFirst { it.key == key }
        if (exact >= 0) return exact
        val tokens = tokens(e.rule)
        if (tokens.isEmpty()) return list.indexOfFirst { it.category == e.category && it.rule.isBlank() }
        var best = -1
        var bestScore = 0.0
        list.forEachIndexed { i, p ->
            if (p.category != e.category) return@forEachIndexed
            val other = tokens(p.rule)
            if (other.isEmpty()) return@forEachIndexed
            val score = tokens.intersect(other).size.toDouble() / tokens.union(other).size
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return if (bestScore >= 0.6) best else -1
    }

    companion object {
        const val SOURCE_SESSION = "Sitzung"
        const val SOURCE_KLAUSUR = "Klausur"
        private const val MIN_WORDS = 120
        private const val MAX_PATTERNS = 120
        private val STOP = setOf("vs", "versus", "the", "a", "an", "of", "with", "for", "in", "and", "or", "to", "use", "using", "wrong")

        private fun norm(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

        private fun tokens(rule: String): Set<String> =
            rule.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(" ").filter { it.isNotBlank() && it !in STOP }.toSet()

        /** "SVA" + "Third person -s" → "SVA:third person s". */
        fun keyOf(category: String, rule: String): String {
            val r = rule.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
            return "${category.ifBlank { "OTHER" }}:" + r.ifBlank { "general" }
        }
    }
}
