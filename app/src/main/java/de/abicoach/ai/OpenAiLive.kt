package de.abicoach.ai

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** Turns the microphone's 16 kHz audio into the 24 kHz that OpenAI's live models expect (linear interpolation). */
class Upsampler16to24 {
    /** Last input sample of the previous block, so that the blocks join without a click. */
    private var prev = 0

    /** Where the next output sample lies, in input samples counted from [prev]. */
    private var phase = 0.0

    fun process(pcm16k: ByteArray): ByteArray {
        val n = pcm16k.size / 2
        if (n == 0) return ByteArray(0)
        val samples = IntArray(n + 1)
        samples[0] = prev
        for (i in 0 until n) samples[i + 1] = (pcm16k[2 * i].toInt() and 0xFF) or (pcm16k[2 * i + 1].toInt() shl 8)
        val out = java.io.ByteArrayOutputStream(n * 3 + 4)
        var t = phase
        while (t < n) {
            val i = t.toInt()
            val f = t - i
            val v = (samples[i] + (samples[i + 1] - samples[i]) * f).toInt().coerceIn(-32768, 32767)
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
            t += 2.0 / 3.0
        }
        phase = t - n
        prev = samples[n]
        return out.toByteArray()
    }
}

/**
 * OpenAI GPT-Live as the examiner, over the Live API WebSocket (wss://api.openai.com/v1/live/sessions). The model
 * is full-duplex: it listens while it speaks and decides itself when to take the turn, so it can cut into a long
 * monologue like a real examiner. Audio is 16-bit PCM at 24 kHz in both directions.
 */
