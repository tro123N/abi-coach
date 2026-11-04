package de.abicoach.ai

/**
 * Which company's AI does the work. Everything in the app goes through the chosen one, so the student can compare
 * them: Google (Gemini) and OpenAI hear and speak themselves; Claude (Anthropic) cannot hear or speak, so with it the
 * phone's own speech recognition and voice do the listening and talking while Claude does all the thinking.
 */
enum class Provider(val id: String, val label: String, val company: String, val keyUrl: String) {
    GOOGLE("gemini", "Google Gemini", "Google", "https://aistudio.google.com/apikey"),
    OPENAI("openai", "OpenAI", "OpenAI", "https://platform.openai.com/api-keys"),
    ANTHROPIC("anthropic", "Anthropic Claude", "Anthropic", "https://platform.claude.com/settings/keys"),
    ;

    companion object {
        fun of(id: String?): Provider = entries.firstOrNull { it.id == id || it.name == id } ?: GOOGLE
    }
}

/** Splits Gemini-style parts ({"text"}, {"inlineData"}, {"fileData"}) for the other providers. */
internal object Parts {
    fun text(p: org.json.JSONObject): String? = if (p.has("text")) p.optString("text") else null

    fun inline(p: org.json.JSONObject): Pair<String, String>? = p.optJSONObject("inlineData")?.let { it.optString("mimeType") to it.optString("data") }

    fun hasAudio(parts: List<org.json.JSONObject>): Boolean =
        parts.any { p ->
            inline(p)?.first?.startsWith("audio/") == true || p.optJSONObject("fileData")?.optString("mimeType")?.startsWith("audio/") == true
        }
}
