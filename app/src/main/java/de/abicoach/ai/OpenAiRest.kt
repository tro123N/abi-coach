package de.abicoach.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OpenAiException(message: String, code: Int = 0) : AiException(message, code, "OpenAI")

/**
 * OpenAI over plain HTTP: the Responses API for text and JSON (grading, material, coach, reading pages), the audio
 * model for listening to recordings, speech, transcription of files and search vectors.
 */
class OpenAiRest(private val http: OkHttpClient, private val apiKey: () -> String) {
    private val base = "https://api.openai.com/v1"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Models that rejected the reasoning setting: it is left out for them from now on. */
    private val noReasoning = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Audio models that rejected a JSON schema: they are asked for JSON in plain words instead. */
    private val noSchema = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // ---------- text and JSON (Responses API) ----------

    /** A structured answer; [effort] is the reasoning effort ("low", "medium", "high") or null for the model's default. */
    suspend fun json(model: String, system: String, parts: List<JSONObject>, schema: Sch, retries: Int = 1, effort: String? = null): JSONObject {
        val format =
            JSONObject().put("format", JSONObject().put("type", "json_schema").put("name", "answer").put("schema", schema.strictSchema()).put("strict", true))
        val input = JSONArray().put(JSONObject().put("role", "user").put("content", inputContent(parts)))
        return GeminiRest.parseJsonLoose(respond(model, system, input, format, retries, effort))
    }

    /** A chat turn: [turns] are (role, text) pairs with role "user" or "assistant". */
    suspend fun text(model: String, system: String, turns: List<Pair<String, String>>, retries: Int = 1, effort: String? = null): String {
        val input = JSONArray()
        turns.forEach { (role, text) -> input.put(JSONObject().put("role", if (role == "user") "user" else "assistant").put("content", text)) }
        return respond(model, system, input, null, retries, effort)
    }

    /** Models that do not know the effort "none": they get "low" instead. */
    private val noNone = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private suspend fun respond(model: String, system: String, input: JSONArray, text: JSONObject?, retries: Int, effort: String?): String {
        var level = if (effort == "none" && model in noNone) "low" else effort
        while (level != null && model !in noReasoning) {
            try {
                return respondOnce(model, system, input, text, retries, level)
            } catch (e: OpenAiException) {
                val m = e.message.orEmpty()
                if (e.code != 400 || !(m.contains("reasoning", ignoreCase = true) || m.contains("effort", ignoreCase = true))) throw e
                if (level == "none") {
                    noNone += model
                    level = "low"
                } else {
                    noReasoning += model
                }
            }
        }
        return respondOnce(model, system, input, text, retries, null)
    }

    private suspend fun respondOnce(model: String, system: String, input: JSONArray, text: JSONObject?, retries: Int, effort: String?): String =
        withRetry(retries) {
            withContext(Dispatchers.IO) {
                val body = JSONObject().put("model", model).put("input", input).put("store", false)
                if (system.isNotBlank()) body.put("instructions", system)
                if (text != null) body.put("text", text)
                if (effort != null) body.put("reasoning", JSONObject().put("effort", effort))
                val request = authorized("$base/responses").post(body.toString().toRequestBody(jsonType)).build()
                execute(request) { root -> outputText(root) }
            }
        }

