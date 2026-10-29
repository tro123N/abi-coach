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
    ): JSONObject {
        val config = JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema.gemini())
        val text = generate(model, system, listOf(JSONObject().put("role", "user").put("parts", JSONArray(parts))), config, retries)
        return parseJsonLoose(text)
    }

    /** A chat turn: [turns] are (role, text) pairs with role "user" or "model". */
    suspend fun generateText(model: String, system: String, turns: List<Pair<String, String>>, retries: Int = 1): String {
        val contents =
            turns.map { (role, text) ->
                JSONObject().put("role", if (role == "assistant") "model" else role).put("parts", JSONArray().put(textPart(text)))
            }
        return generate(model, system, contents, JSONObject(), retries)
    }

    private suspend fun generate(model: String, system: String, contents: List<JSONObject>, config: JSONObject, retries: Int): String =
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

    /** Text to speech; returns 16-bit PCM mono at 24 kHz. */
    suspend fun tts(model: String, text: String, voice: String): ByteArray =
        withRetry(1) {
            withContext(Dispatchers.IO) {
                val config =
                    JSONObject()
                        .put("responseModalities", JSONArray().put("AUDIO"))
                        .put(
                            "speechConfig",
                            JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))),
                        )
                val body =
                    JSONObject()
                        .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(textPart(text)))))
                        .put("generationConfig", config)
                val request =
                    Request.Builder()
                        .url("$base/models/${model.removePrefix("models/")}:generateContent")
                        .header("x-goog-api-key", apiKey())
                        .post(body.toString().toRequestBody(jsonType))
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

/** Retries transient errors (network, 429, 5xx) with growing pauses. */
suspend fun <T> withRetry(retries: Int, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: AiException) {
            if (!e.isTransient || attempt >= retries) throw e
            attempt++
            delay(if (e.code == 429) 20_000L * attempt else 2_500L * attempt)
        }
    }
}
