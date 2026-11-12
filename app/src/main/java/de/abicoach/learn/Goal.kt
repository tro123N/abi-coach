package de.abicoach.learn

import java.util.Locale
import kotlin.math.max

/**
 * The student's own goal: how many points the oral exam should bring. The whole app steers by it – the plan
 * practises what still lies below the goal, the overview shows how far it is, and the forecast says whether the
 * current pace is enough.
 */
object Goal {
    enum class Status { UNSET, NO_DATA, REACHED, ON_TRACK, TIGHT, BEHIND, OPEN }

    /** [headline] and [detail] are ready to show; [gap] is what is still missing (0 when reached). */
    data class State(val target: Int, val status: Status, val gap: Double, val headline: String, val detail: String)

    /** The choices offered: from "pass" to the best grade. */
    val CHOICES = listOf(5, 7, 8, 9, 10, 11, 12, 13, 14, 15)

    private fun p(value: Double) = String.format(Locale.GERMANY, "%.1f", value)

    /**
     * Where the student stands against the goal. [current] is the forecast today, [projected] the forecast for the
     * exam day at the current pace (both null when not known yet).
     */
    fun state(target: Int, current: Double?, projected: Double?): State {
        if (target <= 0) {
            return State(0, Status.UNSET, 0.0, "Ziel festlegen", "Wie viele Punkte willst du erreichen? Danach richtet sich dein Plan.")
        }
        if (current == null) {
            return State(target, Status.NO_DATA, 0.0, "Ziel: $target Punkte", "Nach der Einstufung siehst du, wie weit es noch ist.")
        }
        val gap = max(0.0, target - current)
        if (gap <= 0.0) {
            return State(target, Status.REACHED, 0.0, "Ziel $target Punkte erreicht", "Jetzt absichern: Der Plan hält dein Niveau und prüft es in ganzen Prüfungen.")
        }
        val head = "Ziel $target Punkte · noch ${p(gap)}"
        return when {
            projected == null -> State(target, Status.OPEN, gap, head, "Der Plan übt zuerst, was noch unter deinem Ziel liegt.")
            projected >= target -> State(target, Status.ON_TRACK, gap, head, "Du bist auf Kurs: Bei diesem Tempo schaffst du es bis zur Prüfung.")
            projected >= target - 1.0 -> State(target, Status.TIGHT, gap, head, "Es wird knapp: Bei diesem Tempo landest du bei etwa ${p(projected)}. Lass keinen Tag aus.")
            else -> State(target, Status.BEHIND, gap, head, "Bei diesem Tempo landest du bei etwa ${p(projected)}. Übe mehr pro Tag, dann ist das Ziel drin.")
        }
    }

    /**
     * How much a skill at [score] points still counts for the plan. Without a goal every point up to 15 counts the
     * same. With a goal, what lies below it (plus one point of safety) counts fully and what is already above it
     * only a little – so the time goes where the goal is decided.
     */
    fun gap(score: Double, target: Int): Double {
        if (target <= 0) return 15.0 - score
        val top = (target + 1.0).coerceAtMost(15.0)
        val below = max(0.0, top - score)
        val above = max(0.0, 15.0 - max(score, top))
        return below + 0.2 * above
    }
}
