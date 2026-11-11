package de.abicoach.learn

import android.content.Context
import de.abicoach.data.Observation
import de.abicoach.data.objects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One thing the app has learned about the student's habits, confirmed [count] times. [overcome] counts the sessions
 * in which the student reached the goal set against this habit: after two it counts as overcome.
 */
data class Insight(val text: String, val area: String, val polarity: String, val count: Int, val firstAt: Long, val lastAt: Long, val overcome: Int = 0) {
    val strength: Boolean get() = polarity == "staerke"

    val solved: Boolean get() = !strength && overcome >= 2

    val areaLabel: String
        get() =
            when (area) {
                "inhalt" -> "Inhalt"
                "sprache" -> "Sprache"
                "vortrag" -> "Vortrag"
                "gespraech" -> "Gespräch"
                else -> "Strategie"
            }

    fun toJson(): JSONObject =
        JSONObject().put("text", text).put("area", area).put("polarity", polarity).put("count", count).put("firstAt", firstAt).put("lastAt", lastAt).put("overcome", overcome)

    companion object {
        fun fromJson(o: JSONObject) =
            Insight(o.optString("text"), o.optString("area"), o.optString("polarity", "schwaeche"), o.optInt("count", 1), o.optLong("firstAt"), o.optLong("lastAt"), o.optInt("overcome"))
    }
}

/**
 * The learner memory: what a good tutor remembers about a student beyond points and single mistakes – "gives very
 * short answers in the conversation", "repeats the content in task 2 instead of analysing it", "uses examples from
 * class well". The grader notices these after every session; similar observations are merged and counted, so the
 * picture sharpens over time. The plan, the session goals, the examiner and the coach use it, and a habit the student
 * has overcome twice drops out of the list.
 */
class InsightStore(context: Context) {
    private val file = File(context.filesDir, "insights.json")
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Insight>> = _all.asStateFlow()

    private fun load(): List<Insight> =
        de.abicoach.data.SafeFile.load(file) { text -> JSONArray(text).objects().map { Insight.fromJson(it) }.filter { it.text.isNotBlank() } } ?: emptyList()

    @Synchronized
    private fun save(list: List<Insight>) {
        _all.value = list
        de.abicoach.data.SafeFile.write(file, JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString())
    }

    @Synchronized
    fun add(observations: List<Observation>, at: Long) {
        if (observations.isEmpty()) return
        val list = _all.value.toMutableList()
        observations.filter { it.text.isNotBlank() }.forEach { o ->
            val words = tokens(o.text)
            val index =
                list.indices.maxByOrNull { i -> if (list[i].polarity == o.polarity) similarity(words, tokens(list[i].text)) else 0.0 }
                    ?.takeIf { i -> list[i].polarity == o.polarity && similarity(words, tokens(list[i].text)) >= 0.4 }
            if (index != null) {
                val old = list[index]
                // Seen again: it is not overcome after all.
                list[index] = old.copy(text = o.text.trim(), area = o.area.ifBlank { old.area }, count = old.count + 1, lastAt = at, overcome = 0)
            } else {
                list += Insight(o.text.trim(), o.area, o.polarity, 1, at, at)
            }
        }
        tidy(list, at)
    }

    /** The result of a session goal against a habit: reached counts towards "overcome", missed confirms the habit. */
    @Synchronized
    fun resolve(text: String, result: String, at: Long) {
        val list = _all.value.toMutableList()
        val index = list.indexOfFirst { it.text == text }.takeIf { it >= 0 } ?: return
        val old = list[index]
        list[index] =
            when (result) {
                "yes" -> old.copy(overcome = old.overcome + 1, lastAt = at)
                "no" -> old.copy(count = old.count + 1, overcome = 0, lastAt = at)
                else -> old
            }
        tidy(list, at)
    }

    private fun tidy(list: List<Insight>, at: Long) {
        // Old single observations fade away; confirmed ones stay.
        val limit = at - 75L * 86_400_000L
        save(list.filter { it.count >= 2 || it.lastAt >= limit }.sortedWith(compareByDescending<Insight> { it.count }.thenByDescending { it.lastAt }).take(30))
    }

    /** Habits that still cost points, the most confirmed first. */
    fun weaknesses(limit: Int = 4): List<Insight> = _all.value.filter { !it.strength && !it.solved }.take(limit)

    fun strengths(limit: Int = 3): List<Insight> = _all.value.filter { it.strength }.take(limit)

    fun solved(limit: Int = 3): List<Insight> = _all.value.filter { it.solved }.take(limit)

    /** Short German text for the prompts. */
    fun summary(limit: Int = 6): String {
        val weak = weaknesses(limit - 2)
        val strong = strengths(2)
        if (weak.isEmpty() && strong.isEmpty()) return ""
        val sb = StringBuilder()
        weak.forEach { sb.append("- Schwäche (").append(it.areaLabel).append(", ").append(it.count).append("× beobachtet): ").append(it.text).append('\n') }
        strong.forEach { sb.append("- Stärke (").append(it.areaLabel).append(", ").append(it.count).append("×): ").append(it.text).append('\n') }
        return sb.toString().trim()
    }

    /** The known observations, for the grader: the same habit should be named with the same words. */
    fun knownTexts(limit: Int = 10): List<String> = _all.value.filter { !it.solved }.take(limit).map { it.text }

    @Synchronized
    fun reset() {
        save(emptyList())
        de.abicoach.data.SafeFile.dropBackup(file)
    }

    @Synchronized
    fun replaceAll(list: List<Insight>) = save(list)

    companion object {
        private val STOP =
            setOf("der", "die", "das", "und", "oder", "aber", "noch", "nicht", "eine", "einen", "einem", "einer", "beim", "statt", "sehr", "seine", "seinen", "seiner", "mehr", "wenn", "dass", "auch", "oft", "häufig", "manchmal", "immer", "schon", "sich", "wird", "werden", "nutzt", "macht")

        private fun tokens(text: String): Set<String> =
            text.lowercase().split(Regex("[^a-zäöüß0-9]+")).filter { it.length >= 4 && it !in STOP }.map { it.take(7) }.toSet()

        private fun similarity(a: Set<String>, b: Set<String>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            return a.intersect(b).size.toDouble() / a.union(b).size
        }
    }
}
