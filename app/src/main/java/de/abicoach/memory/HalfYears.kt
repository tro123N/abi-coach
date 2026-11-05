package de.abicoach.memory

import de.abicoach.AbiCoachApp
import de.abicoach.data.Knowledge
import de.abicoach.learn.LearningBrain

/**
 * Which topic field the course covered in which Kurshalbjahr. The oral Abitur must not stay with the subject of one
 * half-year (§ 38 APO-GOSt): in a simulation, part 2 therefore takes topic fields from other half-years than part 1.
 * The student can set the half-years himself; otherwise they come from the uploaded material.
 */
object HalfYears {
    /** The half-years of the Qualifikationsphase, which the Abitur covers. */
    val Q = listOf("Q1.1", "Q1.2", "Q2.1", "Q2.2")

    /** Topic field → half-year: as the student set it, else as the material shows it. */
    fun mapping(app: AbiCoachApp): Map<Int, String> {
        val out = HashMap<Int, String>()
        app.settings.halfYearTopics.entries.sortedBy { Q.indexOf(it.key).let { i -> if (i < 0) 9 else i } }.forEach { (half, topics) ->
            topics.forEach { t -> out.putIfAbsent(t, half) }
        }
        inferred(app).forEach { (t, half) -> out.putIfAbsent(t, half) }
        return out
    }

    /** What the material says: for each topic field the half-year of most of its passages. */
    fun inferred(app: AbiCoachApp): Map<Int, String> {
        val counts = HashMap<Int, HashMap<String, Int>>()
        app.memory.docs.value.filter { it.halbjahr in Q }.forEach { d ->
            d.topicCounts.forEachIndexed { t, c ->
                if (c > 0) counts.getOrPut(t) { HashMap() }.merge(d.halbjahr, c, Int::plus)
            }
        }
        return counts.mapNotNull { (t, byHalf) -> byHalf.maxByOrNull { it.value }?.let { t to it.key } }.toMap()
    }

    /** The topic fields of a half-year: as set, or as found in the material. */
    fun topicsOf(app: AbiCoachApp, half: String): Set<Int> = app.settings.halfYearTopics[half] ?: inferred(app).filterValues { it == half }.keys

    fun label(app: AbiCoachApp, topic: Int): String? = mapping(app)[topic]

    /**
     * Topic fields for the exam conversation: the ones the student needs most – from other half-years than the topic
     * of part 1 and, where possible, from different half-years among themselves, so the whole course is covered.
     */
    fun part2Topics(app: AbiCoachApp, part1: Int, count: Int = 2, exclude: Set<Int> = emptySet()): List<Int> {
        val map = mapping(app)
        val needs = LearningBrain.topicNeeds(app, exclude = exclude + part1).map { it.first }
        if (needs.isEmpty()) return Knowledge.TOPICS.map { it.id }.filter { it != part1 && it !in exclude }.shuffled().take(count)
        val used = HashSet<String>()
        map[part1]?.let { used += it }
        val out = ArrayList<Int>()
        repeat(count) {
            // First choice: a half-year that is not covered yet; then any other half-year; then simply the most needed.
            val next =
                needs.firstOrNull { t -> t !in out && map[t] != null && map[t] !in used }
                    ?: needs.firstOrNull { t -> t !in out && map[t] == null }
                    ?: needs.firstOrNull { t -> t !in out }
                    ?: return@repeat
            out += next
            map[next]?.let { used += it }
        }
        return out
    }

    /** One line for the prompts: which half-year each topic comes from (empty if unknown). */
    fun promptNote(app: AbiCoachApp, part1: Int, part2: List<Int>): String {
        val map = mapping(app)
        if (map.isEmpty()) return ""
        val p1 = map[part1] ?: return ""
        val p2 = part2.mapNotNull { t -> map[t]?.let { "${Knowledge.topic(t)?.english ?: t} ($it)" } }
        if (p2.isEmpty()) return ""
        return "\nThe course covered the topic of part 1 in $p1; the part-2 topics come from other terms of the course: ${p2.joinToString("; ")}. " +
            "As in the Abitur, the conversation must not stay with the subject of one term: link the terms."
    }
}
