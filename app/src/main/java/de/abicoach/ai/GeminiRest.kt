package de.abicoach.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** An error from one of the AI providers, with the HTTP status (0 = network, -2 = blocked as recitation). */
open class AiException(message: String, val code: Int = 0, val provider: String = "") : Exception(message) {
    /** True when the request itself can never work (wrong key, no credit, unknown model): no retry, fall back. */
    val isPermanent: Boolean get() = code in setOf(400, 401, 402, 403, 404)

    /** True when waiting a little may help. */
    val isTransient: Boolean get() = code == 0 || code == 408 || code == 429 || code >= 500

    /** Google's servers are overloaded ("high demand") or had an internal error: another model may answer at once. */
    val isOverloaded: Boolean get() = code == 500 || code == 503 || code == 504 || code == 529

    /** No connection at all (no network, the phone's DNS failed, timeout). */
    val isNetwork: Boolean get() = code == 0
}

class GeminiException(message: String, code: Int = 0) : AiException(message, code, "Gemini")

/** Plain REST calls to the Gemini Developer API. */
class GeminiRest(private val http: OkHttpClient, private val apiKey: () -> String) {
    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val hasKey: Boolean get() = apiKey().isNotBlank()

    /** generateContent with structured output; returns the parsed JSON. */
    suspend fun generateJson(
        model: String,
        system: String,
        parts: List<JSONObject>,
        schema: Sch,
        retries: Int = 1,
        thinking: String? = null,
    ): JSONObject {
        val config = JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema.gemini())
        val text = generate(model, system, listOf(JSONObject().put("role", "user").put("parts", JSONArray(parts))), config, retries, thinking)
        return parseJsonLoose(text)
    }

    /** A chat turn: [turns] are (role, text) pairs with role "user" or "model". */
    suspend fun generateText(model: String, system: String, turns: List<Pair<String, String>>, retries: Int = 1, thinking: String? = null): String {
        val contents =
            turns.map { (role, text) ->
                JSONObject().put("role", if (role == "assistant") "model" else role).put("parts", JSONArray().put(textPart(text)))
            }
        return generate(model, system, contents, JSONObject(), retries, thinking)
    }

    /** Models that rejected a thinking setting: it is left out for them from now on. */
    private val noThinkingConfig = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * [thinking] is a thinking level ("low", "medium", "high") for jobs that must be quick; null leaves the model's own default.
     * If a model does not accept the setting, the request is repeated without it.
     */
    private suspend fun generate(model: String, system: String, contents: List<JSONObject>, config: JSONObject, retries: Int, thinking: String?): String {
        if (thinking != null && model !in noThinkingConfig) {
            val withThinking = JSONObject(config.toString()).put("thinkingConfig", JSONObject().put("thinkingLevel", thinking))
            try {
                return generateOnce(model, system, contents, withThinking, retries)
            } catch (e: GeminiException) {
                if (e.code != 400 || !(e.message ?: "").contains("think", ignoreCase = true)) throw e
                noThinkingConfig += model
            }
        }
        return generateOnce(model, system, contents, config, retries)
    }

    private suspend fun generateOnce(model: String, system: String, contents: List<JSONObject>, config: JSONObject, retries: Int): String =
        withRetry(retries) {
            withContext(Dispatchers.IO) {
                val body = JSONObject().put("contents", JSONArray(contents))
                if (system.isNotBlank()) {
                    body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(textPart(system))))
                }
                if (config.length() > 0) body.put("generationConfig", config)
                val request =
                    Request.Builder()
                        .url("$base/models/${model.removePrefix("models/")}:generateContent")
                        .header("x-goog-api-key", apiKey())
                        .post(body.toString().toRequestBody(jsonType))
                        .build()
                execute(request) { root ->
                    val candidates = root.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        val reason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                        throw GeminiException("Keine Antwort vom Modell $reason".trim(), if (reason.isNotEmpty()) -3 else 500)
                    }
                    val candidate = candidates.getJSONObject(0)
                    val finish = candidate.optString("finishReason", "")
                    val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                    val answer = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue
                            if (!part.optBoolean("thought", false)) answer.append(part.optString("text", ""))
                        }
                    }
                    if (answer.isBlank()) {
                        when (finish) {
                            "RECITATION" -> throw GeminiException("Google hat die Antwort als Zitat aus geschütztem Text blockiert", -2)
                            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST" -> throw GeminiException("Antwort von Google blockiert ($finish)", -3)
                            "MAX_TOKENS" -> throw GeminiException("Antwort zu lang abgeschnitten", 500)
                            else -> throw GeminiException("Leere Antwort vom Modell ($finish)", 500)
                        }
                    }
                    answer.toString()
                }
            }
        }

    /** Uploads a file with the resumable Files API and returns its URI (valid for 48 hours). */
    suspend fun uploadFile(file: File, mimeType: String, displayName: String): String =
        withContext(Dispatchers.IO) {
            val start =
                Request.Builder()
                    .url("https://generativelanguage.googleapis.com/upload/v1beta/files")
                    .header("x-goog-api-key", apiKey())
                    .header("X-Goog-Upload-Protocol", "resumable")
                    .header("X-Goog-Upload-Command", "start")
                    .header("X-Goog-Upload-Header-Content-Length", file.length().toString())
                    .header("X-Goog-Upload-Header-Content-Type", mimeType)
                    .post(JSONObject().put("file", JSONObject().put("display_name", displayName)).toString().toRequestBody(jsonType))
                    .build()
            val uploadUrl =
                try {
                    http.newCall(start).execute().use { r ->
                        val t = r.body?.string().orEmpty()
                        if (!r.isSuccessful) throw GeminiException(errorMessage(t, r.code), r.code)
                        r.header("x-goog-upload-url") ?: throw GeminiException("Kein Upload-Link erhalten")
                    }
                } catch (e: IOException) {
                    throw GeminiException("Netzwerkfehler: ${e.message}", 0)
                }
            val upload =
                Request.Builder()
                    .url(uploadUrl)
                    .header("X-Goog-Upload-Offset", "0")
                    .header("X-Goog-Upload-Command", "upload, finalize")
                    .post(file.asRequestBody(mimeType.toMediaType()))
                    .build()
            val fileInfo =
                try {
                    http.newCall(upload).execute().use { r ->
                        val t = r.body?.string().orEmpty()
                        if (!r.isSuccessful) throw GeminiException(errorMessage(t, r.code), r.code)
                        JSONObject(t).getJSONObject("file")
                    }
                } catch (e: IOException) {
                    throw GeminiException("Netzwerkfehler: ${e.message}", 0)
                }
            val name = fileInfo.getString("name")
            var uri = fileInfo.getString("uri")
            var state = fileInfo.optString("state", "ACTIVE")
            var tries = 0
            while (state == "PROCESSING" && tries < 60) {
                delay(1500)
                tries++
                val check = Request.Builder().url("$base/$name").header("x-goog-api-key", apiKey()).get().build()
                runCatching {
                    http.newCall(check).execute().use { r ->
                        val o = JSONObject(r.body?.string().orEmpty().ifEmpty { "{}" })
                        state = o.optString("state", "ACTIVE")
                        uri = o.optString("uri", uri)
                    }
                }
            }
            if (state == "FAILED") throw GeminiException("Google konnte die Datei nicht verarbeiten")
            uri
        }

    /**
     * Embeds texts with gemini-embedding-2 (task given as a text prefix) or gemini-embedding-001 (task type field).
     * Returns one unit-length vector per text.
     */
    suspend fun embed(model: String, texts: List<String>, query: Boolean, dims: Int = 768, titles: List<String>? = null): List<FloatArray> =
        withRetry(2) {
            withContext(Dispatchers.IO) {
                val clean = model.removePrefix("models/")
                val legacy = clean.contains("001") || clean.contains("text-embedding")
                val requests = JSONArray()
                texts.forEachIndexed { i, raw ->
                    val text =
                        when {
                            legacy -> raw
                            query -> "task: search result | query: $raw"
                            else -> "title: ${titles?.getOrNull(i)?.ifBlank { null } ?: "none"} | text: $raw"
                        }
                    val r =
                        JSONObject()
                            .put("model", "models/$clean")
                            .put("content", JSONObject().put("parts", JSONArray().put(textPart(text))))
                            .put("outputDimensionality", dims)
                    if (legacy) r.put("taskType", if (query) "RETRIEVAL_QUERY" else "RETRIEVAL_DOCUMENT")
                    requests.put(r)
                }
                val request =
                    Request.Builder()
                        .url("$base/models/$clean:batchEmbedContents")
                        .header("x-goog-api-key", apiKey())
                        .post(JSONObject().put("requests", requests).toString().toRequestBody(jsonType))
                        .build()
                execute(request) { root ->
                    val embeddings = root.optJSONArray("embeddings") ?: throw GeminiException("Keine Vektoren erhalten", 500)
                    (0 until embeddings.length()).map { i ->
                        val values = embeddings.getJSONObject(i).getJSONArray("values")
                        val v = FloatArray(values.length()) { values.getDouble(it).toFloat() }
                        normalize(v)
                    }
                }
            }
        }

    private fun ttsBody(text: String, voice: String): JSONObject {
        val config =
            JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put(
                    "speechConfig",
                    JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))),
                )
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(textPart(text)))))
            .put("generationConfig", config)
    }

    /** Models whose speech cannot be streamed: they answer in one piece. */
    private val noStreamTts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Text to speech, streamed: [onChunk] gets each piece of 16-bit PCM (mono, 24 kHz) as soon as Google sends it, so
     * playback starts before the whole sentence is made. Returns all of it. Falls back to one piece if streaming is
     * not offered for the model.
     */
    suspend fun ttsStream(model: String, text: String, voice: String, onChunk: (ByteArray) -> Unit): ByteArray {
        val clean = model.removePrefix("models/")
        if (clean !in noStreamTts) {
            var sent = false
            try {
                return ttsStreamOnce(clean, text, voice) { pcm ->
                    sent = true
                    onChunk(pcm)
                }
            } catch (e: GeminiException) {
                if (sent || (e.code != 400 && e.code != 404 && e.code != 405)) throw e
                noStreamTts += clean
            }
        }
        return tts(clean, text, voice).also(onChunk)
    }

    private suspend fun ttsStreamOnce(model: String, text: String, voice: String, onChunk: (ByteArray) -> Unit): ByteArray {
        val request =
            Request.Builder()
                .url("$base/models/$model:streamGenerateContent?alt=sse")
                .header("x-goog-api-key", apiKey())
                .post(ttsBody(text, voice).toString().toRequestBody(jsonType))
                .build()
        return try {
            http.streamCall(request) { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw GeminiException(errorMessage(body, response.code), response.code)
                }
                val source = response.body?.source() ?: throw GeminiException("Keine Audiodaten erhalten", 500)
                val aligner = SampleAligner(onChunk)
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val json = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
                    json.optJSONObject("error")?.let { err -> throw GeminiException(err.optString("message", "Fehler beim Sprechen"), err.optInt("code", 500)) }
                    val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    for (i in 0 until parts.length()) {
                        val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data").orEmpty()
                        if (data.isNotEmpty()) aligner.add(Base64.decode(data, Base64.DEFAULT))
                    }
                }
                aligner.result().also { if (it.isEmpty()) throw GeminiException("Keine Audiodaten erhalten", 500) }
            }
        } catch (e: IOException) {
            throw GeminiException("Netzwerkfehler: ${e.message ?: e.javaClass.simpleName}", 0)
        }
    }

    /** Text to speech in one piece; returns 16-bit PCM mono at 24 kHz. */
    suspend fun tts(model: String, text: String, voice: String): ByteArray =
        withRetry(1) {
            withContext(Dispatchers.IO) {
                val request =
                    Request.Builder()
                        .url("$base/models/${model.removePrefix("models/")}:generateContent")
                        .header("x-goog-api-key", apiKey())
                        .post(ttsBody(text, voice).toString().toRequestBody(jsonType))
                        .build()
                execute(request) { root ->
                    val parts =
                        root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                            ?: throw GeminiException("Keine Audiodaten erhalten", 500)
                    var pcm: ByteArray? = null
                    for (i in 0 until parts.length()) {
                        val data = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                        pcm = Base64.decode(data.optString("data"), Base64.DEFAULT)
                    }
                    pcm ?: throw GeminiException("Keine Audiodaten erhalten", 500)
                }
            }
        }

    // ---------- Work that runs in Google's cloud (Interactions API, background mode) ----------

    /** State of a background job: "in_progress", "completed", "failed", "cancelled" … and, when done, the answer text. */
    data class CloudState(val status: String, val text: String, val error: String) {
        /** Still working or waiting for its turn at Google. */
        val running: Boolean get() = status.isBlank() || status in WAITING

        val done: Boolean get() = status == "completed"

        /** Ended without an answer: failed, cancelled, expired or incomplete. */
        val ended: Boolean get() = !running && !done

        private companion object {
            val WAITING = setOf("in_progress", "queued", "pending", "created", "running", "scheduled")
        }
    }

    /** Models whose background jobs do not accept a separate system instruction: it goes into the input instead. */
    private val systemInInput = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Starts a model call that runs in the cloud and returns its id at once. It keeps running when the app is closed or
     * the phone is off; [cloudState] fetches the result later. [parts] use the generateContent format (text, fileData).
     */
    suspend fun startCloud(model: String, system: String, parts: List<JSONObject>, schema: Sch?, thinking: String? = null): String {
        val clean = model.removePrefix("models/")
        if (system.isNotBlank() && clean !in systemInInput) {
            try {
                return startCloudOnce(clean, system, parts, schema, thinking, separateSystem = true)
            } catch (e: GeminiException) {
                if (e.code != 400 || !(e.message ?: "").contains("system", ignoreCase = true)) throw e
                systemInInput += clean
            }
        }
        return startCloudOnce(clean, system, parts, schema, thinking, separateSystem = false)
    }

    private suspend fun startCloudOnce(model: String, system: String, parts: List<JSONObject>, schema: Sch?, thinking: String?, separateSystem: Boolean): String =
        withRetry(2) {
            withContext(Dispatchers.IO) {
                val input = JSONArray()
                if (!separateSystem && system.isNotBlank()) input.put(JSONObject().put("type", "text").put("text", "Anweisungen:\n$system"))
                parts.forEach { input.put(interactionPart(it)) }
                val body = JSONObject().put("model", model).put("input", input).put("background", true).put("store", true)
                if (separateSystem && system.isNotBlank()) body.put("system_instruction", system)
                if (schema != null) {
                    body.put("response_format", JSONObject().put("type", "text").put("mime_type", "application/json").put("schema", schema.jsonSchema()))
                }
                if (thinking != null) body.put("generation_config", JSONObject().put("thinking_level", thinking))
                val request =
                    Request.Builder()
                        .url("$base/interactions")
                        .header("x-goog-api-key", apiKey())
                        .header("Api-Revision", API_REVISION)
                        .post(body.toString().toRequestBody(jsonType))
                        .build()
                execute(request) { root -> root.optString("id").ifBlank { throw GeminiException("Google hat keine Auftragsnummer geschickt", 500) } }
            }
        }

    /** Where a background job stands; the text of the model's answer once it is completed. */
    suspend fun cloudState(id: String): CloudState =
        withRetry(2) {
            withContext(Dispatchers.IO) {
                val request =
                    Request.Builder()
                        .url("$base/interactions/$id")
                        .header("x-goog-api-key", apiKey())
                        .header("Api-Revision", API_REVISION)
                        .get()
                        .build()
                execute(request) { root -> parseInteraction(root) }
            }
        }

    /** Stops a background job (best effort: it may already be finished). */
    suspend fun cancelCloud(id: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request.Builder()
                        .url("$base/interactions/$id/cancel")
                        .header("x-goog-api-key", apiKey())
                        .header("Api-Revision", API_REVISION)
                        .post("{}".toRequestBody(jsonType))
                        .build()
                http.newCall(request).execute().use { }
            }
        }
    }

    private fun parseInteraction(root: JSONObject): CloudState {
        val text = StringBuilder()
        val steps = root.optJSONArray("steps")
        if (steps != null) {
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                if (step.optString("type") != "model_output") continue
                val content = step.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val c = content.optJSONObject(j) ?: continue
                    if (c.optString("type") == "text") text.append(c.optString("text"))
                }
            }
        }
        // The older answer format ("outputs") just in case.
        if (text.isEmpty()) {
            root.optJSONArray("outputs")?.let { outputs ->
                for (i in 0 until outputs.length()) {
                    val o = outputs.optJSONObject(i) ?: continue
                    if (o.optString("type") == "text") text.append(o.optString("text"))
                }
            }
        }
        val errors =
            root.optJSONArray("errors")?.let { e -> (0 until e.length()).mapNotNull { e.optJSONObject(it)?.optString("message")?.ifBlank { null } }.joinToString("; ") }.orEmpty()
        return CloudState(root.optString("status"), text.toString(), errors)
    }

    /** generateContent parts → Interactions API content (text, audio/image/video/document by URI or inline). */
    private fun interactionPart(p: JSONObject): JSONObject {
        fun kind(mime: String): String =
            when {
                mime.startsWith("audio/") -> "audio"
                mime.startsWith("image/") -> "image"
                mime.startsWith("video/") -> "video"
                else -> "document"
            }
        p.optJSONObject("fileData")?.let { f ->
            val mime = f.optString("mimeType")
            return JSONObject().put("type", kind(mime)).put("uri", f.optString("fileUri")).put("mime_type", mime)
        }
        p.optJSONObject("inlineData")?.let { d ->
            val mime = d.optString("mimeType")
            return JSONObject().put("type", kind(mime)).put("data", d.optString("data")).put("mime_type", mime)
        }
        return JSONObject().put("type", "text").put("text", p.optString("text"))
    }

    /** Returns null when the key works, otherwise a readable error. */
    suspend fun checkKey(): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("$base/models?pageSize=5").header("x-goog-api-key", apiKey()).get().build()
                http.newCall(request).execute().use { r ->
                    if (r.isSuccessful) null else errorMessage(r.body?.string().orEmpty(), r.code)
                }
            }.getOrElse { it.message ?: "Netzwerkfehler" }
        }

    private fun <T> execute(request: Request, parse: (JSONObject) -> T): T {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw GeminiException(errorMessage(text, response.code), response.code)
                return parse(JSONObject(text))
            }
        } catch (e: IOException) {
            throw GeminiException("Netzwerkfehler: ${e.message ?: e.javaClass.simpleName}", 0)
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val message = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
        val hint =
            when (code) {
                400 -> if (message?.contains("prepayment", true) == true || message?.contains("billing", true) == true) "Kein Guthaben im Google-Projekt" else "Ungültige Anfrage"
                401, 403 -> "Gemini-Schlüssel ungültig oder ohne Berechtigung"
                402 -> "Kein Guthaben im Google-Projekt"
                404 -> "Modell nicht gefunden"
                429 -> "Kontingent erschöpft – kurz warten"
                else -> "Fehler $code"
            }
        return if (message.isNullOrBlank()) hint else "$hint: $message"
    }

    companion object {
        /** The schema version of the Interactions API the app speaks ("steps" in the answer). */
        const val API_REVISION = "2026-05-20"

        /** Accepts JSON even if the model wrapped it in a code fence or added text around it. */
        fun parseJsonLoose(text: String): JSONObject {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw AiException("Antwort war kein JSON", 500)
            return try {
                JSONObject(text.substring(start, end + 1))
            } catch (e: Exception) {
                throw AiException("Antwort war kein gültiges JSON", 500)
            }
        }

        fun textPart(text: String): JSONObject = JSONObject().put("text", text)

        fun filePart(mimeType: String, uri: String): JSONObject = JSONObject().put("fileData", JSONObject().put("mimeType", mimeType).put("fileUri", uri))

        fun inlinePart(mimeType: String, bytes: ByteArray): JSONObject =
            JSONObject().put("inlineData", JSONObject().put("mimeType", mimeType).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))

        fun normalize(v: FloatArray): FloatArray {
            var sum = 0.0
            for (x in v) sum += x * x
            val n = kotlin.math.sqrt(sum).toFloat()
            if (n > 0f) for (i in v.indices) v[i] /= n
            return v
        }
    }
}

/**
 * Retries transient errors (network, 429, 5xx) with growing pauses. A lost connection is always tried at least
 * twice more (after 1.5 and 3 seconds): a weak network or a DNS hiccup usually passes within seconds.
 */
suspend fun <T> withRetry(retries: Int, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: AiException) {
            val allowed = if (e.code == 0) maxOf(retries, 2) else retries
            if (!e.isTransient || attempt >= allowed) throw e
            attempt++
            delay(
                when (e.code) {
                    0 -> 1_500L * attempt
                    429 -> 20_000L * attempt
                    else -> 2_500L * attempt
                },
            )
        }
    }
}
