package de.abicoach.session

import de.abicoach.AbiCoachApp
import de.abicoach.data.Debrief
import de.abicoach.data.Knowledge
import de.abicoach.data.Progress
import de.abicoach.data.SessionRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Compares a fresh debrief with the one before it (or with the first attempt, after a second attempt): which kinds of
 * mistakes did not come back, which did. This is plain bookkeeping done after the grading, so the grader itself
 * never sees earlier points and judges every session on its own.
 */
object ProgressMaker {
    private fun normal(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    fun make(app: AbiCoachApp, record: SessionRecord, debrief: Debrief): Progress? {
        val rated = app.store.sessions.value.filter { it.id != record.id && it.debrief != null && it.startedAt < record.startedAt }
        val base =
            (if (record.retryOf.isNotBlank()) rated.firstOrNull { it.id == record.retryOf } else null)
                ?: rated.maxByOrNull { it.startedAt }
                ?: return null
        val before = base.debrief ?: return null
        val older = rated.filter { it.id != base.id }.sortedByDescending { it.startedAt }.take(3)
        val chain = listOf(base) + older

        val fixed = ArrayList<String>()
        val still = ArrayList<String>()

        // The very same mistake again.
        val oldCorrections = before.errors.map { normal(it.correction) }.filter { it.isNotEmpty() }.toSet()
        debrief.errors.filter { normal(it.correction) in oldCorrections }.distinctBy { normal(it.correction) }.take(2).forEach {
            still += "„${it.quote}“ → „${it.correction}“ – genau dieser Fehler war schon beim letzten Mal dabei."
        }

        // Kinds of mistakes: gone or back?
        val nowKinds = debrief.errors.map { it.category }.toSet()
        val oldKinds =
            before.errors.filter { it.severity != "minor" }.map { it.category }.ifEmpty { before.errors.map { it.category } }
                .filter { it != "OTHER" }.distinct().take(4)
        oldKinds.forEach { kind ->
            val label = Knowledge.categoryLabel(kind)
            if (kind !in nowKinds) {
                fixed += "$label: diesmal kein Fehler dieser Art."
            } else {
                val inARow = 1 + chain.takeWhile { s -> s.debrief?.errors?.any { it.category == kind } == true }.size
                val times = debrief.errors.count { it.category == kind }
                still += "$label: kam wieder vor (${times}×)" + if (inARow >= 3) " – schon in $inARow Sitzungen in Folge." else "."
            }
        }

        val taskNumber = if (record.retryOf.isNotBlank()) record.focusTask else 0
        var taskBefore = -1
        var taskAfter = -1
        if (taskNumber in 1..3) {
            taskBefore = before.taskReviews.firstOrNull { it.number == taskNumber }?.points ?: -1
            taskAfter = debrief.taskReviews.firstOrNull { it.number == taskNumber }?.points ?: -1
        }
        val date = Instant.ofEpochMilli(base.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM."))
        return Progress(
            comparedTo = base.id,
            comparedLabel = "${base.title} vom $date",
            comparedPoints = before.totalPoints,
            fixed = fixed,
            still = still,
            taskNumber = taskNumber,
            taskBefore = taskBefore,
            taskAfter = taskAfter,
        )
    }

    /** One short German paragraph about the progress, for the AI coach and the daily plan. */
    fun note(app: AbiCoachApp): String {
        val last = app.store.sessions.value.filter { it.debrief?.progress != null }.maxByOrNull { it.startedAt } ?: return ""
        val p = last.debrief?.progress ?: return ""
        val parts = ArrayList<String>()
        if (p.fixed.isNotEmpty()) parts += "Zuletzt gelöst: " + p.fixed.joinToString(" ") { it.removeSuffix(".") }
        if (p.still.isNotEmpty()) parts += "Noch hartnäckig: " + p.still.joinToString(" ")
        return parts.joinToString("\n")
    }
}
