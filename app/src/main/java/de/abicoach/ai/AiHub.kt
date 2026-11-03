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

    /** Set when a better model failed and a fallback answered; shown once in the app. */
    @Volatile var lastNotice: String? = null

    val hasGemini: Boolean get() = settings.geminiKey.isNotBlank()

    private val bestGemini: String
        get() = if (settings.quality == Quality.BEST && settings.geminiBestModel !in unavailable) settings.geminiBestModel else settings.geminiTextModel

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
            if (primary == settings.geminiTextModel || !(e.isPermanent || e.code == 429)) throw e
            unavailable += primary
            lastNotice = fallbackNotice(primary, e)
            gemini.generateText(settings.geminiTextModel, system, turns) to pretty(settings.geminiTextModel)
        }
    }

    /** Quick checks during speaking: must answer within one or two seconds. */
    suspend fun fastJson(system: String, user: String, schema: Sch): JSONObject {
        val model = settings.geminiFastModel
        val parts = listOf(GeminiRest.textPart(user))
        return try {
            gemini.generateJson(model, system, parts, schema, retries = 0, thinking = "low")
        } catch (e: AiException) {
            if (model == settings.geminiTextModel || !e.isPermanent) throw e
            gemini.generateJson(settings.geminiTextModel, system, parts, schema, retries = 0, thinking = "low")
        }
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

    /** A quick listen to a short recording ("Sag es richtig", pronunciation lab): the fast model with little thinking. */
    suspend fun quickListenJson(system: String, parts: List<JSONObject>, schema: Sch): Answer {
        val model = settings.geminiFastModel
        return try {
            Answer(gemini.generateJson(model, system, parts, schema, retries = 1, thinking = "low"), pretty(model))
        } catch (e: AiException) {
            if (model == settings.geminiTextModel || !e.isPermanent) throw e
            Answer(gemini.generateJson(settings.geminiTextModel, system, parts, schema, retries = 1, thinking = "low"), pretty(settings.geminiTextModel))
        }
    }

    suspend fun embed(texts: List<String>, query: Boolean, titles: List<String>? = null): List<FloatArray> =
        gemini.embed(settings.embeddingModel, texts, query, titles = titles)

    /** Speech for "read aloud" buttons; null if it failed (the app then uses Android's voice). */
    suspend fun speak(text: String): ByteArray? =
        runCatching { gemini.tts(settings.ttsModel, text, settings.ttsVoice) }.getOrNull()

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
        "${pretty(model)} ist für deinen Schlüssel gerade nicht nutzbar (${e.message}). ${pretty(settings.geminiTextModel)} hat übernommen – etwas weniger genau."

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
            if (primary == fallback || !(e.isPermanent || e.code == 429)) throw e
            // e.g. Gemini 4 Argon is not part of the free tier: remember it and use Flash from now on.
            unavailable += primary
            lastNotice = fallbackNotice(primary, e)
            Answer(gemini.generateJson(fallback, system, parts, schema, retries, thinkingLevel), pretty(fallback))
        }

    companion object {
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
