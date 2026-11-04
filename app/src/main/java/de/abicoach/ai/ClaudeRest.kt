package de.abicoach.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ClaudeException(message: String, code: Int = 0) : AiException(message, code, "Anthropic")

/**
 * Claude over the Messages API: text and structured JSON with adaptive thinking, and pictures (pages of the course
 * material). Claude cannot hear recordings – the hub turns them into text with the phone's speech recognition first.
 */
class ClaudeRest(private val http: OkHttpClient, private val apiKey: () -> String) {
    private val base = "https://api.anthropic.com/v1"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Models that rejected thinking or the effort setting: left out for them from now on. */
    private val plain = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Models that rejected structured outputs: asked for JSON in plain words instead. */
    private val noFormat = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * A structured answer; [effort] is "low", "medium" or "high" (how much Claude thinks). [think] false answers at
     * once without thinking – for the quick checks while the student speaks.
     */
    suspend fun json(
        model: String,
        system: String,
        parts: List<JSONObject>,
        schema: Sch,
        retries: Int = 1,
        effort: String? = null,
        maxTokens: Int = 16_000,
        think: Boolean = true,
    ): JSONObject {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content(parts)))
        if (model !in noFormat) {
            try {
                return GeminiRest.parseJsonLoose(send(model, system, messages, schema, retries, effort, maxTokens, think))
            } catch (e: ClaudeException) {
                if (e.code != 400 || !(e.message ?: "").contains("output_config", ignoreCase = true) && !(e.message ?: "").contains("format", ignoreCase = true)) throw e
                noFormat += model
            }
        }
        val asked = system + "\n\nAnswer only with JSON in exactly this form:\n" + schema.outline()
        return GeminiRest.parseJsonLoose(send(model, asked, messages, null, retries, effort, maxTokens, think))
    }

    /** A chat turn: [turns] are (role, text) pairs with role "user" or "assistant". */
    suspend fun text(
        model: String,
        system: String,
        turns: List<Pair<String, String>>,
        retries: Int = 1,
        effort: String? = null,
        maxTokens: Int = 8_000,
        think: Boolean = true,
    ): String {
        val messages = JSONArray()
        merged(turns).forEach { (role, text) -> messages.put(JSONObject().put("role", role).put("content", text)) }
        return send(model, system, messages, null, retries, effort, maxTokens, think)
    }

    /** Claude needs the conversation to start with the user and to alternate: neighbouring turns of one side are joined. */
    private fun merged(turns: List<Pair<String, String>>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        turns.forEach { (r, t) ->
            val role = if (r == "user") "user" else "assistant"
            if (t.isBlank()) return@forEach
            if (out.isNotEmpty() && out.last().first == role) out[out.lastIndex] = role to (out.last().second + "\n\n" + t) else out += role to t
        }
        if (out.isEmpty() || out.first().first != "user") out.add(0, "user" to "(Beginn)")
        return out
    }

    private suspend fun send(model: String, system: String, messages: JSONArray, schema: Sch?, retries: Int, effort: String?, maxTokens: Int, think: Boolean): String {
        if (think && model !in plain) {
            try {
                return sendOnce(model, system, messages, schema, retries, effort, maxTokens, thinking = true)
            } catch (e: ClaudeException) {
                val m = e.message.orEmpty()
                if (e.code != 400 || !(m.contains("thinking", true) || m.contains("effort", true) || m.contains("adaptive", true))) throw e
                plain += model
            }
        }
        return sendOnce(model, system, messages, schema, retries, null, maxTokens, thinking = false)
    }

    private suspend fun sendOnce(
        model: String,
        system: String,
        messages: JSONArray,
        schema: Sch?,
        retries: Int,
        effort: String?,
        maxTokens: Int,
        thinking: Boolean,
    ): String =
        withRetry(retries) {
            withContext(Dispatchers.IO) {
                val body = JSONObject().put("model", model).put("max_tokens", maxTokens).put("messages", messages)
                if (system.isNotBlank()) body.put("system", system)
                if (thinking) body.put("thinking", JSONObject().put("type", "adaptive"))
                val output = JSONObject()
                if (schema != null) output.put("format", JSONObject().put("type", "json_schema").put("schema", schema.strictSchema()))
                if (thinking && effort != null) output.put("effort", effort)
                if (output.length() > 0) body.put("output_config", output)
                val request =
                    Request.Builder()
                        .url("$base/messages")
                        .header("x-api-key", apiKey())
                        .header("anthropic-version", VERSION)
                        .post(body.toString().toRequestBody(jsonType))
                        .build()
                execute(request) { root ->
                    val text = StringBuilder()
                    val content = root.optJSONArray("content") ?: JSONArray()
                    for (i in 0 until content.length()) {
                        val c = content.optJSONObject(i) ?: continue
                        if (c.optString("type") == "text") text.append(c.optString("text"))
                    }
                    if (text.isBlank()) {
                        when (root.optString("stop_reason")) {
                            "refusal" -> throw ClaudeException("Claude hat die Antwort verweigert", -3)
                            "max_tokens" -> throw ClaudeException("Antwort zu lang abgeschnitten", 500)
                            else -> throw ClaudeException("Leere Antwort von Claude", 500)
                        }
                    }
                    text.toString()
                }
            }
        }

    /** Gemini-style parts → Claude content blocks: text, pictures, PDFs. Recordings were turned into text before. */
    private fun content(parts: List<JSONObject>): JSONArray {
        val out = JSONArray()
        parts.forEach { p ->
            val text = Parts.text(p)
            if (text != null) {
                if (text.isNotBlank()) out.put(JSONObject().put("type", "text").put("text", text))
                return@forEach
            }
            val (mime, data) = Parts.inline(p) ?: return@forEach
            when {
                mime.startsWith("image/") ->
                    out.put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", mime).put("data", data)))
                mime == "application/pdf" ->
                    out.put(JSONObject().put("type", "document").put("source", JSONObject().put("type", "base64").put("media_type", mime).put("data", data)))
                else -> Unit
            }
        }
        if (out.length() == 0) out.put(JSONObject().put("type", "text").put("text", "(leer)"))
        return out
    }

    /** Returns null when the key works, otherwise a readable error. */
    suspend fun checkKey(): String? =
        withContext(Dispatchers.IO) {
            if (apiKey().isBlank()) return@withContext "Es ist kein Anthropic-Schlüssel eingetragen."
            runCatching {
                val request = Request.Builder().url("$base/models?limit=5").header("x-api-key", apiKey()).header("anthropic-version", VERSION).get().build()
                http.newCall(request).execute().use { r -> if (r.isSuccessful) null else errorMessage(r.body?.string().orEmpty(), r.code) }
            }.getOrElse { it.message ?: "Netzwerkfehler" }
        }

    private fun <T> execute(request: Request, parse: (JSONObject) -> T): T {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // "Overloaded" comes as 529: another model may answer, like a 503 at Google.
                    throw ClaudeException(errorMessage(text, response.code), response.code)
                }
                return parse(JSONObject(text))
            }
        } catch (e: IOException) {
            throw ClaudeException("Netzwerkfehler: ${e.message ?: e.javaClass.simpleName}", 0)
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val message = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
        val hint =
            when (code) {
                400 -> if (message?.contains("credit", true) == true) "Anthropic: kein Guthaben" else "Ungültige Anfrage an Claude"
                401 -> "Anthropic-Schlüssel ungültig"
                403 -> "Anthropic: kein Zugriff"
                404 -> "Claude-Modell nicht gefunden"
                429 -> "Anthropic: Limit erreicht – kurz warten"
                529 -> "Claude ist gerade überlastet"
                else -> "Anthropic-Fehler $code"
            }
        return if (message.isNullOrBlank()) hint else "$hint: $message"
    }

    companion object {
        const val VERSION = "2023-06-01"
    }
}