class OpenAiLiveAgent(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val voice: String,
    private val instructions: String,
    private val onEvent: (LiveEvent) -> Unit,
    override val label: String,
    /** "Knopf gedrückt halten": only the held audio goes in, and on release the examiner is told to answer now. */
    private val manualTurns: Boolean = false,
) : VoiceAgent {
    override val outputRate: Int = 24_000

    private val url = "wss://api.openai.com/v1/live/sessions"
    private val ids = AtomicInteger(0)
    private val resampler = Upsampler16to24()

    @Volatile private var socket: WebSocket? = null

    @Volatile private var ready = false

    @Volatile private var closedByUser = false

    @Volatile private var reconnects = 0

    @Volatile private var sendHistory = false

    /** Instructions that arrived before the session was ready. */
    private val pending = ArrayList<String>()

    // Transcript pieces are grouped into turns here: the Live API has no turn-complete event.
    private val lock = Any()
    private val modelTurn = StringBuilder()
    private val userTurn = StringBuilder()
    private val history = ArrayList<Pair<String, String>>()

    @Volatile private var lastModelOutputAt = 0L

    @Volatile private var modelSpeaking = false

    @Volatile private var watchdog: Thread? = null

    override fun connect() {
        closedByUser = false
        ready = false
        socket = http.newWebSocket(Request.Builder().url(url).header("Authorization", "Bearer $apiKey").build(), Listener())
        startWatchdog()
    }

    override fun sendAudio(pcm16k: ByteArray) {
        if (!ready) return
        val pcm24 = resampler.process(pcm16k)
        if (pcm24.isEmpty()) return
        socket?.send(JSONObject().put("type", "session.input_audio.append").put("audio", Base64.encodeToString(pcm24, Base64.NO_WRAP)).toString())
    }

    /** GPT-Live takes instructions into account at once; it decides itself when to say something. */
    override fun sendInstruction(text: String): Boolean {
        if (!ready) {
            synchronized(pending) { pending += text }
            return true
        }
        val message =
            JSONObject()
                .put("type", "session.instructions.append")
                .put("event_id", "app_${ids.incrementAndGet()}")
                .put("content", text.take(1800))
        return socket?.send(message.toString()) ?: false
    }

    override fun sendNote(text: String): Boolean = sendInstruction(text)

    override fun sendStreamEnd() = Unit

    override fun turnStart() {
        if (!manualTurns) return
        if (modelSpeaking) {
            modelSpeaking = false
            onEvent(LiveEvent.Interrupted)
        }
    }

    override fun turnEnd() {
        if (manualTurns) sendInstruction("The student has let go of the talk button: he has finished his turn. Answer him now.")
    }

    override fun close() {
        closedByUser = true
        val s = socket
        if (s != null && ready) {
            runCatching { s.send(JSONObject().put("type", "session.close").toString()) }
            Thread {
                runCatching { Thread.sleep(1500) }
                s.close(1000, "done")
            }.apply { isDaemon = true }.start()
        } else {
            s?.close(1000, "done")
        }
        ready = false
        socket = null
        watchdog?.interrupt()
        watchdog = null
    }

    private fun startSession(webSocket: WebSocket) {
        val session =
            JSONObject()
                .put("model", model)
                .put("instructions", instructions)
                .put(
                    "audio",
                    JSONObject()
                        .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24_000))
                        .put("output", JSONObject().put("voice", voice)),
                )
        if (sendHistory) {
            val input = JSONArray()
            val recent = synchronized(lock) { history.takeLast(40) }
            var chars = 0
            recent.reversed().takeWhile { (_, t) -> chars += t.length; chars < 24_000 }.reversed().forEach { (role, t) ->
                input.put(JSONObject().put("role", role).put("content", t))
            }
            input.put(JSONObject().put("role", "developer").put("content", "The connection dropped for a moment. Continue the conversation naturally from where it stopped."))
            session.put("input", input)
        }
        webSocket.send(JSONObject().put("type", "session.start").put("event_id", "start_${ids.incrementAndGet()}").put("session", session).toString())
    }

    private fun handle(text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (message.optString("type")) {
            "session.started" -> {
                ready = true
                reconnects = 0
                onEvent(LiveEvent.Ready)
                val queued = synchronized(pending) { pending.toList().also { pending.clear() } }
                queued.forEach { sendInstruction(it) }
            }
            "session.output_audio.delta" -> {
                val b64 = message.optString("delta")
                if (b64.isNotEmpty()) {
                    modelOutput()
                    onEvent(LiveEvent.Audio(Base64.decode(b64, Base64.DEFAULT)))
                }
            }
            "session.output_transcript.delta" -> {
                val delta = message.optString("delta")
                if (delta.isNotEmpty()) {
                    modelOutput()
                    synchronized(lock) { modelTurn.append(delta) }
                    onEvent(LiveEvent.ModelText(delta))
                }
            }
            "session.input_transcript.delta" -> {
                val delta = message.optString("delta")
                if (delta.isNotEmpty()) {
                    synchronized(lock) { userTurn.append(delta) }
                    onEvent(LiveEvent.UserText(delta))
                }
            }
            "error" -> {
                val error = message.optJSONObject("error")
                val msg = error?.optString("message").orEmpty().ifBlank { "unbekannter Fehler" }
                if (!ready) {
                    if (sendHistory) {
                        // The saved history may have been rejected: start fresh instead.
                        sendHistory = false
                        socket?.let { startSession(it) }
                    } else {
                        onEvent(LiveEvent.Failed("$label: $msg"))
                    }
                } else {
                    onEvent(LiveEvent.Info("$label: $msg"))
                }
            }
            "session.closed" -> {
                val reason = message.optString("reason")
                ready = false
                if (!closedByUser) {
                    if (reason == "content") onEvent(LiveEvent.Failed("$label hat die Sitzung beendet (Inhaltsregeln).")) else reconnect(reason.ifBlank { "geschlossen" })
                }
            }
        }
    }

    private fun modelOutput() {
        lastModelOutputAt = System.currentTimeMillis()
        if (!modelSpeaking) {
            modelSpeaking = true
            synchronized(lock) {
                val user = userTurn.toString().trim()
                userTurn.setLength(0)
                if (user.isNotEmpty()) history += "user" to user
            }
        }
    }

    /** Ends the examiner's turn after a short silence (the API has no event for it). */
    private fun startWatchdog() {
        if (watchdog != null) return
        watchdog =
            Thread({
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        Thread.sleep(250)
                        if (modelSpeaking && System.currentTimeMillis() - lastModelOutputAt > 1_300) {
                            modelSpeaking = false
                            synchronized(lock) {
                                val said = modelTurn.toString().trim()
                                modelTurn.setLength(0)
                                if (said.isNotEmpty()) history += "assistant" to said
                            }
                            onEvent(LiveEvent.TurnComplete)
                        }
                    }
                } catch (e: InterruptedException) {
                }
            }, "gpt-live-turns").apply {
                isDaemon = true
                start()
            }
    }

    private fun reconnect(reason: String) {
        if (closedByUser) return
        reconnects++
        if (reconnects > 3) {
            onEvent(LiveEvent.Failed("$label: Verbindung verloren ($reason)"))
            return
        }
        onEvent(LiveEvent.Info("$label: verbinde neu …"))
        sendHistory = synchronized(lock) { history.isNotEmpty() }
        socket = http.newWebSocket(Request.Builder().url(url).header("Authorization", "Bearer $apiKey").build(), Listener())
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = startSession(webSocket)

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket === socket) handle(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (webSocket === socket) handle(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket || closedByUser) return
            val wasReady = ready
            ready = false
            if (wasReady) reconnect("Code $code") else onEvent(LiveEvent.Failed("$label: ${reason.ifBlank { "Code $code" }}"))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket || closedByUser) return
            val wasReady = ready
            ready = false
            val why =
                when (response?.code) {
                    401 -> "OpenAI-Schlüssel ungültig"
                    403 -> "kein Zugriff auf $label"
                    404 -> "$label nicht gefunden"
                    429 -> "OpenAI: Limit oder Guthaben erschöpft"
                    else -> t.message ?: t.javaClass.simpleName
                }
            if (wasReady) reconnect(why) else onEvent(LiveEvent.Failed("$label: $why"))
        }
    }
}

