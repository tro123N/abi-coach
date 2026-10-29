package de.abicoach.ai

import de.abicoach.data.Quality
import de.abicoach.data.Settings
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Decides which AI does which job, with automatic fallbacks:
 * - examiner voice: OpenAI GPT-Live-1 (full-duplex) → Gemini Live
 * - "brain" (grading, exam material, coach, plans): Claude Opus → Gemini 4 Argon → Gemini Flash
 * - quick live checks: Claude Haiku → Gemini Flash
 * - reading the course material, listening to recordings, embeddings, speech: Gemini
 * Only the Gemini key is required; every other key upgrades one function.
 */
class AiHub(private val http: OkHttpClient, private val settings: Settings) {
    val gemini = GeminiRest(http) { settings.geminiKey }
    val claude = ClaudeRest(http) { settings.anthropicKey }

    /** Models that failed permanently in this run of the app (no access, no credit, unknown): skipped from now on. */
    private val unavailable = ConcurrentHashMap.newKeySet<String>()

    /** Set when a better model failed and a fallback answered; shown once in the app. */
    @Volatile var lastNotice: String? = null

    val hasGemini: Boolean get() = settings.geminiKey.isNotBlank()
    val useClaude: Boolean get() = settings.quality == Quality.BEST && settings.anthropicKey.isNotBlank() && settings.claudeBrainModel !in unavailable
    val useOpenAi: Boolean get() = settings.quality == Quality.BEST && settings.openAiKey.isNotBlank()

    private val bestGemini: String
        get() = if (settings.quality == Quality.BEST && settings.geminiBestModel !in unavailable) settings.geminiBestModel else settings.geminiTextModel

    fun brainLabel(): String = if (useClaude) pretty(settings.claudeBrainModel) else pretty(bestGemini)

    fun fastLabel(): String =
        if (settings.quality == Quality.BEST && settings.anthropicKey.isNotBlank() && settings.claudeFastModel !in unavailable) {
            pretty(settings.claudeFastModel)
        } else {
            pretty(settings.geminiTextModel)
        }

    fun examinerLabel(): String = if (useOpenAi) pretty(settings.openAiLiveModel) else pretty(settings.geminiLiveModel)

    fun listenerLabel(): String = pretty(bestGemini)

    data class Answer(val json: JSONObject, val by: String)

    /** The hard jobs: grading a whole session, writing exam material, plans. */
    suspend fun brainJson(system: String, user: String, schema: Sch, maxTokens: Int = 16_000, thinking: Boolean = true): Answer {
        if (useClaude) {
            val model = settings.claudeBrainModel
            try {
                return Answer(claude.json(model, system, user, schema, maxTokens, thinking), pretty(model))
            } catch (e: AiException) {
                if (!hasGemini) throw e
                if (e.isPermanent) unavailable += model
                lastNotice = "${pretty(model)} war nicht erreichbar (${e.message}). Gemini hat übernommen."
            }
        }
        return geminiJson(bestGemini, settings.geminiTextModel, system, listOf(GeminiRest.textPart(user)), schema)
    }

    /** Chat answers of the coach. Returns the text and the model that wrote it. */
    suspend fun brainText(system: String, turns: List<Pair<String, String>>, maxTokens: Int = 6_000): Pair<String, String> {
        if (useClaude) {
            val model = settings.claudeBrainModel
            try {
                return claude.text(model, system, turns, maxTokens, thinking = true) to pretty(model)
            } catch (e: AiException) {
                if (!hasGemini) throw e
                if (e.isPermanent) unavailable += model
                lastNotice = "${pretty(model)} war nicht erreichbar (${e.message}). Gemini hat übernommen."
            }
        }
        val primary = bestGemini
        return try {
            gemini.generateText(primary, system, turns) to pretty(primary)
        } catch (e: AiException) {
            if (primary == settings.geminiTextModel || !(e.isPermanent || e.code == 429)) throw e
            unavailable += primary
            gemini.generateText(settings.geminiTextModel, system, turns) to pretty(settings.geminiTextModel)
        }
    }

