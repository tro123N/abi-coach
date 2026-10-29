package de.abicoach.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Stores every session as one JSON file on the phone. Nothing leaves the device except the API calls. */
class SessionStore(context: Context) {
    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }
    private val audioDir = File(context.filesDir, "audio").apply { mkdirs() }

    private val _sessions = MutableStateFlow<List<SessionRecord>>(emptyList())
    val sessions: StateFlow<List<SessionRecord>> = _sessions.asStateFlow()

    init {
        reload()
    }

    @Synchronized
    fun reload() {
        val files = dir.listFiles { f -> f.extension == "json" } ?: emptyArray()
        _sessions.value =
            files.mapNotNull { f -> runCatching { SessionRecord.fromJson(JSONObject(f.readText())) }.getOrNull() }
                .sortedByDescending { it.startedAt }
    }

    @Synchronized
    fun save(record: SessionRecord) {
        File(dir, record.id + ".json").writeText(record.toJson().toString())
        reload()
    }

    fun get(id: String): SessionRecord? = _sessions.value.firstOrNull { it.id == id }

    @Synchronized
    fun delete(id: String) {
        File(dir, "$id.json").delete()
        (0..2).forEach { audioFile(id, it).delete() }
        reload()
    }

    @Synchronized
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
        audioDir.listFiles()?.forEach { it.delete() }
        reload()
    }

    /** Recording of the student's microphone; part 0 = training, 1 = presentation, 2 = exam conversation. */
    fun audioFile(id: String, part: Int): File = File(audioDir, "${id}_p$part.wav")

    /** Only sessions that were evaluated. */
    fun graded(): List<SessionRecord> = _sessions.value.filter { it.debrief != null }

    /** How often each error category occurred across all sessions (the "Fehlerprofil"). */
    fun categoryCounts(): Map<String, Int> =
        _sessions.value
            .flatMap { s -> s.debrief?.errors ?: s.liveErrors }
            .filter { it.category != "CONTENT" }
            .groupingBy { it.category }
            .eachCount()

    /** Latest examples per category, newest first. */
    fun examples(category: String, limit: Int = 4): List<LiveError> =
        _sessions.value
            .flatMap { s -> s.debrief?.errors ?: s.liveErrors }
            .filter { it.category == category }
            .take(limit)

    /** Practice minutes per day for the last [days] days, oldest first. */
    fun minutesPerDay(days: Int = 14): List<Pair<LocalDate, Int>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val byDay =
            _sessions.value.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
                .mapValues { (_, list) -> list.sumOf { it.durationSec } / 60 }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to (byDay[d] ?: 0)
        }
    }

    /** Days in a row (ending today or yesterday) with at least one session. */
    fun streak(): Int {
        val zone = ZoneId.systemDefault()
        val days = _sessions.value.filter { it.durationSec >= 60 }.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()
        var day = LocalDate.now(zone)
        if (day !in days) day = day.minusDays(1)
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun minutesToday(): Int {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return _sessions.value.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today }.sumOf { it.durationSec } / 60
    }

    /** How often each topic field was practised. */
    fun topicCounts(): Map<Int, Int> = _sessions.value.filter { it.topicId >= 0 }.groupingBy { it.topicId }.eachCount()
}
