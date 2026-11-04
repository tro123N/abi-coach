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
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Setup messages for the two kinds of Gemini Live sessions. */
object GeminiSetup {
    /** The "second ear": text-only session that transcribes the microphone word for word, mistakes included. */
    fun transcriber(model: String, vocabulary: List<String>, minimal: Boolean): JSONObject {
        val transcription = JSONObject().put("mode", "VERBATIM")
        if (!minimal) {
            transcription.put("languageCodes", JSONArray().put("en-GB"))
            transcription.put("customVocabulary", JSONArray(vocabulary.distinct().take(100)))
        }
        val setup =
            JSONObject()
                .put("model", "models/" + model.removePrefix("models/"))
                .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT")))
                .put("inputAudioTranscription", transcription)
        return JSONObject().put("setup", setup)
    }

    /** The examiner: Gemini Live with a voice. */
    fun examiner(model: String, voice: String, system: String, handle: String?, minimal: Boolean): JSONObject {
        val generation =
            JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put(
                    "speechConfig",
                    JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))),
                )
        val setup =
            JSONObject()
                .put("model", "models/" + model.removePrefix("models/"))
                .put("generationConfig", generation)
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("outputAudioTranscription", JSONObject())
                .put("inputAudioTranscription", JSONObject())
        if (!minimal) {
            // Learners pause to think: wait longer before treating silence as the end of a turn.
            setup.put(
                "realtimeInputConfig",
                JSONObject().put(
                    "automaticActivityDetection",
                    JSONObject().put("endOfSpeechSensitivity", "END_SENSITIVITY_LOW").put("silenceDurationMs", 1400),
                ),
            )
            setup.put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            setup.put("sessionResumption", if (handle != null) JSONObject().put("handle", handle) else JSONObject())
        }
        return JSONObject().put("setup", setup)
    }
}

/**
 * One WebSocket connection to the Gemini Live API (examiner or verbatim transcriber).
 * Reconnects by itself when Google ends a session (time limit, goAway); the examiner session resumes with
 * its context via a session-resumption handle.
 */
