package de.abicoach.ai

import de.abicoach.data.Quality
import de.abicoach.data.Settings
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * All AI comes from one provider, Google (one key, one bill). Which Gemini model does which job:
 * - examiner voice: Gemini 3.8 Live (listens and speaks at the same time)
 * - verbatim transcript ("second ear"): Gemini 3.5 Transcribe Live
 * - "brain" (grading, exam material, coach, plans, listening to recordings): Gemini 4 Argon → Gemini 3.8 Flash
 * - quick live checks while the student speaks: Gemini 3.8 Flash with little thinking
 * - reading the course material, search vectors, read-aloud voice: Gemini Flash, Embedding 2, Flash TTS
 */
class AiHub(private val http: OkHttpClient, private val settings: Settings) {
    val gemini = GeminiRest(http) { settings.geminiKey }

    /** Models that failed permanently in this run of the app (no access, no credit, unknown): skipped from now on. */
    private val unavailable = ConcurrentHashMap.newKeySet<String>()

    /** Models that were overloaded ("high demand", 503): skipped for a few minutes, then tried again. */
    private val busyUntil = ConcurrentHashMap<String, Long>()

    private fun usable(model: String): Boolean = model !in unavailable && (busyUntil[model] ?: 0L) <= System.currentTimeMillis()

    /** Remembers why a model did not answer; true if another model should be tried. */
    private fun shouldFallBack(model: String, e: AiException): Boolean {
        when {
            e.isPermanent -> unavailable += model
            e.code == 429 || e.isOverloaded -> busyUntil[model] = System.currentTimeMillis() + BUSY_MS
            else -> return false
        }
        return true
    }

    /** Set when a better model failed and a fallback answered; shown once in the app. */
    @Volatile var lastNotice: String? = null

    val hasGemini: Boolean get() = settings.geminiKey.isNotBlank()

    private val bestGemini: String
        get() = if (settings.quality == Quality.BEST && usable(settings.geminiBestModel)) settings.geminiBestModel else settings.geminiTextModel

    fun brainLabel(): String = pretty(bestGemini)

    fun fastLabel(): String = pretty(settings.geminiFastModel)

    fun examinerLabel(): String = pretty(settings.geminiLiveModel)

    fun listenerLabel(): String = pretty(bestGemini)

    data class Answer(val json: JSONObject, val by: String)

    /** The hard jobs: grading a whole session, writing exam material, plans. [thinking] false = little thinking, faster. */
    suspend fun brainJson(system: String, user: String, schema: Sch, thinking: Boolean = true): Answer =
        geminiJson(bestGemini, settings.geminiTextModel, system, listOf(GeminiRest.textPart(user)), schema, thinkingLevel = if (thinking) null else "low")

    /** Chat answers of the coach. Returns the text and the model that wrote it. */
    suspend fun brainText(system: String, turns: List<Pair<String, String>>): Pair<String, String> {
        val primary = bestGemini
        return try {
            gemini.generateText(primary, system, turns) to pretty(primary)
        } catch (e: AiException) {
            if (primary == settings.geminiTextModel || !shouldFallBack(primary, e)) throw e
            lastNotice = fallbackNotice(primary, e)
            gemini.generateText(settings.geminiTextModel, system, turns) to pretty(settings.geminiTextModel)
        }
    }

    /** The quick models in order: the chosen fast model, the normal one, then Flash-Lite as the last resort. */
    private fun quickChain(): List<String> = listOf(settings.geminiFastModel, settings.geminiTextModel, LITE_MODEL).distinct()

    /** Tries the quick models one after the other when one is overloaded, has no quota or is not available. */
    private suspend fun <T> quick(block: suspend (String) -> T): Pair<T, String> {
        val chain = quickChain()
        val first = chain.firstOrNull { usable(it) } ?: chain.first()
        var last: AiException? = null
        for (model in listOf(first) + chain.filter { it != first && usable(it) }) {
            try {
                return block(model) to model
            } catch (e: AiException) {
                last = e
                if (!shouldFallBack(model, e)) throw e
            }
        }
        throw last ?: AiException("Kein schnelles Modell erreichbar", 503)
    }

    /** Quick checks during speaking: must answer within one or two seconds. */
    suspend fun fastJson(system: String, user: String, schema: Sch): JSONObject {
        val parts = listOf(GeminiRest.textPart(user))
        return quick { model -> gemini.generateJson(model, system, parts, schema, retries = 0, thinking = "low") }.first
    }

    /** Reading pages, photos and documents. */
    suspend fun readJson(system: String, parts: List<JSONObject>, schema: Sch): JSONObject =
        geminiJson(settings.geminiReaderModel, settings.geminiTextModel, system, parts, schema, retries = 2).json

    /** Cheap text jobs that need no top model (summaries of the course material). */
    suspend fun cheapJson(system: String, user: String, schema: Sch): JSONObject =
        gemini.generateJson(settings.geminiTextModel, system, listOf(GeminiRest.textPart(user)), schema, retries = 2)

    /** Listening to the student's recording. */
    suspend fun listenJson(system: String, parts: List<JSONObject>, schema: Sch): Answer =
        geminiJson(bestGemini, settings.geminiTextModel, system, parts, schema)

