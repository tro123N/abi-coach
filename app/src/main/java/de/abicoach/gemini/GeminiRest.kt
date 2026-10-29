package de.abicoach.gemini

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

class GeminiException(message: String, val code: Int = 0) : Exception(message)

/** Plain REST calls to the Gemini Developer API: JSON generation, file upload and a key check. */
class GeminiRest(private val http: OkHttpClient, private val apiKey: () -> String) {
    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Calls generateContent and returns the model's answer parsed as JSON (structured output). */
    suspend fun generateJson(
        model: String,
        system: String,
        parts: List<JSONObject>,
        schema: JSONObject,
        temperature: Double = 0.4,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray(parts))))
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("responseMimeType", "application/json")
                            .put("responseSchema", schema)
                            .put("temperature", temperature),
                    )
            val request =
                Request.Builder()
                    .url("$base/models/${model.removePrefix("models/")}:generateContent")
                    .header("x-goog-api-key", apiKey())
                    .post(body.toString().toRequestBody(jsonType))
                    .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw GeminiException(errorMessage(text, response.code), response.code)
                val root = JSONObject(text)
                val candidates = root.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    val reason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                    throw GeminiException("Keine Antwort vom Modell $reason".trim())
                }
                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    ?: throw GeminiException("Leere Antwort vom Modell")
                val answer = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (!part.optBoolean("thought", false)) answer.append(part.optString("text", ""))
                }
                parseJsonLoose(answer.toString())
            }
        }

    /** Uploads a file with the resumable Files API and returns its URI for use in a prompt. */
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
                http.newCall(start).execute().use { r ->
                    val t = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw GeminiException(errorMessage(t, r.code), r.code)
                    r.header("x-goog-upload-url") ?: throw GeminiException("Kein Upload-Link erhalten")
                }
            val upload =
                Request.Builder()
                    .url(uploadUrl)
                    .header("X-Goog-Upload-Offset", "0")
                    .header("X-Goog-Upload-Command", "upload, finalize")
                    .post(file.asRequestBody(mimeType.toMediaType()))
                    .build()
            val fileInfo =
                http.newCall(upload).execute().use { r ->
                    val t = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw GeminiException(errorMessage(t, r.code), r.code)
                    JSONObject(t).getJSONObject("file")
                }
            val name = fileInfo.getString("name")
            var uri = fileInfo.getString("uri")
            var state = fileInfo.optString("state", "ACTIVE")
            var tries = 0
            while (state == "PROCESSING" && tries < 40) {
                delay(1500)
                tries++
                val check = Request.Builder().url("$base/$name").header("x-goog-api-key", apiKey()).get().build()
                http.newCall(check).execute().use { r ->
                    val o = JSONObject(r.body?.string().orEmpty().ifEmpty { "{}" })
                    state = o.optString("state", "ACTIVE")
                    uri = o.optString("uri", uri)
                }
            }
            if (state == "FAILED") throw GeminiException("Google konnte die Audiodatei nicht verarbeiten")
            uri
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

    private fun errorMessage(body: String, code: Int): String {
        val message = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
        val hint =
            when (code) {
                400 -> "Ungültige Anfrage"
                401, 403 -> "API-Schlüssel ungültig oder ohne Berechtigung"
                404 -> "Modell nicht gefunden – Modellname in den Einstellungen prüfen"
                429 -> "Kontingent erschöpft – kurz warten (Gratis-Tarif)"
                else -> "Fehler $code"
            }
        return if (message.isNullOrBlank()) hint else "$hint: $message"
    }

    companion object {
        /** Accepts JSON even if the model wrapped it in a code fence. */
        fun parseJsonLoose(text: String): JSONObject {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw GeminiException("Antwort war kein JSON")
            return JSONObject(text.substring(start, end + 1))
        }

        fun textPart(text: String): JSONObject = JSONObject().put("text", text)

        fun filePart(mimeType: String, uri: String): JSONObject =
            JSONObject().put("fileData", JSONObject().put("mimeType", mimeType).put("fileUri", uri))
    }
}