/**
 * The verbatim transcript with OpenAI: a Realtime transcription session. Utterances are cut by the server's voice
 * detection; the running text comes as interim, each finished utterance as text.
 */
class OpenAiTranscriber(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val vocabulary: List<String>,
    private val onEvent: (LiveEvent) -> Unit,
) : LiveTranscriber {
    private val url = "wss://api.openai.com/v1/realtime?intent=transcription"
    private val resampler = Upsampler16to24()
    private val lock = Any()
    private val interim = LinkedHashMap<String, StringBuilder>()

    /** The chosen model first; if the key cannot use it, the older transcription models. */
    private val models = listOf(model, "gpt-realtime-whisper", "gpt-4o-transcribe").distinct()

    @Volatile private var modelIndex = 0

    @Volatile private var socket: WebSocket? = null

    @Volatile override var ready = false
        private set

    @Volatile private var closedByUser = false

    @Volatile private var failures = 0

    override fun connect() {
        closedByUser = false
        ready = false
        socket = http.newWebSocket(Request.Builder().url(url).header("Authorization", "Bearer $apiKey").build(), Listener())
    }

    override fun sendAudio(pcm16k: ByteArray) {
        if (!ready) return
        val pcm24 = resampler.process(pcm16k)
        if (pcm24.isEmpty()) return
        socket?.send(JSONObject().put("type", "input_audio_buffer.append").put("audio", Base64.encodeToString(pcm24, Base64.NO_WRAP)).toString())
    }

    override fun sendStreamEnd() {
        runCatching { socket?.send(JSONObject().put("type", "input_audio_buffer.commit").toString()) }
    }

    override fun reconnectNow() {
        val old = socket
        socket = null
        ready = false
        runCatching { old?.close(1000, "renew") }
        connect()
    }

    override fun close() {
        closedByUser = true
        ready = false
        socket?.close(1000, "done")
        socket = null
    }

    private fun configure(webSocket: WebSocket) {
        val transcription = JSONObject().put("model", models[modelIndex]).put("language", "en")
        val words = vocabulary.filter { it.length in 2..40 }.take(40)
        // The prompt shows the style wanted: every filler and false start written down (otherwise they are left out).
        transcription.put(
            "prompt",
            "Umm, so, uh, I think the, er, the author wants to, hmm, show that… Verbatim transcript of a learner, with all fillers (um, uh, er, hmm), " +
                "repetitions and mistakes." + if (words.isNotEmpty()) " Names and terms: " + words.joinToString(", ") else "",
        )
        val input =
            JSONObject()
                .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24_000))
                .put("transcription", transcription)
                .put("turn_detection", JSONObject().put("type", "server_vad").put("threshold", 0.5).put("prefix_padding_ms", 300).put("silence_duration_ms", 450))
                .put("noise_reduction", JSONObject().put("type", "near_field"))
        val session = JSONObject().put("type", "transcription").put("audio", JSONObject().put("input", input))
        webSocket.send(JSONObject().put("type", "session.update").put("session", session).toString())
    }

    private fun handle(text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (message.optString("type")) {
            // Ready only once our settings (model, language, pause detection) are taken.
            "session.updated", "transcription_session.updated" -> becomeReady()
            "session.created", "transcription_session.created" -> {
                // Should the confirmation never come, the session works with what it has after three seconds.
                val ws = socket
                val at = modelIndex
                Thread {
                    runCatching { Thread.sleep(3_000) }
                    if (socket === ws && !closedByUser && modelIndex == at) becomeReady()
                }.apply { isDaemon = true }.start()
            }
            "conversation.item.input_audio_transcription.delta" -> {
                val id = message.optString("item_id")
                val delta = message.optString("delta")
                if (delta.isEmpty()) return
                val all = synchronized(lock) { interim.getOrPut(id) { StringBuilder() }.append(delta).toString() }
                onEvent(LiveEvent.UserInterim(all.trim()))
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val id = message.optString("item_id")
                synchronized(lock) { interim.remove(id) }
                val done = message.optString("transcript").trim()
                if (done.isNotEmpty()) onEvent(LiveEvent.UserText(done))
            }
            "error" -> {
                val msg = message.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "unbekannter Fehler" }
                val ws = socket
                when {
                    ready -> onEvent(LiveEvent.Info("OpenAI-Mitschrift: $msg"))
                    modelIndex < models.lastIndex && ws != null -> {
                        // The model is not offered for this key: the next one.
                        modelIndex++
                        configure(ws)
                    }
                    else -> onEvent(LiveEvent.Failed("OpenAI-Mitschrift: $msg"))
                }
            }
        }
    }

    @Synchronized
    private fun becomeReady() {
        if (ready) return
        ready = true
        failures = 0
        onEvent(LiveEvent.Ready)
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = configure(webSocket)

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket === socket) handle(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (webSocket === socket) handle(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket || closedByUser) return
            ready = false
            if (failures++ < 3) connect() else onEvent(LiveEvent.Failed("OpenAI-Mitschrift: Verbindung verloren (Code $code)"))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket || closedByUser) return
            val wasReady = ready
            ready = false
            val why =
                when (response?.code) {
                    401 -> "OpenAI-Schlüssel ungültig"
                    403, 404 -> "kein Zugriff auf $model"
                    429 -> "Limit oder Guthaben erschöpft"
                    else -> t.message ?: t.javaClass.simpleName
                }
            if (wasReady && failures++ < 3) connect() else onEvent(LiveEvent.Failed("OpenAI-Mitschrift: $why"))
        }
    }
}

