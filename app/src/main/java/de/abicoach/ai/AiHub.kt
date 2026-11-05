package de.abicoach.ai

import android.content.Context
import android.util.LruCache
import de.abicoach.audio.SpeechClean
import de.abicoach.audio.Wav
import de.abicoach.data.Quality
import de.abicoach.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * All AI comes from the one company the student chose (one key, one bill), so the companies can be compared:
 *
 * - **Google:** Gemini Live examines (hears and speaks at once), Gemini Transcribe Live writes along, Gemini 4 Argon /
 *   Flash grade, write and coach, Flash TTS reads aloud. Gradings can run in Google's cloud.
 * - **OpenAI:** GPT-Live examines, GPT-Live-Transcribe writes along, GPT (Sol / Terra / Luna) grades, writes and
 *   coaches, the GPT audio model listens to recordings, GPT TTS reads aloud.
 * - **Anthropic:** Claude grades, writes, coaches and examines (Opus / Sonnet / Haiku). Claude can neither hear nor
 *   speak, so the phone's own speech recognition and voice do that; the course search then uses keywords only.
 */
class AiHub(context: Context, private val http: OkHttpClient, private val settings: Settings) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val gemini = GeminiRest(http) { settings.geminiKey }
    val openai = OpenAiRest(http) { settings.openAiKey }
    val claude = ClaudeRest(http) { settings.anthropicKey }

    /** The phone's own ears and voice (used with Claude). */
    val phone: PhoneSpeech by lazy { PhoneSpeech(appContext) { settings.accent.locale } }

    val provider: Provider get() = settings.provider

    /** The chosen company has a key: the AI can work. */
    val ready: Boolean get() = settings.keyOf(provider).isNotBlank()

    /** Short recordings can be judged: by the model itself or, with Claude, through the phone's speech recognition. */
    val canHear: Boolean get() = provider != Provider.ANTHROPIC || phone.canRecognize

    /** Whole recordings of a session can be listened to (pronunciation, fluency). Not with Claude. */
    val hearsRecordings: Boolean get() = provider != Provider.ANTHROPIC

    /** Search vectors exist (Claude has none: the course search then works with keywords). */
    val canEmbed: Boolean get() = provider != Provider.ANTHROPIC

    /** The vector space of the current provider; vectors of different spaces must not be compared. */
    val embedSpace: String?
        get() =
            when (provider) {
                Provider.GOOGLE -> "gemini:${settings.embeddingModel}"
                Provider.OPENAI -> "openai:${settings.oaEmbeddingModel}"
                Provider.ANTHROPIC -> null
            }

    /** Gradings that go on in the cloud while the phone is off exist only at Google. */
    val canRunInCloud: Boolean get() = provider == Provider.GOOGLE

    /** Models that failed permanently in this run of the app (no access, no credit, unknown): skipped from now on. */
    private val unavailable = ConcurrentHashMap.newKeySet<String>()

    /** Models that were overloaded ("high demand", 503, 529): skipped for a few minutes, then tried again. */
    private val busyUntil = ConcurrentHashMap<String, Long>()

    private fun usable(model: String): Boolean = model !in unavailable && (busyUntil[model] ?: 0L) <= System.currentTimeMillis()

    /** Remembers why a model did not answer; true if another model should be tried. */
    private fun shouldFallBack(model: String, e: AiException): Boolean {
        when {
            e.isPermanent -> unavailable += model
            e.code == 429 || e.code == 529 || e.isOverloaded -> busyUntil[model] = System.currentTimeMillis() + BUSY_MS
            else -> return false
        }
        return true
    }

    /** Set when a better model failed and a fallback answered; shown once in the app. */
    @Volatile var lastNotice: String? = null

    // ---------- which model does what ----------

    private val best: Boolean get() = settings.quality == Quality.BEST

    /** The strongest model (grading, exam material, coach) – or the normal one with "Sparsam" or when it is out. */
    private fun brainModel(): String =
        when (provider) {
            Provider.GOOGLE -> settings.geminiBestModel.takeIf { best && usable(it) } ?: settings.geminiTextModel
            Provider.OPENAI -> settings.oaBrainModel.takeIf { best && usable(it) } ?: settings.oaTextModel
            Provider.ANTHROPIC -> settings.clBrainModel.takeIf { best && usable(it) } ?: settings.clTextModel
        }

    /** The normal model: fallback of the brain and for cheap work. */
    private fun normalModel(): String =
        when (provider) {
            Provider.GOOGLE -> settings.geminiTextModel
            Provider.OPENAI -> settings.oaTextModel
            Provider.ANTHROPIC -> settings.clTextModel
        }

    /** The quick models in order: the chosen fast model, then the normal one (and Flash-Lite at Google). */
    private fun quickChain(): List<String> =
        when (provider) {
            Provider.GOOGLE -> listOf(settings.geminiFastModel, settings.geminiTextModel, LITE_MODEL)
            Provider.OPENAI -> listOf(settings.oaFastModel, settings.oaTextModel)
            Provider.ANTHROPIC -> listOf(settings.clFastModel, settings.clTextModel)
        }.distinct()

    private fun readerModel(): String =
        when (provider) {
            Provider.GOOGLE -> settings.geminiReaderModel
            Provider.OPENAI -> settings.oaTextModel
            Provider.ANTHROPIC -> settings.clTextModel
        }

    fun providerLabel(): String = provider.label

    fun brainLabel(): String = pretty(brainModel())

    fun fastLabel(): String = pretty(quickChain().firstOrNull { usable(it) } ?: quickChain().first())

    fun examinerLabel(): String =
        when (provider) {
            Provider.GOOGLE -> pretty(settings.geminiLiveModel)
            Provider.OPENAI -> pretty(settings.oaLiveModel)
            Provider.ANTHROPIC -> "${pretty(settings.clFastModel)} mit Handy-Stimme"
        }

    fun listenerLabel(): String =
        when (provider) {
            Provider.GOOGLE -> pretty(brainModel())
            Provider.OPENAI -> pretty(settings.oaAudioModel)
            Provider.ANTHROPIC -> "Spracherkennung des Handys"
        }

    fun transcriberLabel(): String =
        when (provider) {
            Provider.GOOGLE -> pretty(settings.transcribeModel)
            Provider.OPENAI -> pretty(settings.oaTranscribeModel)
            Provider.ANTHROPIC -> "Spracherkennung des Handys"
        }

    fun readerLabel(): String = pretty(readerModel())

    fun searchLabel(): String =
        when (provider) {
            Provider.GOOGLE -> pretty(settings.embeddingModel)
            Provider.OPENAI -> pretty(settings.oaEmbeddingModel)
            Provider.ANTHROPIC -> "Stichwortsuche (ohne Vektoren)"
        }

    fun voiceLabel(): String =
        when (provider) {
            Provider.GOOGLE -> pretty(settings.ttsModel)
            Provider.OPENAI -> pretty(settings.oaTtsModel)
            Provider.ANTHROPIC -> "Stimme des Handys"
        }

    data class Answer(val json: JSONObject, val by: String)

    /** How much the model thinks: [INSTANT] for the checks while the student speaks, [FULL] for grading. */
    private enum class Effort(val gemini: String?, val openai: String?, val claude: String?, val think: Boolean) {
        INSTANT("low", "none", null, false),
        LOW("low", "low", "low", true),
        FULL(null, "medium", "medium", true),
    }

    private suspend fun jsonCall(model: String, system: String, parts: List<JSONObject>, schema: Sch, retries: Int, effort: Effort): JSONObject =
        when (provider) {
            Provider.GOOGLE -> gemini.generateJson(model, system, parts, schema, retries, effort.gemini)
            Provider.OPENAI -> openai.json(model, system, parts, schema, retries, effort.openai)
            Provider.ANTHROPIC -> claude.json(model, system, parts, schema, retries, effort.claude, think = effort.think)
        }

    private suspend fun textCall(model: String, system: String, turns: List<Pair<String, String>>, retries: Int, effort: Effort): String =
        when (provider) {
            Provider.GOOGLE -> gemini.generateText(model, system, turns, retries, effort.gemini)
            Provider.OPENAI -> openai.text(model, system, turns, retries, effort.openai)
            Provider.ANTHROPIC -> claude.text(model, system, turns, retries, effort.claude, think = effort.think)
        }

    /** The primary model, and if it is overloaded or not usable, the fallback. */
    private suspend fun <T> withFallback(primary: String, fallback: String, block: suspend (String) -> T): Pair<T, String> =
        try {
            block(primary) to primary
        } catch (e: AiException) {
            if (primary == fallback || !shouldFallBack(primary, e)) throw e
            lastNotice = fallbackNotice(primary, fallback, e)
            block(fallback) to fallback
        }

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

    // ---------- the jobs ----------

    /** The hard jobs: grading a whole session, writing exam material, plans. [thinking] false = little thinking, faster. */
    suspend fun brainJson(system: String, user: String, schema: Sch, thinking: Boolean = true): Answer {
        val parts = listOf(GeminiRest.textPart(user))
        val (json, model) = withFallback(brainModel(), normalModel()) { m -> jsonCall(m, system, parts, schema, 1, if (thinking) Effort.FULL else Effort.LOW) }
        return Answer(json, pretty(model))
    }

    /** Chat answers of the coach. Returns the text and the model that wrote it. */
    suspend fun brainText(system: String, turns: List<Pair<String, String>>): Pair<String, String> {
        val (text, model) = withFallback(brainModel(), normalModel()) { m -> textCall(m, system, turns, 1, Effort.FULL) }
        return text to pretty(model)
    }

    /** Quick checks during speaking: must answer within one or two seconds. */
    suspend fun fastJson(system: String, user: String, schema: Sch): JSONObject {
        val parts = listOf(GeminiRest.textPart(user))
        return quick { model -> jsonCall(model, system, parts, schema, 0, Effort.INSTANT) }.first
    }

    /** Reading pages, photos and documents. */
    suspend fun readJson(system: String, parts: List<JSONObject>, schema: Sch): JSONObject {
        val fallback = if (provider == Provider.GOOGLE) settings.geminiTextModel else quickChain().first()
        return withFallback(readerModel(), fallback) { m -> jsonCall(m, system, parts, schema, 2, Effort.LOW) }.first
    }

    /** Cheap text jobs that need no top model (summaries of the course material). */
    suspend fun cheapJson(system: String, user: String, schema: Sch): JSONObject {
        val parts = listOf(GeminiRest.textPart(user))
        return withFallback(normalModel(), quickChain().first()) { m -> jsonCall(m, system, parts, schema, 2, Effort.LOW) }.first
    }

    /** Listening to the whole recording of a session (not with Claude: see [hearsRecordings]). */
    suspend fun listenJson(system: String, parts: List<JSONObject>, schema: Sch): Answer =
        when (provider) {
            Provider.GOOGLE -> {
                val (json, model) = withFallback(brainModel(), settings.geminiTextModel) { m -> gemini.generateJson(m, system, parts, schema, 1, null) }
                Answer(json, pretty(model))
            }
            Provider.OPENAI -> Answer(openai.audioJson(settings.oaAudioModel, system, parts, schema, 1), pretty(settings.oaAudioModel))
            Provider.ANTHROPIC -> throw AiException("Claude kann keine Aufnahmen anhören", NOT_OFFERED)
        }

    /** A quick listen to a short recording (the ear, "Sag es richtig", speaking training): the fast model with little thinking. */
    suspend fun quickListenJson(system: String, parts: List<JSONObject>, schema: Sch): Answer =
        when (provider) {
            Provider.GOOGLE -> {
                val (json, model) = quick { model -> gemini.generateJson(model, system, parts, schema, retries = 1, thinking = "low") }
                Answer(json, pretty(model))
            }
            Provider.OPENAI -> {
                val audio = settings.oaAudioModel
                try {
                    if (!usable(audio)) throw AiException("Audiomodell gerade nicht nutzbar", 503)
                    Answer(openai.audioJson(audio, system, parts, schema, 1), pretty(audio))
                } catch (e: AiException) {
                    if (!shouldFallBack(audio, e) && !e.isTransient) throw e
                    // The audio model is out: write the recording down and judge the words.
                    val written = writtenDown(parts) { wav -> openai.transcribe(settings.oaFileTranscribeModel, wav) }
                    val (json, model) = quick { m -> openai.json(m, system + WRITTEN_NOTE, written, schema, 1, "low") }
                    Answer(json, pretty(model))
                }
            }
            Provider.ANTHROPIC -> {
                if (!phone.canRecognize) throw AiException("Mit Claude hört die Spracherkennung des Handys zu – dafür braucht es Android 13 oder neuer.", NOT_OFFERED)
                val written = writtenDown(parts) { wav -> Wav.toPcm(wav, 16_000)?.let { phone.transcribe(it) }.orEmpty() }
                val (json, model) = quick { m -> claude.json(m, system + WRITTEN_NOTE, written, schema, 1, null, maxTokens = 4_000, think = false) }
                Answer(json, pretty(model))
            }
        }

    /** Replaces the recordings in [parts] by what speech recognition understood in them. */
    private suspend fun writtenDown(parts: List<JSONObject>, transcribe: suspend (ByteArray) -> String): List<JSONObject> =
        parts.mapNotNull { p ->
            val inline = Parts.inline(p)
            when {
                inline != null && inline.first.startsWith("audio/") -> {
                    val wav = android.util.Base64.decode(inline.second, android.util.Base64.DEFAULT)
                    val text = runCatching { transcribe(wav) }.getOrElse { if (it is CancellationException) throw it else "" }.trim()
                    GeminiRest.textPart(if (text.isBlank()) "[Speech recognition understood nothing in the recording.]" else "[Speech recognition of the recording:] \"$text\"")
                }
                p.optJSONObject("fileData")?.optString("mimeType")?.startsWith("audio/") == true -> null
                else -> p
            }
        }

    suspend fun embed(texts: List<String>, query: Boolean, titles: List<String>? = null): List<FloatArray> =
        when (provider) {
            Provider.GOOGLE -> gemini.embed(settings.embeddingModel, texts, query, titles = titles)
            Provider.OPENAI -> openai.embed(settings.oaEmbeddingModel, texts)
            Provider.ANTHROPIC -> throw AiException("Claude bietet keine Suchvektoren an", NOT_OFFERED)
        }

    // ---------- the voice for reading aloud and the coach ----------

    private val speechMemory =
        object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ByteArray): Int = value.size
        }

    private val speechDir: File by lazy { File(appContext.cacheDir, "speech").apply { mkdirs() } }
    private val speechJobs = ConcurrentHashMap<String, Deferred<ByteArray?>>()
    private val prefetchSlots = Semaphore(2)

    /** Speech as 24 kHz PCM, cleaned (no noise after the last word); null if it failed (the app then uses Android's voice). */
    suspend fun speak(text: String): ByteArray? = speakStream(text, onChunk = null)

    /**
     * Speech, handed to [onChunk] piece by piece as soon as it arrives, so playback starts early. Every sentence is
     * kept (memory and disk): the second time it plays at once. The last 1.5 seconds are held back until the end
     * is known, because the noise some voices add comes after the last word and is cut away there.
     */
    suspend fun speakStream(text: String, holdMs: Int = HOLD_MS, voice: String? = null, onChunk: ((ByteArray) -> Unit)?): ByteArray? {
        val line = text.trim().take(MAX_SPEECH)
        if (line.isBlank() || (provider != Provider.ANTHROPIC && !ready)) return null
        val key = speechKey(line, voice)
        withContext(Dispatchers.IO) { cachedSpeech(key) }?.let { pcm ->
            onChunk?.invoke(pcm)
            return pcm
        }
        speechJobs[key]?.let { running ->
            val pcm = running.await()
            if (pcm != null) {
                onChunk?.invoke(pcm)
                return pcm
            }
        }
        return try {
            synthesizeClean(line, key, onChunk, holdMs, voice)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Makes the speech for [text] in the background, so that a tap on "Anhören" plays at once. */
    fun prefetchSpeech(text: String) {
        if (provider == Provider.ANTHROPIC || !ready) return
        val line = text.trim().take(MAX_SPEECH)
        if (line.isBlank()) return
        val key = speechKey(line)
        if (speechMemory.get(key) != null || speechJobs.containsKey(key)) return
        val job =
            scope.async(start = CoroutineStart.LAZY) {
                prefetchSlots.withPermit {
                    runCatching { cachedSpeech(key) ?: synthesizeClean(line, key, null) }.getOrNull()
                }
            }
        if (speechJobs.putIfAbsent(key, job) != null) return
        job.invokeOnCompletion { speechJobs.remove(key, job) }
        job.start()
    }

    private suspend fun synthesizeClean(line: String, key: String, onChunk: ((ByteArray) -> Unit)?, holdMs: Int = HOLD_MS, voice: String? = null): ByteArray? {
        val hold = RATE * 2 * holdMs / 1000
        val lock = Any()
        val buffer = java.io.ByteArrayOutputStream()
        var emitted = 0
        val raw =
            synthesize(line, voice) { piece ->
                if (onChunk == null) return@synthesize
                synchronized(lock) {
                    buffer.write(piece)
                    val upTo = (buffer.size() - hold) and 1.inv()
                    if (upTo - emitted >= MIN_PIECE) {
                        val out = buffer.toByteArray().copyOfRange(emitted, upTo)
                        if (emitted == 0) SpeechClean.fadeIn(out, RATE)
                        emitted = upTo
                        onChunk(out)
                    }
                }
            } ?: return null
        if (raw.size < 2_000) return null
        val cleaned = withContext(Dispatchers.Default) { SpeechClean.clean(raw, RATE) }
        if (onChunk != null) {
            synchronized(lock) {
                if (cleaned.size > emitted) onChunk(cleaned.copyOfRange(emitted, cleaned.size))
            }
        }
        withContext(Dispatchers.IO) { storeSpeech(key, cleaned) }
        return cleaned
    }

    /** Raw speech of the chosen company (24 kHz PCM), streamed to [onPiece]. */
    private suspend fun synthesize(line: String, voice: String? = null, onPiece: (ByteArray) -> Unit): ByteArray? =
        when (provider) {
            Provider.GOOGLE -> {
                var result: ByteArray? = null
                for (model in listOf(settings.ttsModel, LITE_TTS_MODEL).distinct().filter { usable(it) }) {
                    var sent = false
                    try {
                        result = gemini.ttsStream(model, "Read this aloud clearly in a natural ${settings.accent.english} English accent: $line", voice ?: settings.ttsVoice) { pcm ->
                            sent = true
                            onPiece(pcm)
                        }
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: AiException) {
                        if (sent || !shouldFallBack(model, e)) break
                    }
                }
                result
            }
            Provider.OPENAI -> {
                val model = settings.oaTtsModel
                try {
                    openai.tts(model, voice ?: settings.oaTtsVoice, line, OpenAiRest.voiceStyle(settings.accent.english), onPiece)
                } catch (e: AiException) {
                    shouldFallBack(model, e)
                    null
                }
            }
            Provider.ANTHROPIC -> phone.synthesize(line)?.also(onPiece)
        }

    private fun speechKey(line: String, override: String? = null): String {
        // Voice and accent belong to the key: a sentence with another voice or accent is a different recording.
        val voice =
            when (provider) {
                Provider.GOOGLE -> "g|${settings.ttsModel}|${override ?: settings.ttsVoice}|${settings.accent.name}"
                Provider.OPENAI -> "o|${settings.oaTtsModel}|${override ?: settings.oaTtsVoice}|${settings.accent.name}"
                Provider.ANTHROPIC -> "p"
            }
        val digest = MessageDigest.getInstance("SHA-1").digest("$CACHE_VERSION|$voice|$line".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cachedSpeech(key: String): ByteArray? {
        speechMemory.get(key)?.let { return it }
        val file = File(speechDir, "$key.pcm")
        if (!file.exists()) return null
        val pcm = runCatching { file.readBytes() }.getOrNull() ?: return null
        file.setLastModified(System.currentTimeMillis())
        speechMemory.put(key, pcm)
        return pcm
    }

    private fun storeSpeech(key: String, pcm: ByteArray) {
        speechMemory.put(key, pcm)
        runCatching {
            File(speechDir, "$key.pcm").writeBytes(pcm)
            var total = 0L
            speechDir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { f ->
                total += f.length()
                if (total > SPEECH_DISK_BYTES) f.delete()
            }
        }
    }

    // ---------- live conversation ----------

    /** The phone's ear of the current session (Claude): shared by the transcript and the examiner. */
    @Volatile private var phoneEar: PhoneEar? = null

    @Volatile private var phoneEarAt = 0L

    /** The examiner for a live conversation. */
    /** The examiner; with [manualTurns] the student holds a button while he speaks and the examiner answers on release. */
    fun examiner(instructions: String, manualTurns: Boolean = false, onEvent: (LiveEvent) -> Unit): VoiceAgent =
        when (provider) {
            Provider.GOOGLE -> {
                val socket =
                    LiveSocket(
                        http = http,
                        apiKey = settings.geminiKey,
                        name = "Prüfer",
                        supportsResumption = true,
                        setupFactory = { handle, minimal -> GeminiSetup.examiner(settings.geminiLiveModel, settings.geminiVoice, instructions, handle, minimal, manualTurns) },
                        onEvent = onEvent,
                    )
                GeminiVoiceAgent(socket, pretty(settings.geminiLiveModel))
            }
            Provider.OPENAI ->
                // GPT-Live first; if the key cannot reach it, the Realtime model takes over.
                FallbackVoiceAgent(
                    first = { events -> OpenAiLiveAgent(http, settings.openAiKey, settings.oaLiveModel, settings.oaVoice, instructions, events, pretty(settings.oaLiveModel), manualTurns) },
                    second = { events -> OpenAiRealtimeAgent(http, settings.openAiKey, settings.oaRealtimeModel, settings.oaVoice, instructions, events, pretty(settings.oaRealtimeModel), manualTurns) },
                    onEvent = onEvent,
                )
            Provider.ANTHROPIC -> {
                val ear = phoneEar?.takeIf { System.currentTimeMillis() - phoneEarAt < 15_000 }
                PhoneVoiceAgent(
                    phone = phone,
                    ear = ear,
                    instructions = instructions,
                    answer = { system, turns -> quick { m -> claude.text(m, system, turns, retries = 1, maxTokens = 600, think = false) }.first },
                    onEvent = onEvent,
                    label = examinerLabel(),
                    manualTurns = manualTurns,
                )
            }
        }

    /** The verbatim transcriber ("second ear"). */
    fun transcriber(vocabulary: List<String>, onEvent: (LiveEvent) -> Unit): LiveTranscriber =
        when (provider) {
            Provider.GOOGLE ->
                LiveSocket(
                    http = http,
                    apiKey = settings.geminiKey,
                    name = "Mitschrift",
                    supportsResumption = false,
                    setupFactory = { _, minimal -> GeminiSetup.transcriber(settings.transcribeModel, vocabulary, minimal, settings.accent.bcp47) },
                    onEvent = onEvent,
                )
            Provider.OPENAI -> OpenAiTranscriber(http, settings.openAiKey, settings.oaTranscribeModel, vocabulary, onEvent)
            Provider.ANTHROPIC ->
                PhoneEar(phone).also {
                    it.onEvent = onEvent
                    phoneEar = it
                    phoneEarAt = System.currentTimeMillis()
                }
        }

    // ---------- key ----------

    /** Null when the key of [p] works, otherwise a readable error. */
    suspend fun checkKey(p: Provider = provider): String? =
        when (p) {
            Provider.GOOGLE -> if (settings.geminiKey.isBlank()) "Es ist kein Google-Schlüssel eingetragen." else gemini.checkKey()
            Provider.OPENAI -> openai.checkKey()
            Provider.ANTHROPIC -> claude.checkKey()
        }

    private fun fallbackNotice(model: String, fallback: String, e: AiException): String =
        if (e.isOverloaded || e.code == 429 || e.code == 529) {
            "${pretty(model)} ist gerade überlastet. ${pretty(fallback)} hat übernommen – in ein paar Minuten ist wieder ${pretty(model)} dran."
        } else {
            "${pretty(model)} ist für deinen Schlüssel gerade nicht nutzbar (${e.message}). ${pretty(fallback)} hat übernommen – etwas weniger genau."
        }

    // ---------- work that runs in Google's cloud ----------

    /** A cloud job of the brain (grading): the job id and the model that took it. */
    data class CloudJobStart(val id: String, val model: String)

    /**
     * Starts a grading job in Google's cloud (background interaction) with the brain; if it is overloaded or not
     * usable, with the normal model. The phone can be switched off while it runs. Google only.
     */
    suspend fun startCloudBrain(system: String, parts: List<JSONObject>, schema: Sch): CloudJobStart {
        if (provider != Provider.GOOGLE) throw AiException("Auswertung in der Cloud gibt es nur bei Google", NOT_OFFERED)
        val (id, model) = withFallback(brainModel(), settings.geminiTextModel) { m -> gemini.startCloud(m, system, parts, schema) }
        return CloudJobStart(id, model)
    }

    /** A tiny cloud job with the quick model (system test). */
    suspend fun startCloudQuick(prompt: String): CloudJobStart =
        CloudJobStart(gemini.startCloud(settings.geminiFastModel, "", listOf(GeminiRest.textPart(prompt)), null), settings.geminiFastModel)

    companion object {
        /** Last resort for the quick jobs when Flash is overloaded. */
        const val LITE_MODEL = "gemini-3.5-flash-lite"
        const val LITE_TTS_MODEL = "gemini-3.8-flash-lite-tts"

        /** The job is not offered by the chosen company (e.g. listening with Claude). */
        const val NOT_OFFERED = -4

        private const val BUSY_MS = 3 * 60_000L
        private const val RATE = 24_000
        private const val HOLD_MS = 1_500
        private const val MIN_PIECE = 4_800
        private const val MAX_SPEECH = 3_000
        private const val SPEECH_DISK_BYTES = 40L * 1024 * 1024
        private const val CACHE_VERSION = 2

        /** Tells Gemini's voice how to read; it speaks only the text after the colon. */

        private const val WRITTEN_NOTE =
            "\n\nIMPORTANT: You cannot hear the recording. You get what speech recognition understood instead (it may smooth over small slips). " +
                "Judge words, grammar and content from it. Do not invent pronunciation problems: rate pronunciation and fluency as neutral and say that only the words were checked."

        private val UPPER = setOf("gpt", "tts", "ai")

        /** "gemini-3.8-flash" → "Gemini 3.8 Flash", "gpt-6.1-sol" → "GPT 6.1 Sol", "claude-opus-5-5" → "Claude Opus 5.5". */
        fun pretty(model: String): String {
            val id = model.removePrefix("models/")
            val parts = id.split("-")
            val out = ArrayList<String>()
            var i = 0
            while (i < parts.size) {
                val p = parts[i]
                if (p.isNotEmpty() && p.all { it.isDigit() } && i + 1 < parts.size && parts[i + 1].isNotEmpty() && parts[i + 1].all { it.isDigit() } && parts[i + 1].length <= 2) {
                    out += "$p.${parts[i + 1]}"
                    i += 2
                    continue
                }
                out += if (p.lowercase() in UPPER) p.uppercase() else p.replaceFirstChar { it.uppercase() }
                i++
            }
            return out.joinToString(" ")
        }
    }
}
