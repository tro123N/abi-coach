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
class SessionStore(root: File) {
    constructor(context: Context) : this(context.filesDir)

    private val dir = File(root, "sessions").apply { mkdirs() }
    private val audioDir = File(root, "audio").apply { mkdirs() }

    private val _sessions = MutableStateFlow<List<SessionRecord>>(emptyList())
    val sessions: StateFlow<List<SessionRecord>> = _sessions.asStateFlow()

    init {
        reload()
    }

    /** Reads every session from the disk (once at the start and after a restore). A broken file falls back to its backup. */
    @Synchronized
    fun reload() {
        val names =
            (dir.listFiles() ?: emptyArray())
                .map { it.name.removeSuffix(".bak").removeSuffix(".tmp") }
                .filter { it.endsWith(".json") }
                .distinct()
        _sessions.value =
            names.mapNotNull { name -> SafeFile.load(File(dir, name)) { text -> SessionRecord.fromJson(JSONObject(text)) } }
                .sortedByDescending { it.startedAt }
    }

    /** Saves one session. The list in memory is updated directly – the other files are not read again. */
    @Synchronized
    fun save(record: SessionRecord) {
        if (!SafeFile.write(File(dir, record.id + ".json"), record.toJson().toString())) {
            CrashLog.nonFatal("Sitzung speichern", java.io.IOException("Sitzung ${record.id} konnte nicht gespeichert werden (Speicher voll?)"))
        }
        _sessions.value = (_sessions.value.filter { it.id != record.id } + record).sortedByDescending { it.startedAt }
    }

    fun get(id: String): SessionRecord? = _sessions.value.firstOrNull { it.id == id }

    @Synchronized
    fun delete(id: String) {
        SafeFile.delete(File(dir, "$id.json"))
        (0..2).forEach { audioFile(id, it).delete() }
        _sessions.value = _sessions.value.filter { it.id != id }
    }

    @Synchronized
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
        audioDir.listFiles()?.forEach { it.delete() }
        _sessions.value = emptyList()
    }

    /** Bytes used by all recordings. */
    fun audioBytes(): Long = audioDir.listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * Frees space: deletes the recordings of sessions that are evaluated and older than [days] days (the evaluation,
     * the transcript and everything learnt from it stay); 0 keeps them for ever. Recordings without a session
     * (nobody can reach them any more) are always removed. Returns the bytes freed.
     */
    @Synchronized
    fun tidyAudio(days: Int, now: Long = System.currentTimeMillis()): Long {
        val byId = _sessions.value.associateBy { it.id }
        var freed = 0L
        audioDir.listFiles()?.forEach { f ->
            val id = f.name.substringBeforeLast("_p")
            val record = byId[id]
            val old = days > 0 && now - f.lastModified() > days * 86_400_000L
            // A recording nobody refers to any more (older than a day, so a session that is just starting is safe).
            val orphan = record == null && now - f.lastModified() > 86_400_000L
            if (orphan || (record != null && record.debrief != null && old)) {
                val size = f.length()
                if (f.delete()) freed += size
            }
        }
        return freed
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