    /** Quick checks during speaking: must answer within one or two seconds. */
    suspend fun fastJson(system: String, user: String, schema: Sch, maxTokens: Int = 1_500): JSONObject {
        val model = settings.claudeFastModel
        if (settings.quality == Quality.BEST && settings.anthropicKey.isNotBlank() && model !in unavailable) {
            try {
                return claude.json(model, system, user, schema, maxTokens, thinking = false, retries = 0)
            } catch (e: AiException) {
                if (!hasGemini) throw e
                if (e.isPermanent) unavailable += model
            }
        }
        return gemini.generateJson(settings.geminiTextModel, system, listOf(GeminiRest.textPart(user)), schema, retries = 0)
    }

    /** Reading pages, photos and documents (always Gemini). */
    suspend fun readJson(system: String, parts: List<JSONObject>, schema: Sch): JSONObject =
        geminiJson(settings.geminiReaderModel, settings.geminiTextModel, system, parts, schema, retries = 2).json

    /** Cheap text jobs that need no top model (summaries of the course material). */
    suspend fun cheapJson(system: String, user: String, schema: Sch): JSONObject =
        gemini.generateJson(settings.geminiTextModel, system, listOf(GeminiRest.textPart(user)), schema, retries = 2)

    /** Listening to the student's recording (Gemini: Claude cannot hear audio). */
    suspend fun listenJson(system: String, parts: List<JSONObject>, schema: Sch): Answer =
        geminiJson(bestGemini, settings.geminiTextModel, system, parts, schema)

    suspend fun embed(texts: List<String>, query: Boolean, titles: List<String>? = null): List<FloatArray> =
        gemini.embed(settings.embeddingModel, texts, query, titles = titles)

    /** Speech for "read aloud" buttons; null if it failed (the app then uses Android's voice). */
    suspend fun speak(text: String): ByteArray? =
        runCatching { gemini.tts(settings.ttsModel, text, settings.ttsVoice) }.getOrNull()

    /** The examiner for a live conversation. */
    fun examiner(instructions: String, onEvent: (LiveEvent) -> Unit): VoiceAgent =
        if (useOpenAi) {
            OpenAiLiveAgent(http, settings.openAiKey, settings.openAiLiveModel, settings.openAiVoice, instructions, onEvent)
        } else {
            val socket =
                LiveSocket(
                    http = http,
                    apiKey = settings.geminiKey,
                    name = "Prüfer",
                    supportsResumption = true,
                    setupFactory = { handle, minimal -> GeminiSetup.examiner(settings.geminiLiveModel, settings.geminiVoice, instructions, handle, minimal) },
                    onEvent = onEvent,
                )
            GeminiVoiceAgent(socket, pretty(settings.geminiLiveModel))
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

    private suspend fun geminiJson(primary: String, fallback: String, system: String, parts: List<JSONObject>, schema: Sch, retries: Int = 1): Answer =
        try {
            Answer(gemini.generateJson(primary, system, parts, schema, retries), pretty(primary))
        } catch (e: AiException) {
            if (primary == fallback || !(e.isPermanent || e.code == 429)) throw e
            // e.g. Gemini 4 Argon is not part of the free tier: remember and use Flash from now on.
            unavailable += primary
            Answer(gemini.generateJson(fallback, system, parts, schema, retries), pretty(fallback))
        }

    companion object {
        /** "claude-opus-5-5" → "Claude Opus 5.5", "gemini-3.8-flash" → "Gemini 3.8 Flash", "gpt-live-1" → "GPT-Live-1". */
        fun pretty(model: String): String {
            val id = model.removePrefix("models/")
            if (id.startsWith("gpt-")) return "GPT-" + id.removePrefix("gpt-").split("-").joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }
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
