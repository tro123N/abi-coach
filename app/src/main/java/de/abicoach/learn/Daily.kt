package de.abicoach.learn

import de.abicoach.data.SafeFile
import de.abicoach.data.objects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What a step of the day is for. The order of the roles is the order of the day. */
enum class StepRole(val label: String) {
    WIEDERHOLEN("Wiederholen"),
    FEHLER("Fehler beheben"),
    SCHWERPUNKT("Schwerpunkt"),
    FESTIGEN("Festigen"),
    EXTRA("Zusatz"),
}

/**
 * One step of today's plan. [kind] is the name of the [ActivityKind]; [ref] is the session that fulfilled it (so the
 * result of its evaluation can be written next to it later); [effect] says in one line what the step brought.
 */
data class DayStep(
    val id: String,
    val role: StepRole,
    val kind: String,
    val action: String,
    val title: String,
    val reason: String,
    val minutes: Int,
    val topic: Int = -1,
    val patternKey: String = "",
    val done: Boolean = false,
    val doneAt: Long = 0,
    val ref: String = "",
    val effect: String = "",
    /** Left open yesterday and taken along. */
    val carried: Boolean = false,
) {
    fun toJson(): JSONObject =
        JSONObject().put("id", id).put("role", role.name).put("kind", kind).put("action", action).put("title", title).put("reason", reason)
            .put("minutes", minutes).put("topic", topic).put("patternKey", patternKey).put("done", done).put("doneAt", doneAt)
            .put("ref", ref).put("effect", effect).put("carried", carried)

    companion object {
        fun fromJson(o: JSONObject) =
            DayStep(
                id = o.optString("id"),
                role = runCatching { StepRole.valueOf(o.optString("role")) }.getOrDefault(StepRole.EXTRA),
                kind = o.optString("kind"),
                action = o.optString("action"),
                title = o.optString("title"),
                reason = o.optString("reason"),
                minutes = o.optInt("minutes", 5),
                topic = o.optInt("topic", -1),
                patternKey = o.optString("patternKey"),
                done = o.optBoolean("done"),
                doneAt = o.optLong("doneAt"),
                ref = o.optString("ref"),
                effect = o.optString("effect"),
                carried = o.optBoolean("carried"),
            )
    }
}

/** Something the student did today, with what it brought – whether it was a step of the plan or not. */
data class LedgerEntry(val at: Long, val kind: String, val title: String, val effect: String, val ref: String = "", val planned: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("kind", kind).put("title", title).put("effect", effect).put("ref", ref).put("planned", planned)

    companion object {
        fun fromJson(o: JSONObject) = LedgerEntry(o.optLong("at"), o.optString("kind"), o.optString("title"), o.optString("effect"), o.optString("ref"), o.optBoolean("planned"))
    }
}

/** How a past day went (for the streak of finished plans and for what was left open). */
data class DaySummary(val date: String, val planned: Int, val done: Int, val open: List<String>) {
    val finished: Boolean get() = planned > 0 && done >= planned

    fun toJson(): JSONObject = JSONObject().put("date", date).put("planned", planned).put("done", done).put("open", JSONArray(open))

    companion object {
        fun fromJson(o: JSONObject) =
            DaySummary(o.optString("date"), o.optInt("planned"), o.optInt("done"), o.optJSONArray("open")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList())
    }
}

/** Today: the plan with what is done, and everything that counted today. */
data class Day(val date: String, val phase: String, val steps: List<DayStep>, val ledger: List<LedgerEntry> = emptyList()) {
    val doneCount: Int get() = steps.count { it.done }

    /** The step to do now: the first one that is still open. */
    val next: DayStep? get() = steps.firstOrNull { !it.done }

    val allDone: Boolean get() = steps.isNotEmpty() && steps.all { it.done }

    val minutesLeft: Int get() = steps.filter { !it.done }.sumOf { it.minutes }

    fun toJson(): JSONObject =
        JSONObject().put("date", date).put("phase", phase)
            .put("steps", JSONArray().also { a -> steps.forEach { a.put(it.toJson()) } })
            .put("ledger", JSONArray().also { a -> ledger.forEach { a.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject) =
            Day(
                date = o.optString("date"),
                phase = o.optString("phase"),
                steps = o.optJSONArray("steps").objects().map { DayStep.fromJson(it) }.filter { it.id.isNotBlank() },
                ledger = o.optJSONArray("ledger").objects().map { LedgerEntry.fromJson(it) },
            )
    }
}

/**
 * The memory of the days. It keeps today's plan with what is done, writes down everything that counted today
 * (the "ledger"), and remembers how the last days went – which steps stayed open, and on how many days in a row the
 * plan was finished. The plan itself is calculated by the learning brain; this class only keeps it.
 */
class DailyStore(root: File) {
    constructor(context: android.content.Context) : this(context.filesDir)

