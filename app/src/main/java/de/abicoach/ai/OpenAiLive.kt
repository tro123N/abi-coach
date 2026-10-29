package de.abicoach.ai

import android.util.Base64
import de.abicoach.audio.LinearResampler
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** Checks an OpenAI key by listing the models. */
object OpenAiKeyCheck {
    suspend fun check(http: OkHttpClient, key: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (key.isBlank()) return@withContext "Kein Schlüssel eingetragen"
            runCatching {
                val request = Request.Builder().url("https://api.openai.com/v1/models").header("Authorization", "Bearer $key").get().build()
                http.newCall(request).execute().use { r ->
                    when {
                        r.isSuccessful -> null
                        r.code == 401 -> "OpenAI-Schlüssel ungültig"
                        r.code == 429 -> "OpenAI: Kontingent oder Guthaben erschöpft"
                        else -> "OpenAI-Fehler ${r.code}: " + runCatching { JSONObject(r.body?.string().orEmpty()).getJSONObject("error").getString("message") }.getOrDefault("")
                    }
                }
            }.getOrElse { it.message ?: "Netzwerkfehler" }
        }
}

/**
 * OpenAI GPT-Live-1 over the Live API WebSocket (wss://api.openai.com/v1/live/sessions).
 * The model is full-duplex: it listens while it speaks and decides itself when to take the turn,
 * so it can interrupt a monologue like a real examiner. Audio is 16-bit PCM at 24 kHz in both directions;
 * the microphone's 16 kHz audio is upsampled before sending.
 */
class OpenAiLiveAgent(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val voice: String,
    private val instructions: String,
    private val onEvent: (LiveEvent) -> Unit,
) : VoiceAgent {
    override val label: String = "GPT-Live-1"
    override val outputRate: Int = 24_000

    private val url = "wss://api.openai.com/v1/live/sessions"
    private val ids = AtomicInteger(0)
    private val resampler = LinearResampler(16_000, 24_000)

    @Volatile private var socket: WebSocket? = null

    @Volatile private var ready = false

    @Volatile private var closedByUser = false

    @Volatile private var reconnects = 0

    @Volatile private var sendHistory = false

    /** Instructions that arrived before the session was ready. */
    private val pending = ArrayList<String>()

    // Transcript pieces are grouped into turns here, because the Live API has no turn-complete event.
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
        val request = Request.Builder().url(url).header("Authorization", "Bearer $apiKey").build()
        socket = http.newWebSocket(request, Listener())
        startWatchdog()
    }

    override fun sendAudio(pcm16k: ByteArray) {
        if (!ready) return
        val pcm24 = resampler.process(pcm16k)
        if (pcm24.isEmpty()) return
        val message = JSONObject().put("type", "session.input_audio.append").put("audio", Base64.encodeToString(pcm24, Base64.NO_WRAP))
        socket?.send(message.toString())
    }

    override fun sendInstruction(text: String) {
        if (!ready) {
            synchronized(pending) { pending += text }
            return
        }
        val message =
            JSONObject()
                .put("type", "session.instructions.append")
                .put("event_id", "app_${ids.incrementAndGet()}")
                .put("delegation_id", JSONObject.NULL)
                .put("content", text.take(1800))
        socket?.send(message.toString())
    }

    override fun sendStreamEnd() = Unit

    override fun close() {
        closedByUser = true
        val s = socket
        if (s != null && ready) {
            runCatching { s.send(JSONObject().put("type", "session.close").toString()) }
            // Give the server a moment to finalise (session.closed), then drop the connection.
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
            recent.reversed().takeWhile { (_, text) -> chars += text.length; chars < 24_000 }.reversed().forEach { (role, text) ->
                input.put(JSONObject().put("role", role).put("content", text))
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
            "session.usage.updated" -> {
                message.optJSONObject("usage")?.optDouble("seconds", Double.NaN)?.takeIf { !it.isNaN() }?.let { onEvent(LiveEvent.Usage(it)) }
            }
            "error" -> {
                val error = message.optJSONObject("error")
                val msg = error?.optString("message").orEmpty().ifBlank { "unbekannter Fehler" }
                val code = error?.optString("code").orEmpty()
                if (!ready) {
                    if (sendHistory) {
                        // The saved history may have been rejected: start fresh instead.
                        sendHistory = false
                        socket?.let { startSession(it) }
                    } else {
                        onEvent(LiveEvent.Failed("GPT-Live: $msg"))
                    }
                } else {
                    onEvent(LiveEvent.Info("GPT-Live: $msg${if (code.isNotBlank()) " ($code)" else ""}"))
                }
            }
            "session.closed" -> {
                val reason = message.optString("reason")
                message.optJSONObject("usage")?.optDouble("seconds", Double.NaN)?.takeIf { !it.isNaN() }?.let { onEvent(LiveEvent.Usage(it)) }
                ready = false
                if (!closedByUser) {
                    when (reason) {
                        "content" -> onEvent(LiveEvent.Failed("GPT-Live hat die Sitzung beendet (Inhaltsregeln)."))
                        else -> reconnect(reason.ifBlank { "geschlossen" })
                    }
                }
            }
        }
    }

    private fun modelOutput() {
        lastModelOutputAt = System.currentTimeMillis()
        if (!modelSpeaking) {
            modelSpeaking = true
            // The student's words before the examiner starts speaking form one turn.
            synchronized(lock) {
                val user = userTurn.toString().trim()
                userTurn.setLength(0)
                if (user.isNotEmpty()) history += "user" to user
            }
        }
    }

    /** Ends the examiner's turn after a short silence (the API has no explicit event for it). */
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
                } catch (_: InterruptedException) {
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
            onEvent(LiveEvent.Failed("GPT-Live: Verbindung verloren ($reason)"))
            return
        }
        onEvent(LiveEvent.Info("GPT-Live: verbinde neu …"))
        sendHistory = synchronized(lock) { history.isNotEmpty() }
        val request = Request.Builder().url(url).header("Authorization", "Bearer $apiKey").build()
        socket = http.newWebSocket(request, Listener())
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            startSession(webSocket)
        }

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
            if (wasReady) reconnect("Code $code") else onEvent(LiveEvent.Failed("GPT-Live: ${reason.ifBlank { "Code $code" }}"))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket || closedByUser) return
            val wasReady = ready
            ready = false
            val why =
                when (response?.code) {
                    401 -> "OpenAI-Schlüssel ungültig"
                    403 -> "OpenAI-Schlüssel ohne Zugriff auf GPT-Live-1"
                    404 -> "GPT-Live-1 nicht gefunden"
                    429 -> "OpenAI: Kontingent oder Guthaben erschöpft"
                    else -> t.message ?: t.javaClass.simpleName
                }
            if (wasReady) reconnect(why) else onEvent(LiveEvent.Failed("GPT-Live: $why"))
        }
    }
}