/**
 * OpenAI's Realtime API (gpt-realtime) as the examiner – the safety net when GPT-Live cannot be reached with the key.
 * Speech in and out as 24 kHz PCM; the server notices when the student has finished and answers, and stops talking
 * when the student cuts in.
 */
class OpenAiRealtimeAgent(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val voice: String,
    private val instructions: String,
    private val onEvent: (LiveEvent) -> Unit,
    override val label: String,
    /** "Knopf gedrückt halten": no voice detection on the server; the app commits the turn on release. */
    private val manualTurns: Boolean = false,
) : VoiceAgent {
    override val outputRate: Int = 24_000

    private val resampler = Upsampler16to24()

    @Volatile private var socket: WebSocket? = null

    @Volatile private var ready = false

    @Volatile private var closedByUser = false

    @Volatile private var speaking = false

    @Volatile private var reconnects = 0

    private val pending = ArrayList<Pair<String, Boolean>>()

    override fun connect() {
        closedByUser = false
        ready = false
        socket = http.newWebSocket(Request.Builder().url("wss://api.openai.com/v1/realtime?model=$model").header("Authorization", "Bearer $apiKey").build(), Listener())
    }

    override fun sendAudio(pcm16k: ByteArray) {
        if (!ready) return
        val pcm24 = resampler.process(pcm16k)
        if (pcm24.isEmpty()) return
        socket?.send(JSONObject().put("type", "input_audio_buffer.append").put("audio", Base64.encodeToString(pcm24, Base64.NO_WRAP)).toString())
    }

    override fun sendInstruction(text: String): Boolean = post(text, answer = true)

    override fun sendNote(text: String): Boolean = post(text, answer = false)

    /** A message from the app; [answer] makes the examiner react at once (otherwise it shapes his next turn). */
    private fun post(text: String, answer: Boolean): Boolean {
        if (!ready) {
            synchronized(pending) { pending += text to answer }
            return true
        }
        val s = socket ?: return false
        val item =
            JSONObject()
                .put("type", "message")
                .put("role", if (answer) "user" else "system")
                .put("content", JSONArray().put(JSONObject().put("type", "input_text").put("text", text.take(1800))))
        val sent = s.send(JSONObject().put("type", "conversation.item.create").put("item", item).toString())
        if (sent && answer) s.send(JSONObject().put("type", "response.create").toString())
        return sent
    }

    override fun sendStreamEnd() = Unit

    override fun turnStart() {
        if (!manualTurns || !ready) return
        val s = socket ?: return
        if (speaking) {
            speaking = false
            s.send(JSONObject().put("type", "response.cancel").toString())
            onEvent(LiveEvent.Interrupted)
        }
        s.send(JSONObject().put("type", "input_audio_buffer.clear").toString())
    }

    override fun turnEnd() {
        if (!manualTurns || !ready) return
        val s = socket ?: return
        s.send(JSONObject().put("type", "input_audio_buffer.commit").toString())
        s.send(JSONObject().put("type", "response.create").toString())
    }

    override fun close() {
        closedByUser = true
        ready = false
        socket?.close(1000, "done")
        socket = null
    }

    private fun configure(webSocket: WebSocket) {
        val input =
            JSONObject()
                .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24_000))
                .put("transcription", JSONObject().put("model", "gpt-4o-mini-transcribe").put("language", "en"))
                .put(
                    "turn_detection",
                    if (manualTurns) {
                        JSONObject.NULL
                    } else {
                        JSONObject().put("type", "server_vad").put("silence_duration_ms", 700).put("create_response", true).put("interrupt_response", true)
                    },
                )
        val output = JSONObject().put("format", JSONObject().put("type", "audio/pcm").put("rate", 24_000)).put("voice", voice)
        val session =
            JSONObject()
                .put("type", "realtime")
                .put("instructions", instructions)
                .put("output_modalities", JSONArray().put("audio"))
                .put("audio", JSONObject().put("input", input).put("output", output))
        webSocket.send(JSONObject().put("type", "session.update").put("session", session).toString())
    }

    private fun handle(text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (message.optString("type")) {
            "session.updated" -> {
                if (!ready) {
                    ready = true
                    reconnects = 0
                    onEvent(LiveEvent.Ready)
                    val queued = synchronized(pending) { pending.toList().also { pending.clear() } }
                    queued.forEach { (t, answer) -> post(t, answer) }
                }
            }
            "response.output_audio.delta", "response.audio.delta" -> {
                val b64 = message.optString("delta")
                if (b64.isNotEmpty()) {
                    speaking = true
                    onEvent(LiveEvent.Audio(Base64.decode(b64, Base64.DEFAULT)))
                }
            }
            "response.output_audio_transcript.delta", "response.audio_transcript.delta" -> {
                val delta = message.optString("delta")
                if (delta.isNotEmpty()) onEvent(LiveEvent.ModelText(delta))
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val said = message.optString("transcript").trim()
                if (said.isNotEmpty()) onEvent(LiveEvent.UserText(said))
            }
            "input_audio_buffer.speech_started" -> {
                if (speaking) {
                    speaking = false
                    onEvent(LiveEvent.Interrupted)
                }
            }
            "response.done" -> {
                speaking = false
                onEvent(LiveEvent.TurnComplete)
            }
            "error" -> {
                val msg = message.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "unbekannter Fehler" }
                if (!ready) onEvent(LiveEvent.Failed("$label: $msg")) else onEvent(LiveEvent.Info("$label: $msg"))
            }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = configure(webSocket)

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket === socket) handle(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (webSocket === socket) handle(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket || closedByUser) return
            val wasReady = ready
            ready = false
            if (wasReady && reconnects++ < 3) {
                onEvent(LiveEvent.Info("$label: verbinde neu …"))
                connect()
            } else {
                onEvent(LiveEvent.Failed("$label: ${reason.ifBlank { "Code $code" }}"))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket || closedByUser) return
            val wasReady = ready
            ready = false
            val why =
                when (response?.code) {
                    401 -> "OpenAI-Schlüssel ungültig"
                    403 -> "kein Zugriff auf $model"
                    404 -> "$model nicht gefunden"
                    429 -> "Limit oder Guthaben erschöpft"
                    else -> t.message ?: t.javaClass.simpleName
                }
            if (wasReady && reconnects++ < 3) {
                onEvent(LiveEvent.Info("$label: verbinde neu …"))
                connect()
            } else {
                onEvent(LiveEvent.Failed("$label: $why"))
            }
        }
    }
}

