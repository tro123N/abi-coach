package de.abicoach.session

import de.abicoach.data.Debrief
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import kotlin.math.roundToInt

/**
 * How the student used the time of the presentation. A classic way to lose points in part 1 is a long summary that
 * leaves no room for the comment – the task that shows the most independent thinking. The grader notes where each task
 * begins; from that this object works out how much of the talk went to each task, compares it with a rule of thumb
 * and says in one or two sentences what to change. Plain arithmetic, no AI.
 */
object Pacing {
    /** Rule of thumb for a presentation of about ten minutes: short summary, room for analysis and comment. */
    val TARGET = mapOf(1 to 0.25, 2 to 0.40, 3 to 0.35)

    private val NAMES = mapOf(1 to "Zusammenfassung", 2 to "Analyse", 3 to "Stellungnahme")

    data class Part(val number: Int, val seconds: Int, val share: Double, val target: Double) {
        val name: String get() = NAMES[number] ?: "Aufgabe $number"
        val percent: Int get() = (share * 100).roundToInt()
        val targetPercent: Int get() = (target * 100).roundToInt()
    }

    data class Split(val parts: List<Part>, val totalSeconds: Int, val advice: List<String>, val balanced: Boolean)

    private fun mmss(sec: Int) = "${sec / 60}:${"%02d".format(sec % 60)}"

    /** The time split of a whole presentation, or null if it cannot be told (single task, no task starts found, too short). */
    fun split(record: SessionRecord, debrief: Debrief): Split? {
        if (!record.kind.hasPart1 || record.focusTask != 0 || record.part1Sec < 60) return null
        val text = Prompts.normalize(record.utterances.filter { it.speaker == Speaker.STUDENT && it.part <= 1 }.joinToString(" ") { it.text })
        val totalWords = text.split(' ').count { it.isNotBlank() }
        if (totalWords < 60) return null
        // Where each task begins, as a word position in the talk.
        val starts = ArrayList<Pair<Int, Int>>()
        debrief.segments.sortedBy { it.first }.distinctBy { it.first }.forEach { (number, firstWords) ->
            val words = Prompts.normalize(firstWords).split(' ').filter { it.isNotBlank() }
            // The exact beginning first; if the transcript differs a little, the first three words are enough.
            val at = sequenceOf(words, words.take(3)).filter { it.size >= 2 }.map { text.indexOf(it.joinToString(" ")) }.firstOrNull { it >= 0 } ?: return@forEach
            starts += number to text.substring(0, at).split(' ').count { it.isNotBlank() }
        }
        // The tasks must follow each other in the talk; a start that jumps back is a wrong match and is left out.
        val ordered = ArrayList<Pair<Int, Int>>()
        starts.forEach { s -> if (ordered.isEmpty() || s.second > ordered.last().second) ordered += s }
        if (ordered.size < 2) return null
        // Everything before the first found start (greeting, introduction) belongs to the first task.
        val bounds = ordered.mapIndexed { i, s -> if (i == 0) s.first to 0 else s }
        val parts =
            bounds.mapIndexed { i, (number, from) ->
                val to = bounds.getOrNull(i + 1)?.second ?: totalWords
                val share = (to - from).toDouble() / totalWords
                Part(number, (share * record.part1Sec).roundToInt(), share, TARGET[number] ?: 0.33)
            }
        val advice = ArrayList<String>()
        val byNumber = parts.associateBy { it.number }
        val expectsThree = (record.task?.tasks?.size ?: 3) >= 3
        if (expectsThree && 3 !in byNumber) {
            advice += "Für die Stellungnahme (Aufgabe 3) blieb keine Zeit. Sie zeigt dein eigenes Urteil und bringt die meisten Punkte – plane sie fest ein."
        }
        byNumber[1]?.takeIf { it.share > 0.38 }?.let {
            advice += "Die Zusammenfassung nahm ${it.percent} % deiner Zeit. Kürze sie auf etwa ein Viertel (2–3 Minuten), damit Analyse und Stellungnahme Platz haben."
        }
        byNumber[2]?.takeIf { it.share < 0.25 }?.let {
            advice += "Die Analyse war mit ${it.percent} % kurz. Nimm dir dafür am meisten Zeit: Mittel benennen, Textstelle zeigen, Wirkung erklären."
        }
        byNumber[3]?.takeIf { it.share < 0.20 }?.let {
            advice += "Die Stellungnahme war mit ${it.percent} % zu kurz. Sie sollte etwa ein Drittel bekommen: Position, zwei Gründe mit Beispiel, ein Gegenargument, Fazit."
        }
        if (record.part1Sec < 360) {
            advice += "Du hast ${mmss(record.part1Sec)} von etwa 10 Minuten genutzt. Mehr Textbelege und eigene Beispiele füllen die Zeit sinnvoll."
        }
        val balanced = advice.isEmpty()
        if (balanced) advice += "Gute Zeitaufteilung: kurze Zusammenfassung, genug Raum für Analyse und Stellungnahme."
        return Split(parts, record.part1Sec, advice, balanced)
    }

    /**
     * The hint during the presentation: which task should be on now if the time is used well.
     * [elapsed] and [target] in seconds.
     */
    fun guide(elapsed: Int, target: Int): String {
        if (target <= 0) return ""
        val first = (target * TARGET.getValue(1)).roundToInt()
        val second = (target * (TARGET.getValue(1) + TARGET.getValue(2))).roundToInt()
        return when {
            elapsed < first -> "Richtwert: Aufgabe 1 (Zusammenfassung) bis ${mmss(first)}"
            elapsed < second -> "Richtwert: jetzt Aufgabe 2 (Analyse) bis ${mmss(second)}"
            elapsed < target -> "Richtwert: jetzt Aufgabe 3 (Stellungnahme) bis ${mmss(target)}"
            else -> "Zeit erreicht – komm zum Schluss."
        }
    }
}