    /** A quick listen to a short recording (the ear, "Sag es richtig", speaking training): the fast model with little thinking. */
    suspend fun quickListenJson(system: String, parts: List<JSONObject>, schema: Sch): Answer {
        val (json, model) = quick { model -> gemini.generateJson(model, system, parts, schema, retries = 1, thinking = "low") }
        return Answer(json, pretty(model))
    }

    suspend fun embed(texts: List<String>, query: Boolean, titles: List<String>? = null): List<FloatArray> =
        gemini.embed(settings.embeddingModel, texts, query, titles = titles)

    /** Speech for "read aloud" buttons and the coach; null if it failed (the app then uses Android's voice). */
    suspend fun speak(text: String): ByteArray? {
        for (model in listOf(settings.ttsModel, LITE_TTS_MODEL).distinct().filter { usable(it) }) {
            try {
                return gemini.tts(model, text, settings.ttsVoice)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AiException) {
                if (!shouldFallBack(model, e)) return null
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    /** The examiner for a live conversation (Gemini Live). */
    fun examiner(instructions: String, onEvent: (LiveEvent) -> Unit): VoiceAgent {
        val socket =
            LiveSocket(
                http = http,
                apiKey = settings.geminiKey,
                name = "Prüfer",
                supportsResumption = true,
                setupFactory = { handle, minimal -> GeminiSetup.examiner(settings.geminiLiveModel, settings.geminiVoice, instructions, handle, minimal) },
                onEvent = onEvent,
            )
        return GeminiVoiceAgent(socket, pretty(settings.geminiLiveModel))
    }

    /** The verbatim transcriber ("second ear"). */
    fun transcriber(vocabulary: List<String>, onEvent: (LiveEvent) -> Unit): LiveSocket =
        LiveSocket(
            http = http,
            apiKey = settings.geminiKey,
            name = "Mitschrift",
            supportsResumption = false,
            setupFactory = { _, minimal -> GeminiSetup.transcriber(settings.transcribeModel, vocabulary, minimal) },
            onEvent = onEvent,
        )

    private fun fallbackNotice(model: String, e: AiException): String =
        if (e.isOverloaded || e.code == 429) {
            "${pretty(model)} ist gerade überlastet. ${pretty(settings.geminiTextModel)} hat übernommen – in ein paar Minuten ist wieder ${pretty(model)} dran."
        } else {
            "${pretty(model)} ist für deinen Schlüssel gerade nicht nutzbar (${e.message}). ${pretty(settings.geminiTextModel)} hat übernommen – etwas weniger genau."
        }

    /** A cloud job of the brain (grading): the job id and the model that took it. */
    data class CloudJobStart(val id: String, val model: String)

    /**
     * Starts a grading job in Google's cloud (background interaction) with the brain; if it is overloaded or not
     * usable, with the normal model. The phone can be switched off while it runs.
     */
    suspend fun startCloudBrain(system: String, parts: List<JSONObject>, schema: Sch): CloudJobStart {
        val primary = bestGemini
        val fallback = settings.geminiTextModel
        return try {
            CloudJobStart(gemini.startCloud(primary, system, parts, schema), primary)
        } catch (e: AiException) {
            if (primary == fallback || !shouldFallBack(primary, e)) throw e
            lastNotice = fallbackNotice(primary, e)
            CloudJobStart(gemini.startCloud(fallback, system, parts, schema), fallback)
        }
    }

    /** A tiny cloud job with the quick model (system test). */
    suspend fun startCloudQuick(prompt: String): CloudJobStart =
        CloudJobStart(gemini.startCloud(settings.geminiFastModel, "", listOf(GeminiRest.textPart(prompt)), null), settings.geminiFastModel)

    private suspend fun geminiJson(
        primary: String,
        fallback: String,
        system: String,
        parts: List<JSONObject>,
        schema: Sch,
        retries: Int = 1,
        thinkingLevel: String? = null,
    ): Answer =
        try {
            Answer(gemini.generateJson(primary, system, parts, schema, retries, thinkingLevel), pretty(primary))
        } catch (e: AiException) {
            // e.g. Gemini 4 Argon is overloaded or not part of the free tier: Flash answers instead.
            if (primary == fallback || !shouldFallBack(primary, e)) throw e
            lastNotice = fallbackNotice(primary, e)
            Answer(gemini.generateJson(fallback, system, parts, schema, retries, thinkingLevel), pretty(fallback))
        }

    companion object {
        /** Last resort for the quick jobs when Flash is overloaded. */
        const val LITE_MODEL = "gemini-3.5-flash-lite"
        const val LITE_TTS_MODEL = "gemini-3.8-flash-lite-tts"
        private const val BUSY_MS = 3 * 60_000L

        /** "gemini-3.8-flash" → "Gemini 3.8 Flash", "gemini-3.5-transcribe-live" → "Gemini 3.5 Transcribe Live". */
        fun pretty(model: String): String {
            val id = model.removePrefix("models/")
            val parts = id.split("-")
            val out = ArrayList<String>()
            var i = 0
            while (i < parts.size) {
                val p = parts[i]
                if (p.all { it.isDigit() } && i + 1 < parts.size && parts[i + 1].all { it.isDigit() } && parts[i + 1].length <= 2) {
                    out += "$p.${parts[i + 1]}"
                    i += 2
                    continue
                }
                out += p.replaceFirstChar { it.uppercase() }
                i++
            }
            return out.joinToString(" ")
        }
    }
}
