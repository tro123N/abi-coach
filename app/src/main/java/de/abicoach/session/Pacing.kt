package de.abicoach.session

import kotlin.math.roundToInt

/**
 * The time of the presentation. A classic way to lose points in part 1 is a long summary that leaves no room for the
 * comment – the task that shows the most independent thinking. From the seconds spent on each task this object says
 * in one or two sentences what to change, and during the presentation it tells which task should be on now.
 * Plain arithmetic, no AI.
 */
object Pacing {
    /** Rule of thumb for the three tasks of a whole presentation, in seconds: short summary, room for analysis and comment. */
    val TARGET_SECONDS = mapOf(1 to 120, 2 to 240, 3 to 180)

    private fun mmss(sec: Int) = "${sec / 60}:${"%02d".format(sec % 60)}"

    private fun share(number: Int): Double = TARGET_SECONDS.getValue(number).toDouble() / TARGET_SECONDS.values.sum()

    /**
     * What to change about the use of time. [perTask] holds task number and seconds spent on it (as far as the tasks
     * were found in the talk), [totalSeconds] the length of the whole presentation, [expectsThree] whether the
     * material had three tasks. An empty list means: nothing to complain about.
     */
    fun advice(perTask: List<Pair<Int, Int>>, totalSeconds: Int, expectsThree: Boolean = true): List<String> {
        val advice = ArrayList<String>()
        val found = perTask.filter { it.second > 0 }.toMap()
        val sum = found.values.sum()
        if (found.size >= 2 && sum > 0) {
            fun percent(n: Int): Int? = found[n]?.let { (it * 100.0 / sum).roundToInt() }
            if (expectsThree && 3 !in found) {
                advice += "Für die Stellungnahme (Aufgabe 3) blieb keine Zeit. Sie zeigt dein eigenes Urteil und bringt die meisten Punkte – plane sie fest ein."
            }
            percent(1)?.takeIf { it > 38 }?.let {
                advice += "Die Zusammenfassung nahm $it % deiner Zeit. Kürze sie auf etwa 2 Minuten, damit Analyse und Stellungnahme Platz haben."
            }
            percent(2)?.takeIf { it < 25 }?.let {
                advice += "Die Analyse war mit $it % kurz. Nimm dir dafür am meisten Zeit: Mittel benennen, Textstelle zeigen, Wirkung erklären."
            }
            percent(3)?.takeIf { it < 20 }?.let {
                advice += "Die Stellungnahme war mit $it % zu kurz. Sie sollte etwa ein Drittel bekommen: Position, zwei Gründe mit Beispiel, ein Gegenargument, Fazit."
            }
        }
        if (totalSeconds in 1 until 360) {
            advice += "Du hast ${mmss(totalSeconds)} von etwa 10 Minuten genutzt. Mehr Textbelege und eigene Beispiele füllen die Zeit sinnvoll."
        }
        return advice
    }

    /**
     * The hint during the presentation: which task should be on now if the time is used well.
     * [elapsed] and [target] in seconds.
     */
    fun guide(elapsed: Int, target: Int): String {
        if (target <= 0) return ""
        val first = (target * share(1)).roundToInt()
        val second = (target * (share(1) + share(2))).roundToInt()
        return when {
            elapsed < first -> "Richtwert: Aufgabe 1 (Zusammenfassung) bis ${mmss(first)}"
            elapsed < second -> "Richtwert: jetzt Aufgabe 2 (Analyse) bis ${mmss(second)}"
            elapsed < target -> "Richtwert: jetzt Aufgabe 3 (Stellungnahme) bis ${mmss(target)}"
            else -> "Zeit erreicht – komm zum Schluss."
        }
    }
}