    /** The answer text of a Responses API result (only the final message, not the reasoning). */
    private fun outputText(root: JSONObject): String {
        val answer = StringBuilder()
        var refusal = ""
        val output = root.optJSONArray("output") ?: JSONArray()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val c = content.optJSONObject(j) ?: continue
                when (c.optString("type")) {
                    "output_text" -> answer.append(c.optString("text"))
                    "refusal" -> refusal = c.optString("refusal")
                }
            }
        }
        if (answer.isNotBlank()) return answer.toString()
        if (refusal.isNotBlank()) throw OpenAiException("OpenAI hat die Antwort verweigert: $refusal", -3)
        val reason = root.optJSONObject("incomplete_details")?.optString("reason").orEmpty()
        if (reason.isNotBlank()) throw OpenAiException("Antwort unvollständig ($reason)", 500)
        throw OpenAiException("Leere Antwort von OpenAI (${root.optString("status")})", 500)
    }

    /** Gemini-style parts → Responses API content (text and pictures; PDFs as files). */
    private fun inputContent(parts: List<JSONObject>): JSONArray {
        val content = JSONArray()
        parts.forEach { p ->
            val text = Parts.text(p)
            if (text != null) {
                content.put(JSONObject().put("type", "input_text").put("text", text))
                return@forEach
            }
            val (mime, data) = Parts.inline(p) ?: return@forEach
            when {
                mime.startsWith("image/") -> content.put(JSONObject().put("type", "input_image").put("image_url", "data:$mime;base64,$data"))
                mime == "application/pdf" -> content.put(JSONObject().put("type", "input_file").put("filename", "material.pdf").put("file_data", "data:application/pdf;base64,$data"))
                else -> Unit
            }
        }
        if (content.length() == 0) content.put(JSONObject().put("type", "input_text").put("text", "(leer)"))
        return content
    }

    // ---------- listening (audio model, Chat Completions) ----------

    /** A structured answer from the audio model, which hears the recordings in [parts] itself. */
    suspend fun audioJson(model: String, system: String, parts: List<JSONObject>, schema: Sch, retries: Int = 1): JSONObject {
        if (model !in noSchema) {
            try {
                return GeminiRest.parseJsonLoose(audioOnce(model, system, parts, schema, withSchema = true, retries = retries))
            } catch (e: OpenAiException) {
                if (e.code != 400 || !(e.message ?: "").contains("response_format", ignoreCase = true)) throw e
                noSchema += model
            }
        }
        return GeminiRest.parseJsonLoose(audioOnce(model, system, parts, schema, withSchema = false, retries = retries))
    }

    private suspend fun audioOnce(model: String, system: String, parts: List<JSONObject>, schema: Sch, withSchema: Boolean, retries: Int): String =
        withRetry(retries) {
            withContext(Dispatchers.IO) {
                val content = JSONArray()
                parts.forEach { p ->
                    val text = Parts.text(p)
                    if (text != null) {
                        content.put(JSONObject().put("type", "text").put("text", text))
                        return@forEach
                    }
                    val (mime, data) = Parts.inline(p) ?: return@forEach
                    when {
                        mime.startsWith("audio/") ->
                            content.put(
                                JSONObject().put("type", "input_audio").put("input_audio", JSONObject().put("data", data).put("format", if (mime.contains("mp")) "mp3" else "wav")),
                            )
                        mime.startsWith("image/") -> content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mime;base64,$data")))
                        else -> Unit
                    }
                }
                val instructions = if (withSchema) system else system + "\n\nAnswer only with JSON in exactly this form:\n" + schema.outline()
                val messages = JSONArray()
                if (instructions.isNotBlank()) messages.put(JSONObject().put("role", "system").put("content", instructions))
                messages.put(JSONObject().put("role", "user").put("content", content))
                val body = JSONObject().put("model", model).put("messages", messages).put("modalities", JSONArray().put("text"))
                if (withSchema) {
                    body.put(
                        "response_format",
                        JSONObject().put("type", "json_schema").put("json_schema", JSONObject().put("name", "answer").put("schema", schema.strictSchema()).put("strict", true)),
                    )
                }
                val request = authorized("$base/chat/completions").post(body.toString().toRequestBody(jsonType)).build()
                execute(request) { root ->
                    val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    message?.optString("content")?.takeIf { it.isNotBlank() } ?: throw OpenAiException("Leere Antwort vom Audiomodell", 500)
                }
            }
        }

    /** Writes down a recording (WAV) word for word. */
    suspend fun transcribe(model: String, wav: ByteArray, prompt: String = ""): String =
        withRetry(1) {
            withContext(Dispatchers.IO) {
                val form =
                    MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("model", model)
                        .addFormDataPart("language", "en")
                        .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
                if (prompt.isNotBlank()) form.addFormDataPart("prompt", prompt.take(800))
                val request = authorized("$base/audio/transcriptions").post(form.build()).build()
                execute(request) { root -> root.optString("text").trim() }
            }
        }

    // ---------- speech ----------

    /**
     * Text to speech as 16-bit PCM mono at 24 kHz. The audio is streamed: [onChunk] gets each piece as soon as it
     * arrives, so playback can start before the whole sentence is made.
     */
    suspend fun tts(model: String, voice: String, text: String, style: String, onChunk: ((ByteArray) -> Unit)?): ByteArray {
        val body = JSONObject().put("model", model).put("voice", voice).put("input", text.take(4000)).put("response_format", "pcm")
        if (style.isNotBlank() && !model.startsWith("tts-1")) body.put("instructions", style)
        val request = authorized("$base/audio/speech").post(body.toString().toRequestBody(jsonType)).build()
        return try {
            http.streamCall(request) { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    throw OpenAiException(errorMessage(text, response.code), mapCode(response, text))
                }
                val input = response.body?.byteStream() ?: throw OpenAiException("Keine Audiodaten erhalten", 500)
                SampleAligner { onChunk?.invoke(it) }.apply { pump(input) }.result()
            }
        } catch (e: IOException) {
            throw OpenAiException("Netzwerkfehler: ${e.message ?: e.javaClass.simpleName}", 0)
        }
    }

    // ---------- search vectors ----------

    suspend fun embed(model: String, texts: List<String>, dims: Int = 768): List<FloatArray> =
        withRetry(2) {
            withContext(Dispatchers.IO) {
                val body = JSONObject().put("model", model).put("input", JSONArray(texts.map { it.take(8000) }))
                if (!model.contains("ada")) body.put("dimensions", dims)
                val request = authorized("$base/embeddings").post(body.toString().toRequestBody(jsonType)).build()
                execute(request) { root ->
                    val data = root.optJSONArray("data") ?: throw OpenAiException("Keine Vektoren erhalten", 500)
                    (0 until data.length()).map { i ->
                        val values = data.getJSONObject(i).getJSONArray("embedding")
                        GeminiRest.normalize(FloatArray(values.length()) { values.getDouble(it).toFloat() })
                    }
                }
            }
        }

    // ---------- key ----------

    /** Returns null when the key works, otherwise a readable error. */
    suspend fun checkKey(): String? =
        withContext(Dispatchers.IO) {
            if (apiKey().isBlank()) return@withContext "Es ist kein OpenAI-Schlüssel eingetragen."
            runCatching {
                http.newCall(authorized("$base/models").get().build()).execute().use { r ->
                    if (r.isSuccessful) null else errorMessage(r.body?.string().orEmpty(), r.code)
                }
            }.getOrElse { it.message ?: "Netzwerkfehler" }
        }

    private fun authorized(url: String): Request.Builder = Request.Builder().url(url).header("Authorization", "Bearer ${apiKey()}")

    private fun <T> execute(request: Request, parse: (JSONObject) -> T): T {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw OpenAiException(errorMessage(text, response.code), mapCode(response, text))
                return parse(JSONObject(text))
            }
        } catch (e: IOException) {
            throw OpenAiException("Netzwerkfehler: ${e.message ?: e.javaClass.simpleName}", 0)
        }
    }

    /** "No credit" comes as 429 from OpenAI, but waiting does not help: it counts as a permanent error (402). */
    private fun mapCode(response: okhttp3.Response, body: String = ""): Int =
        if (response.code == 429 && body.contains("insufficient_quota")) 402 else response.code

    private fun errorMessage(body: String, code: Int): String {
        val message = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
        val hint =
            when (code) {
                400 -> "Ungültige Anfrage an OpenAI"
                401 -> "OpenAI-Schlüssel ungültig"
                403 -> "OpenAI: kein Zugriff auf dieses Modell"
                404 -> "OpenAI-Modell nicht gefunden"
                429 -> if (body.contains("insufficient_quota")) "OpenAI: kein Guthaben" else "OpenAI: Limit erreicht – kurz warten"
                else -> "OpenAI-Fehler $code"
            }
        return if (message.isNullOrBlank()) hint else "$hint: $message"
    }

    companion object {
        /** How the read-aloud voice should sound. */
        const val VOICE_STYLE = "A friendly, clear British English teacher. Speak naturally at a calm, normal pace."

        fun wavOf(pcm: ByteArray, rate: Int = 16_000): String = Base64.encodeToString(de.abicoach.audio.Wav.of(pcm, rate), Base64.NO_WRAP)
    }
}