/**
 * Tries the [first] examiner; if it fails before it was ever ready (model not offered for the key, wrong address),
 * the [second] one takes over with the same instructions – the conversation does not notice.
 */
class FallbackVoiceAgent(
    first: ((LiveEvent) -> Unit) -> VoiceAgent,
    private val second: ((LiveEvent) -> Unit) -> VoiceAgent,
    private val onEvent: (LiveEvent) -> Unit,
) : VoiceAgent {
    @Volatile private var current: VoiceAgent

    @Volatile private var switched = false

    @Volatile private var readySeen = false

    init {
        current = first { event -> fromFirst(event) }
    }

    override val label: String get() = current.label

    override val outputRate: Int get() = current.outputRate

    private fun fromFirst(event: LiveEvent) {
        if (switched) return
        if (event is LiveEvent.Ready) readySeen = true
        if (event is LiveEvent.Failed && !readySeen) {
            switched = true
            val old = current
            val next = second(onEvent)
            current = next
            runCatching { old.close() }
            onEvent(LiveEvent.Info("${old.label} ist nicht erreichbar (${event.message.take(80)}) – ${next.label} übernimmt."))
            next.connect()
            return
        }
        onEvent(event)
    }

    override fun connect() = current.connect()

    override fun sendAudio(pcm16k: ByteArray) = current.sendAudio(pcm16k)

    override fun sendInstruction(text: String): Boolean = current.sendInstruction(text)

    override fun sendNote(text: String): Boolean = current.sendNote(text)

    override fun sendStreamEnd() = current.sendStreamEnd()

    override fun turnStart() = current.turnStart()

    override fun turnEnd() = current.turnEnd()

    override fun close() = current.close()
}
