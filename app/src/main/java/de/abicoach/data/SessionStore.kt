package de.abicoach.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

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

    /** Recording of the student's microphone; part 0 = training, 1 = presentation, 2 = exam conversation. */
    fun audioFile(id: String, part: Int): File = File(audioDir, "${id}_p$part.wav")

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
}
