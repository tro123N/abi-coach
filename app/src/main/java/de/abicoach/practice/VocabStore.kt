package de.abicoach.practice

import android.content.Context
import de.abicoach.data.objects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.roundToLong

/** Where a card came from. */
enum class CardSource(val label: String) {
    PHRASE("Redemittel"),
    UPGRADE("15-Punkte-Satz"),
    COURSE("Aus deinem Kurs"),
    ERROR("Dein Fehler"),
}

/**
 * One vocabulary card. Scheduling follows a small SM-2 variant: the interval grows with the ease factor,
 * "Nochmal" sends the card back to the start.
 */
data class VocabCard(
    val id: String,
    val front: String,
    val back: String,
    val example: String,
    val source: CardSource,
    val topic: Int = -1,
    val addedAt: Long = System.currentTimeMillis(),
    val dueAt: Long = 0,
    val intervalDays: Double = 0.0,
    val ease: Double = 2.5,
    val reps: Int = 0,
    val lapses: Int = 0,
) {
    val isNew: Boolean get() = reps == 0

    fun toJson(): JSONObject =
        JSONObject().put("id", id).put("front", front).put("back", back).put("example", example).put("source", source.name)
            .put("topic", topic).put("addedAt", addedAt).put("dueAt", dueAt).put("intervalDays", intervalDays)
            .put("ease", ease).put("reps", reps).put("lapses", lapses)

    companion object {
        fun fromJson(o: JSONObject) =
            VocabCard(
                id = o.optString("id"),
                front = o.optString("front"),
                back = o.optString("back"),
                example = o.optString("example"),
                source = runCatching { CardSource.valueOf(o.optString("source")) }.getOrDefault(CardSource.PHRASE),
                topic = o.optInt("topic", -1),
                addedAt = o.optLong("addedAt"),
                dueAt = o.optLong("dueAt"),
                intervalDays = o.optDouble("intervalDays", 0.0),
                ease = o.optDouble("ease", 2.5),
                reps = o.optInt("reps"),
                lapses = o.optInt("lapses"),
            )

        fun key(front: String): String = front.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
    }
}

enum class Grade(val label: String) { AGAIN("Nochmal"), HARD("Schwer"), GOOD("Gut"), EASY("Leicht") }

object Scheduler {
    private const val DAY = 86_400_000L

    fun review(card: VocabCard, grade: Grade, now: Long = System.currentTimeMillis()): VocabCard {
        if (grade == Grade.AGAIN) {
            return card.copy(dueAt = now + 10 * 60_000L, intervalDays = 0.0, ease = max(1.3, card.ease - 0.2), reps = card.reps + 1, lapses = card.lapses + 1)
        }
        val ease =
            when (grade) {
                Grade.HARD -> max(1.3, card.ease - 0.15)
                Grade.EASY -> card.ease + 0.15
                else -> card.ease
            }
        val interval =
            when {
                card.intervalDays < 1.0 -> if (grade == Grade.EASY) 4.0 else if (grade == Grade.HARD) 1.0 else 2.0
                grade == Grade.HARD -> card.intervalDays * 1.2
                grade == Grade.EASY -> card.intervalDays * ease * 1.3
                else -> card.intervalDays * ease
            }.coerceAtMost(180.0)
        return card.copy(dueAt = now + (interval * DAY).roundToLong(), intervalDays = interval, ease = ease, reps = card.reps + 1)
    }

    /** What the next interval would be, for the button labels ("2 T."). */
    fun preview(card: VocabCard, grade: Grade): String {
        val next = review(card, grade, 0)
        val minutes = next.dueAt / 60_000L
        return when {
            minutes < 60 -> "$minutes Min."
            next.intervalDays < 1.5 -> "1 T."
            next.intervalDays < 30 -> "${next.intervalDays.roundToLong()} T."
            else -> "${(next.intervalDays / 30).roundToLong()} Mon."
        }
    }
}

/** The vocabulary deck: phrases and sentences from the debriefs and words from the course material. */
class VocabStore(root: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(root, "vocab.json")
    private val _cards = MutableStateFlow(load())
    val cards: StateFlow<List<VocabCard>> = _cards.asStateFlow()

    private fun load(): List<VocabCard> = de.abicoach.data.SafeFile.load(file) { text -> JSONArray(text).objects().map { VocabCard.fromJson(it) } } ?: emptyList()

    @Synchronized
    private fun save(list: List<VocabCard>) {
        _cards.value = list
        de.abicoach.data.SafeFile.write(file, JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString())
    }

    /** Adds cards whose front is not in the deck yet; returns how many were new. */
    @Synchronized
    fun addAll(newCards: List<VocabCard>): Int {
        val known = _cards.value.map { VocabCard.key(it.front) }.toMutableSet()
        val fresh = newCards.filter { it.front.isNotBlank() && it.back.isNotBlank() && known.add(VocabCard.key(it.front)) }
        if (fresh.isNotEmpty()) save(_cards.value + fresh)
        return fresh.size
    }

    @Synchronized
    fun update(card: VocabCard) = save(_cards.value.map { if (it.id == card.id) card else it })

    @Synchronized
    fun delete(id: String) = save(_cards.value.filter { it.id != id })

    fun due(now: Long = System.currentTimeMillis(), newPerDay: Int = 10): List<VocabCard> {
        val all = _cards.value
        val reviews = all.filter { !it.isNew && it.dueAt <= now }.sortedBy { it.dueAt }
        val news = all.filter { it.isNew }.sortedBy { it.addedAt }.take(newPerDay)
        return reviews + news
    }

    fun reset() {
        save(emptyList())
        de.abicoach.data.SafeFile.dropBackup(file)
    }
}
