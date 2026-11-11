package de.abicoach.session

import de.abicoach.data.Debrief
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.profile.Area
import de.abicoach.profile.Skill
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The plausibility check after the AI's grading. An AI grades well, but not always consistently: sometimes the points
 * of an area do not fit the competences it rated itself, sometimes a presentation that left out a whole task still
 * gets a "gut", and a session of three sentences is rated as confidently as a whole exam. The guard applies a few
 * fixed, explainable rules to every grading, corrects only what clearly contradicts itself, says what it changed,
 * and tells the profile how much this session may count. It is plain arithmetic – the same input always gives the
 * same result.
 */
object GradeGuard {
    /** The checked grading: [notes] say in German what was changed and why; [reliability] scales the weight in the profile. */
    data class Verdict(val debrief: Debrief, val notes: List<String>, val reliability: Double)

    /** How far the points of an area may be from the average of its competences. */
    private const val TOLERANCE = 2.0

    /** Below this many words a session does not count for the profile at all. */
    const val MIN_WORDS = 15

    /** Below this many words there is too little to judge. */
    const val FEW_WORDS = 40

    /** Below this many words a rating is still shaky. */
    const val SOME_WORDS = 120

    fun words(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

    fun review(record: SessionRecord, graded: Debrief): Verdict {
        val notes = ArrayList<String>()
        val skills = graded.skills.filterValues { it in 0..15 }
        var inhalt = graded.inhaltPoints.coerceIn(0, 15)
        var darstellung = graded.darstellungPoints.coerceIn(0, 15)

        // 1. The points of an area must fit the competences the grader rated itself.
        fun align(area: Area, points: Int): Int {
            val rated = skills.filterKeys { it.area == area }.values
            if (rated.size < 2) return points
            val mean = rated.average()
            if (abs(points - mean) <= TOLERANCE) return points
            val fixed = (if (points > mean) mean + TOLERANCE else mean - TOLERANCE).roundToInt().coerceIn(0, 15)
            if (fixed != points) {
                notes += "${area.label}: $points → $fixed Punkte, weil die einzelnen Kompetenzen dieses Bereichs im Schnitt bei ${"%.1f".format(mean).replace('.', ',')} liegen."
            }
            return fixed
        }
        inhalt = align(Area.INHALT, inhalt)
        darstellung = align(Area.DARSTELLUNG, darstellung)

        // 2. A whole presentation needs all three tasks: without the comment (AFB III) no "gut", with two tasks missing no "befriedigend".
        val wholePresentation = record.kind.hasPart1 && record.focusTask == 0 && (record.task?.tasks?.size ?: 0) >= 3 && graded.taskReviews.isNotEmpty()
        if (wholePresentation) {
            val done = graded.taskReviews.filter { it.number in 1..3 && it.points > 0 }.map { it.number }.toSet()
            val missing = (1..3).filter { it !in done }
            val bonus = if (record.kind == SessionKind.SIMULATION || record.kind == SessionKind.PLACEMENT) 1 else 0
            val cap =
                when {
                    missing.size >= 2 -> 6 + bonus
                    missing.isNotEmpty() -> 9 + bonus
                    else -> 15
                }
            if (inhalt > cap) {
                notes += "Inhalt: $inhalt → $cap Punkte, weil Aufgabe ${missing.joinToString(" und ")} nicht bearbeitet wurde – im Abitur müssen alle drei Aufgaben vorkommen."
                inhalt = cap
            }
        }

        // 3. How much there was to judge decides how much the session counts for the forecast.
        val count = words(record.studentText)
        var reliability = 1.0
        when {
            count < MIN_WORDS -> {
                reliability = 0.0
                notes += "Zu wenig gesprochen ($count Wörter): Diese Sitzung zählt nicht für deine Prognose."
            }
            count < FEW_WORDS -> {
                reliability = 0.3
                notes += "Sehr wenig gesprochen ($count Wörter): Die Punkte sind unsicher und zählen kaum für deine Prognose."
            }
            count < SOME_WORDS -> {
                reliability = 0.6
                notes += "Wenig gesprochen ($count Wörter): Die Punkte zählen nur zum Teil für deine Prognose."
            }
        }
        // A whole presentation that is much shorter than the ten minutes of the exam says less about the real exam.
        if (wholePresentation && record.kind == SessionKind.PART1 && record.part1Sec in 1 until 240) {
            reliability *= 0.8
            notes += "Der Vortrag war nur ${record.part1Sec / 60}:${"%02d".format(record.part1Sec % 60)} Min. lang (im Abitur etwa 10 Min.) – übe, die Zeit zu füllen."
        }

        val total = (Area.INHALT.weight * inhalt + Area.DARSTELLUNG.weight * darstellung).roundToInt().coerceIn(0, 15)
        val checked =
            graded.copy(
                inhaltPoints = inhalt,
                darstellungPoints = darstellung,
                totalPoints = total,
                skills = skills,
                guardNotes = notes,
                reliability = reliability.coerceIn(0.0, 1.0),
            )
        return Verdict(checked, notes, checked.reliability)
    }
}
