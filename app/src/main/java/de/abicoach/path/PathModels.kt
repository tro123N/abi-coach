package de.abicoach.path

import android.content.Context
import de.abicoach.profile.Skill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/** What the student does in a level. */
enum class LevelType(val label: String) {
    /** Only reading: explanation, examples, phrases. */
    LEARN("Lernen"),

    /** One task of part 1 (summarise, analyse or comment) on a short new text. */
    TASK("Aufgabe"),

    /** The whole presentation with all three tasks. */
    PRESENTATION("Vortrag"),

    /** A conversation with the examiner like part 2. */
    TALK("Gespräch"),

    /** A complete exam simulation. */
    SIMULATION("Simulation"),
}

/**
 * One level of the Abi-Weg. Never rename [id]: the progress is stored under it.
 * [instruction] is passed to the AI that writes the material or plays the examiner.
 */
data class Level(
    val id: String,
    val title: String,
    val method: String,
    val type: LevelType,
    val learn: String,
    /** The competence the stars measure; null = the total points of the session. */
    val skill: Skill? = null,
    val focusTask: Int = 0,
    val minutes: Int = 8,
    val instruction: String = "",
    val goal: Int = 10,
    val topicId: Int? = null,
    val literary: Boolean = false,
    val strict: Boolean = false,
    /** A sentence or two in English that the app can read aloud as a model. */
    val example: String = "",
)

data class World(
    val id: String,
    val number: Int,
    val title: String,
    val subtitle: String,
    val icon: Int,
    val levels: List<Level>,
    val topicId: Int? = null,
    /** Topic worlds can be done in any order once the basics are there. */
    val free: Boolean = false,
)

/** Progress of one level. */
data class LevelProgress(
    val stars: Int = 0,
    val best: Int = -1,
    val attempts: Int = 0,
    val passedAt: Long = 0,
    val lastAt: Long = 0,
    /** 0 = not passed; 1–5 = review steps after 1, 3, 7, 14, 30 days. */
    val reviewStage: Int = 0,
    val nextReviewAt: Long = 0,
    /** A review went badly: refresh before going on. */
    val rusty: Boolean = false,
    /** Unlocked by the placement test even though the level before is not done. */
    val unlocked: Boolean = false,
) {
    val passed: Boolean get() = passedAt > 0

    fun toJson(): JSONObject =
        JSONObject().put("stars", stars).put("best", best).put("attempts", attempts).put("passedAt", passedAt).put("lastAt", lastAt)
            .put("reviewStage", reviewStage).put("nextReviewAt", nextReviewAt).put("rusty", rusty).put("unlocked", unlocked)

    companion object {
        fun fromJson(o: JSONObject) =
            LevelProgress(
                o.optInt("stars"), o.optInt("best", -1), o.optInt("attempts"), o.optLong("passedAt"), o.optLong("lastAt"),
                o.optInt("reviewStage"), o.optLong("nextReviewAt"), o.optBoolean("rusty"), o.optBoolean("unlocked"),
            )
    }
}

data class PathProgress(val levels: Map<String, LevelProgress> = emptyMap()) {
    fun of(id: String): LevelProgress = levels[id] ?: LevelProgress()

    fun toJson(): JSONObject {
        val o = JSONObject()
        levels.forEach { (k, v) -> o.put(k, v.toJson()) }
        return JSONObject().put("levels", o)
    }

    companion object {
        fun fromJson(o: JSONObject): PathProgress {
            val l = o.optJSONObject("levels") ?: JSONObject()
            val map = mutableMapOf<String, LevelProgress>()
            l.keys().forEach { k -> l.optJSONObject(k)?.let { map[k] = LevelProgress.fromJson(it) } }
            return PathProgress(map)
        }
    }
}

class PathStore(context: Context) {
    private val file = File(context.filesDir, "path.json")
    private val _progress = MutableStateFlow(load())
    val progress: StateFlow<PathProgress> = _progress.asStateFlow()

    private fun load(): PathProgress = runCatching { PathProgress.fromJson(JSONObject(file.readText())) }.getOrElse { PathProgress() }

    @Synchronized
    fun update(change: (PathProgress) -> PathProgress) {
        val next = change(_progress.value)
        _progress.value = next
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(next.toJson().toString())
            tmp.renameTo(file)
        }
    }

    fun reset() = update { PathProgress() }
}