class LiveSocket(
    private val http: OkHttpClient,
    apiKey: String,
    private val name: String,
    private val supportsResumption: Boolean,
    /** Builds the setup message. Arguments: resumption handle (or null) and whether to use the minimal setup. */
    private val setupFactory: (String?, Boolean) -> JSONObject,
    private val onEvent: (LiveEvent) -> Unit,
) {
    private val url =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService." +
            "BidiGenerateContent?key=" + URLEncoder.encode(apiKey, "UTF-8")

    @Volatile private var socket: WebSocket? = null

    @Volatile var ready = false
        private set

    @Volatile private var closedByUser = false

    @Volatile private var resumeHandle: String? = null

    @Volatile private var useMinimalSetup = false

    @Volatile private var failedReconnects = 0

    fun connect() {
        closedByUser = false
        ready = false
        socket = http.newWebSocket(Request.Builder().url(url).build(), Listener())
    }

    /** Audio that arrived while the connection was (re)opening, at most about three seconds: nothing the student says gets lost. */
    private val backlog = java.util.ArrayDeque<ByteArray>()

    fun sendAudio(pcm: ByteArray) {
        val current = socket
        if (!ready || current == null) {
            synchronized(backlog) {
                backlog.addLast(pcm)
                while (backlog.size > 30) backlog.removeFirst()
            }
            return
        }
        synchronized(backlog) {
            while (backlog.isNotEmpty()) current.send(audioMessage(backlog.removeFirst()))
        }
        current.send(audioMessage(pcm))
    }

    private fun audioMessage(pcm: ByteArray): String =
        JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "audio",
                JSONObject()
                    .put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
                    .put("mimeType", "audio/pcm;rate=16000"),
            ),
        ).toString()

    /** Tells the server that the microphone stopped, so the last utterance gets finalised. */
    fun sendStreamEnd() {
        if (!ready) return
        socket?.send(JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)).toString())
    }

    /**
     * Sends a text turn (used to start the examiner and for coach instructions). With Gemini 3.8 Live a finished
     * client turn is answered at once, even in the middle of the conversation. Returns false if the connection is not ready.
     */
    fun sendUserTurn(text: String): Boolean {
        val current = socket
        if (!ready || current == null) return false
        val turn = JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text)))
        val message = JSONObject().put("clientContent", JSONObject().put("turns", JSONArray().put(turn)).put("turnComplete", true))
        return current.send(message.toString())
    }

    /** Replaces a connection that seems dead by a fresh one; the old one is closed once the new one is started. */
    fun reconnectNow() {
        if (closedByUser) return
        val old = socket
        onEvent(LiveEvent.Info("$name: verbinde neu …"))
        connect()
        old?.close(1000, "refresh")
    }

    fun close() {
        closedByUser = true
        ready = false
        socket?.close(1000, "done")
        socket = null
    }

    /**
     * Opens a new connection after a growing pause (0.5, 1, 2, 4, 8 … seconds): a weak network or a DNS hiccup
     * usually passes within seconds, and hammering the server does not help. Gives up after about 40 seconds.
     */
    private fun reconnect(reason: String) {
        if (closedByUser) return
        failedReconnects++
        if (failedReconnects > MAX_RECONNECTS) {
            onEvent(LiveEvent.Failed("$name: Verbindung verloren ($reason)"))
            return
        }
        val wait = minOf(8_000L, 500L shl minOf(failedReconnects - 1, 4))
        onEvent(LiveEvent.Info("$name: verbinde neu …" + if (wait >= 2_000) " (in ${wait / 1000} s)" else ""))
        scheduler.schedule(Runnable { if (!closedByUser) connect() }, wait, TimeUnit.MILLISECONDS)
    }

    private fun handle(listener: Listener, text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        when {
            message.has("setupComplete") -> {
                listener.gotSetup = true
                ready = true
                failedReconnects = 0
                onEvent(LiveEvent.Ready)
            }
            message.has("serverContent") -> parseContent(message.getJSONObject("serverContent"))
            message.has("sessionResumptionUpdate") -> {
                val update = message.getJSONObject("sessionResumptionUpdate")
                val handle = update.optString("newHandle", "")
                if (update.optBoolean("resumable", false) && handle.isNotEmpty()) resumeHandle = handle
            }
            message.has("goAway") -> {
                // Google ends this connection soon: open the next one now and drop the old one.
                val old = socket
                onEvent(LiveEvent.Info("$name: Sitzung wird verlängert …"))
                connect()
                old?.close(1000, "goAway")
            }
        }
    }

    private fun parseContent(content: JSONObject) {
        content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                val data = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                val b64 = data.optString("data", "")
                if (b64.isNotEmpty() && data.optString("mimeType", "audio").startsWith("audio")) {
                    onEvent(LiveEvent.Audio(Base64.decode(b64, Base64.DEFAULT)))
                }
            }
        }
        content.optJSONObject("outputTranscription")?.optString("text", "")?.takeIf { it.isNotEmpty() }?.let {
            onEvent(LiveEvent.ModelText(it))
        }
        content.optJSONObject("interimInputTranscription")?.optString("text", "")?.let {
            onEvent(LiveEvent.UserInterim(it))
        }
        content.optJSONObject("inputTranscription")?.optString("text", "")?.takeIf { it.isNotEmpty() }?.let {
            onEvent(LiveEvent.UserText(it))
        }
        if (content.optBoolean("interrupted", false)) onEvent(LiveEvent.Interrupted)
        if (content.optBoolean("turnComplete", false)) onEvent(LiveEvent.TurnComplete)
    }

    private inner class Listener : WebSocketListener() {
        @Volatile var gotSetup = false

        override fun onOpen(webSocket: WebSocket, response: Response) {
            val handle = if (supportsResumption) resumeHandle else null
            webSocket.send(setupFactory(handle, useMinimalSetup).toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket === socket) handle(this, text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (webSocket === socket) handle(this, bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            ended(webSocket, if (reason.isBlank()) "Code $code" else "$reason (Code $code)", network = false)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // No HTTP answer at all: no network, DNS failure or timeout – not a problem of the setup.
            ended(webSocket, t.message ?: t.javaClass.simpleName, network = response == null && t is java.io.IOException)
        }

        private fun ended(webSocket: WebSocket, why: String, network: Boolean) {
            if (webSocket !== socket || closedByUser) return
            ready = false
            when {
                gotSetup || network -> reconnect(why)
                !useMinimalSetup -> {
                    // The full setup was rejected: try once more with the smallest possible setup.
                    useMinimalSetup = true
                    onEvent(LiveEvent.Info("$name: einfacher Modus ($why)"))
                    connect()
                }
                else -> onEvent(LiveEvent.Failed("$name: $why"))
            }
        }
    }

    companion object {
        private const val MAX_RECONNECTS = 8

        /** One small background thread for the delayed reconnects of all live connections. */
        private val scheduler =
            Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "live-reconnect").apply { isDaemon = true } }
    }
}
