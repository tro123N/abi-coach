package de.abicoach.session

import android.content.Context
import de.abicoach.data.ExamTask
import de.abicoach.data.FeedbackMode
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.data.strings
import de.abicoach.data.toJsonArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/** Writes a small JSON file safely: first into a temporary file, then in place. */
private fun writeJson(file: File, json: JSONObject) {
    runCatching {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(json.toString())
        if (!temporary.renameTo(file)) {
            file.writeText(json.toString())
            temporary.delete()
        }
    }
}

private fun readJson(file: File): JSONObject? = runCatching { if (file.exists()) JSONObject(file.readText()) else null }.getOrNull()

/**
 * The preparation of a session on disk: text, tasks, notes and the end of the preparation time.
 * If Android closes the app while the student reads, everything is still there when they come back.
 */
class DraftStore(context: Context) {
    private val file = File(context.filesDir, "prep_draft.json")

    /** [prepEndsAt] 0 means: the material is ready, but the preparation time has not been started yet. */
    data class Saved(val record: SessionRecord, val prepEndsAt: Long, val prepMinutes: Int, val savedAt: Long)

    @Synchronized
    fun save(record: SessionRecord, prepEndsAt: Long, prepMinutes: Int) {
        writeJson(
            file,
            JSONObject().put("record", record.toJson()).put("prepEndsAt", prepEndsAt).put("prepMinutes", prepMinutes).put("savedAt", System.currentTimeMillis()),
        )
    }

    @Synchronized
    fun load(): Saved? {
        val o = readJson(file) ?: return null
        val record = o.optJSONObject("record")?.let { runCatching { SessionRecord.fromJson(it) }.getOrNull() } ?: return null
        if (record.task == null) return null
        return Saved(record, o.optLong("prepEndsAt"), o.optInt("prepMinutes", 10), o.optLong("savedAt"))
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
    }
}

/** Finished exam material that waits until the student opens it; it also holds one text prepared in advance. */
class MaterialShelf(context: Context) {
    private val dir = File(context.filesDir, "material").apply { mkdirs() }

    data class Entry(val task: ExamTask, val note: String?, val madeAt: Long)

    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    @Synchronized
    fun put(key: String, task: ExamTask, note: String?) {
        val json = JSONObject().put("task", task.toJson()).put("madeAt", System.currentTimeMillis())
        if (note != null) json.put("note", note)
        writeJson(file(key), json)
    }

    @Synchronized
    fun has(key: String): Boolean = file(key).exists()

    @Synchronized
    fun peek(key: String): Entry? = read(key)

    /** Reads the entry and removes it from the shelf. */
    @Synchronized
    fun take(key: String): Entry? {
        val entry = read(key)
        if (entry != null) file(key).delete()
        return entry
    }

    private fun read(key: String): Entry? {
        val o = readJson(file(key)) ?: return null
        val task = o.optJSONObject("task")?.let { runCatching { ExamTask.fromJson(it) }.getOrNull() } ?: return null
        return Entry(task, o.optString("note").takeIf { it.isNotBlank() }, o.optLong("madeAt"))
    }

    /** Throws away material that nobody opened for a day. */
    @Synchronized
    fun tidy() {
        val limit = System.currentTimeMillis() - 24 * 3_600_000L
        dir.listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }

    companion object {
        const val PREFETCH = "prefetch"
    }
}

/**
 * The session that waits for its material. It is kept on disk, so that the app can pick it up again when the
 * material was finished while the app was closed.
 */
class PendingMaterialStore(context: Context) {
    private val file = File(context.filesDir, "pending_material.json")

    data class Pending(
        val key: String,
        val kind: SessionKind,
        val feedback: FeedbackMode,
        val topicId: Int,
        val focusTask: Int,
        val focusCategories: List<String>,
        val levelId: String,
        val prepMinutes: Int,
        val startedAt: Long,
    )

    @Synchronized
    fun save(p: Pending) {
        writeJson(
            file,
            JSONObject().put("key", p.key).put("kind", p.kind.name).put("feedback", p.feedback.name).put("topicId", p.topicId)
                .put("focusTask", p.focusTask).put("focusCategories", p.focusCategories.toJsonArray()).put("levelId", p.levelId)
                .put("prepMinutes", p.prepMinutes).put("startedAt", p.startedAt),
        )
    }

    @Synchronized
    fun load(): Pending? {
        val o = readJson(file) ?: return null
        val key = o.optString("key")
        if (key.isBlank()) return null
        return Pending(
            key = key,
            kind = runCatching { SessionKind.valueOf(o.optString("kind")) }.getOrDefault(SessionKind.PART1),
            feedback = runCatching { FeedbackMode.valueOf(o.optString("feedback")) }.getOrDefault(FeedbackMode.PRUEFUNG),
            topicId = o.optInt("topicId", -1),
            focusTask = o.optInt("focusTask"),
            focusCategories = o.optJSONArray("focusCategories").strings(),
            levelId = o.optString("levelId"),
            prepMinutes = o.optInt("prepMinutes", 10),
            startedAt = o.optLong("startedAt"),
        )
    }

    @Synchronized
    fun clear() {
        runCatching { file.delete() }
    }
}

/** What the material job is doing right now; the app reads this to show progress and to jump to the result. */
data class MaterialStatus(
    val key: String? = null,
    val text: String = "",
    val error: String? = null,
    /** Set when material has just been finished. */
    val readyKey: String? = null,
)

class MaterialCenter {
    private val _status = MutableStateFlow(MaterialStatus())
    val status: StateFlow<MaterialStatus> = _status.asStateFlow()

    fun begin(key: String) {
        _status.value = MaterialStatus(key = key, text = "Bereite das Material vor …")
    }

    fun progress(key: String, text: String) {
        if (_status.value.key == key) _status.value = _status.value.copy(text = text, error = null)
    }

    fun done(key: String) {
        _status.value = MaterialStatus(readyKey = key)
    }

    fun fail(key: String, message: String) {
        _status.value = MaterialStatus(key = key, error = message)
    }

    /** The app has taken note of a result or an error. */
    fun consume() {
        if (_status.value.readyKey != null || _status.value.error != null) _status.value = MaterialStatus()
    }

    fun reset() {
        _status.value = MaterialStatus()
    }
}