    private val file = File(root, "daily.json")
    private val _day = MutableStateFlow<Day?>(null)
    val day: StateFlow<Day?> = _day.asStateFlow()
    private val _history = MutableStateFlow<List<DaySummary>>(emptyList())
    val history: StateFlow<List<DaySummary>> = _history.asStateFlow()

    init {
        SafeFile.load(file) { JSONObject(it) }?.let { o ->
            _day.value = o.optJSONObject("day")?.let { runCatching { Day.fromJson(it) }.getOrNull() }
            _history.value = o.optJSONArray("history").objects().map { DaySummary.fromJson(it) }
        }
    }

    private fun save() {
        val o = JSONObject().put("history", JSONArray().also { a -> _history.value.forEach { a.put(it.toJson()) } })
        _day.value?.let { o.put("day", it.toJson()) }
        SafeFile.write(file, o.toString())
    }

    /** Today's plan, or null if the stored one is from another day. */
    fun today(date: String): Day? = _day.value?.takeIf { it.date == date }

    /** The steps of the last planned day that stayed open (they get a second chance in the next plan). */
    fun openFromBefore(date: String): List<String> = _history.value.lastOrNull { it.date < date }?.open.orEmpty()

    /** Days in a row (up to yesterday or today) on which the whole plan was done. */
    fun finishedStreak(date: String): Int {
        var n = if (today(date)?.allDone == true) 1 else 0
        for (s in _history.value.filter { it.date < date }.sortedByDescending { it.date }) {
            if (s.finished) n++ else break
        }
        return n
    }

    /** A new day begins: the old one goes into the history, [day] becomes the plan. */
    @Synchronized
    fun begin(day: Day) {
        _day.value?.takeIf { it.date != day.date }?.let { old ->
            val summary = DaySummary(old.date, old.steps.size, old.doneCount, old.steps.filter { !it.done }.map { it.kind })
            _history.value = (_history.value.filter { it.date != old.date } + summary).sortedBy { it.date }.takeLast(60)
        }
        _day.value = day
        save()
    }

    /** Replaces the open steps of today (the plan was calculated again); what is done stays. */
    @Synchronized
    fun replan(date: String, steps: List<DayStep>) {
        val d = today(date) ?: return
        val done = d.steps.filter { it.done }
        val doneKinds = done.map { it.kind }.toSet()
        _day.value = d.copy(steps = done + steps.filter { it.kind !in doneKinds })
        save()
    }

    @Synchronized
    fun add(date: String, step: DayStep) {
        val d = today(date) ?: return
        if (d.steps.any { !it.done && it.kind == step.kind }) return
        _day.value = d.copy(steps = d.steps + step)
        save()
    }

    /**
     * Something was done. The first open step it fulfils is ticked off; in any case it is written into the ledger of
     * today. Returns the step that was ticked off, or null if it was practice outside the plan.
     */
    @Synchronized
    fun complete(date: String, kind: String, title: String, effect: String, now: Long, ref: String = ""): DayStep? {
        val d = today(date) ?: Day(date, "", emptyList())
        val covered = covers(kind)
        val index = d.steps.indexOfFirst { !it.done && it.kind in covered }
        val step = d.steps.getOrNull(index)?.copy(done = true, doneAt = now, ref = ref, effect = effect)
        val steps = if (step != null) d.steps.toMutableList().also { it[index] = step } else d.steps
        val entry = LedgerEntry(now, kind, title, effect, ref, planned = step != null)
        // Several short rounds of the same thing in a row (word after word in the pronunciation lab) are one line.
        val last = d.ledger.lastOrNull()
        val merge = step == null && last != null && !last.planned && last.kind == kind && now - last.at < 30 * 60_000L
        _day.value = d.copy(steps = steps, ledger = ((if (merge) d.ledger.dropLast(1) else d.ledger) + entry).takeLast(40))
        save()
        return step
    }

    /** Writes what an activity brought next to it, once that is known (the evaluation of a session takes a while). */
    @Synchronized
    fun effect(ref: String, text: String) {
        if (ref.isBlank()) return
        val d = _day.value ?: return
        if (d.steps.none { it.ref == ref } && d.ledger.none { it.ref == ref }) return
        _day.value =
            d.copy(
                steps = d.steps.map { if (it.ref == ref) it.copy(effect = text) else it },
                ledger = d.ledger.map { if (it.ref == ref) it.copy(effect = text) else it },
            )
        save()
    }

    @Synchronized
    fun reset() {
        _day.value = null
        _history.value = emptyList()
        SafeFile.delete(file)
    }

    companion object {
        /** A whole exam is also a presentation and a conversation; a whole presentation is not a single task. */
        fun covers(kind: String): Set<String> =
            when (kind) {
                "SIMULATION" -> setOf("SIMULATION", "PRESENTATION", "CONVERSATION")
                "REAL_TASK" -> setOf("REAL_TASK", "PRESENTATION")
                "WORKSHOP" -> setOf("WORKSHOP", "DRILLS")
                else -> setOf(kind)
            }
    }
}
