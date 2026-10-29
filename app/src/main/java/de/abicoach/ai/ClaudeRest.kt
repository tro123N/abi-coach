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

class ClaudeException(message: String, code: Int = 0) : AiException(message, code, "Claude")

/**
 * The Claude Messages API: structured JSON answers (output_config.format) and plain chat answers,
 * optionally with adaptive thinking for the hard tasks (grading, writing exam material).
 */
class ClaudeRest(private val http: OkHttpClient, private val apiKey: () -> String) {
    private val url = "https://api.anthropic.com/v1/messages"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val hasKey: Boolean get() = apiKey().isNotBlank()

    /** Remembers per model whether adaptive thinking was rejected, so the next call does not try again. */
    private val noThinking = mutableSetOf<String>()

    suspend fun json(
        model: String,
        system: String,
        user: String,
        schema: Sch,
        maxTokens: Int = 16_000,
        thinking: Boolean = false,
        retries: Int = 1,
    ): JSONObject {
        val format = JSONObject().put("type", "json_schema").put("schema", schema.jsonSchema())
        val text = call(model, system, listOf("user" to user), maxTokens, thinking, format, retries)
        return GeminiRest.parseJsonLoose(text)
    }

    /** A chat answer; [turns] are (role, text) pairs with role "user" or "assistant". */
    suspend fun text(
        model: String,
        system: String,
        turns: List<Pair<String, String>>,
        maxTokens: Int = 8_000,
        thinking: Boolean = false,
        retries: Int = 1,
    ): String = call(model, system, turns, maxTokens, thinking, null, retries)

    private suspend fun call(
        model: String,
        system: String,
        turns: List<Pair<String, String>>,
        maxTokens: Int,
        thinking: Boolean,
        format: JSONObject?,
        retries: Int,
    ): String =
        withRetry(retries) {
            try {
                request(model, system, turns, maxTokens, thinking && model !in noThinking, format)
            } catch (e: ClaudeException) {
                // Older or smaller models may not support adaptive thinking: try once without it.
                if (thinking && e.code == 400 && e.message?.contains("thinking", ignoreCase = true) == true) {
                    noThinking += model
                    request(model, system, turns, maxTokens, false, format)
                } else {
                    throw e
                }
            }
        }

    private suspend fun request(
        model: String,
        system: String,
        turns: List<Pair<String, String>>,
        maxTokens: Int,
        thinking: Boolean,
        format: JSONObject?,
    ): String =
        withContext(Dispatchers.IO) {
            val messages = JSONArray()
            turns.forEach { (role, text) -> messages.put(JSONObject().put("role", if (role == "model") "assistant" else role).put("content", text)) }
            val body =
                JSONObject()
                    .put("model", model)
                    .put("max_tokens", maxTokens)
                    .put("messages", messages)
            if (system.isNotBlank()) body.put("system", system)
            if (format != null) body.put("output_config", JSONObject().put("format", format))
            if (thinking) body.put("thinking", JSONObject().put("type", "adaptive"))
            val request =
                Request.Builder()
                    .url(url)
                    .header("x-api-key", apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .post(body.toString().toRequestBody(jsonType))
                    .build()
            try {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw ClaudeException(errorMessage(text, response.code), response.code)
                    val root = JSONObject(text)
                    val stop = root.optString("stop_reason")
                    val content = root.optJSONArray("content") ?: JSONArray()
                    val answer = StringBuilder()
                    for (i in 0 until content.length()) {
                        val block = content.optJSONObject(i) ?: continue
                        if (block.optString("type") == "text") answer.append(block.optString("text"))
                    }
                    when {
                        stop == "refusal" -> throw ClaudeException("Claude hat die Anfrage abgelehnt", -3)
                        answer.isBlank() && stop == "max_tokens" -> throw ClaudeException("Antwort zu lang abgeschnitten", 500)
                        answer.isBlank() -> throw ClaudeException("Leere Antwort von Claude ($stop)", 500)
                    }
                    answer.toString()
                }
            } catch (e: IOException) {
                throw ClaudeException("Netzwerkfehler: ${e.message ?: e.javaClass.simpleName}", 0)
            }
        }

    /** Returns null when the key works, otherwise a readable error. */
    suspend fun checkKey(): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request.Builder()
                        .url("https://api.anthropic.com/v1/models?limit=5")
                        .header("x-api-key", apiKey())
                        .header("anthropic-version", "2023-06-01")
                        .get()
                        .build()
                http.newCall(request).execute().use { r -> if (r.isSuccessful) null else errorMessage(r.body?.string().orEmpty(), r.code) }
            }.getOrElse { it.message ?: "Netzwerkfehler" }
        }

    private fun errorMessage(body: String, code: Int): String {
        val message = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
        val hint =
            when (code) {
                400 -> if (message?.contains("credit", true) == true) "Kein Guthaben bei Anthropic" else "Ungültige Anfrage"
                401 -> "Claude-Schlüssel ungültig"
                403 -> "Claude-Schlüssel ohne Berechtigung"
                404 -> "Claude-Modell nicht gefunden"
                413 -> "Anfrage zu groß"
                429 -> "Claude: zu viele Anfragen – kurz warten"
                529 -> "Claude ist gerade überlastet"
                else -> "Claude-Fehler $code"
            }
        return if (message.isNullOrBlank()) hint else "$hint: $message"
    }
}
