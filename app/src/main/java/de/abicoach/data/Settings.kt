package de.abicoach.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import de.abicoach.ai.Provider
import de.abicoach.ui.theme.Palette
import de.abicoach.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the API keys with an AES key that never leaves the Android Keystore. */
internal class KeyCipher {
    private val alias = "abicoach_api_key"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$data"
    }

    fun decrypt(stored: String): String? =
        runCatching {
            val parts = stored.split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
}

/** "Beste Qualität" lets the strongest model of the chosen company grade and write; "Sparsam" uses its cheaper one. */
enum class Quality(val label: String, val description: String) {
    BEST("Beste Qualität", "Das stärkste Modell bewertet, schreibt die Aufgaben und coacht – am genauesten."),
    SPARSAM("Sparsam", "Ein schnelleres, günstigeres Modell für alles – deutlich billiger."),
}

/** How the examiner leads a conversation. */
enum class TalkStyle(val label: String, val description: String) {
    PRUEFUNG("Wie im Abitur", "Der Prüfer führt mit klaren Fragen, wechselt pünktlich das Thema und hakt nach – wie in der echten Prüfung."),
    AUSGEWOGEN("Ausgewogen", "Der Prüfer folgt seinem Plan, baut aber jede Frage auf dem auf, was du gerade gesagt hast."),
    EINGEHEN("Geht auf dich ein", "Der Prüfer greift deine Gedanken und Beispiele auf und fragt dazu nach – und lenkt das Gespräch dabei behutsam zum Prüfungsthema."),
    ;

    companion object {
        fun of(name: String?): TalkStyle? = entries.firstOrNull { it.name == name }
    }
}

/** How hard the examiner is on the student – the points stay the same, only the tone and the pressure change. */
enum class Strictness(val label: String, val description: String) {
    FREUNDLICH("Freundlich", "Der Prüfer ist warm und ermutigend – gut zum Einstieg und wenn du nervös bist."),
    NORMAL("Normal", "Sachlich und neutral, wie die meisten Prüfer im Abitur."),
    STRENG("Streng", "Kühl und anspruchsvoll: wenig Lob, hakt bei vagen Antworten sofort nach und verlangt Belege."),
    STRESSTEST(
        "Stresstest",
        "Extrem kritisch: kalt, ungeduldig, unterbricht, deckt jede Lücke und jeden Widerspruch auf und lässt dich in unangenehmen Pausen stehen – " +
            "der schwierigste Prüfer, den du dir vorstellen kannst. Er greift nur deine Antworten an, nie dich als Person. Abbrechen kannst du jederzeit.",
    ),
    ;

    companion object {
        fun of(name: String?): Strictness? = entries.firstOrNull { it.name == name }
    }
}

/** All user settings. API keys are stored encrypted; everything else is plain preferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("abicoach", Context.MODE_PRIVATE)
    private val cipher = KeyCipher()

    init {
        // Settings of a much older version (other key format and models): removed.
        val old = listOf("openai_key_enc", "anthropic_key_enc", "claude_brain_model", "claude_fast_model", "openai_live_model", "openai_voice")
        if (old.any { prefs.contains(it) }) prefs.edit().apply { old.forEach { remove(it) } }.apply()
    }

    /** Bumped on every change so the screens can recompose. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun changed() {
        _version.value = _version.value + 1
    }

    // ---------- Keys ----------

    var geminiKey: String
        get() = secret(KEY_GEMINI)
        set(value) = putSecret(KEY_GEMINI, value)

    var openAiKey: String
        get() = secret(KEY_OPENAI)
        set(value) = putSecret(KEY_OPENAI, value)

    var anthropicKey: String
        get() = secret(KEY_ANTHROPIC)
        set(value) = putSecret(KEY_ANTHROPIC, value)

    /** Read-only GitHub token for the in-app update (the repository is private); encrypted, never in a backup. */
    var githubToken: String
        get() = secret(KEY_GITHUB)
        set(value) = putSecret(KEY_GITHUB, value)

    /** Where the updates come from (GitHub "owner/repo"). */
    var updateRepo by StringPref("update_repo", "tro123N/abi-coach")

    /** When the app last asked GitHub for a new version (it asks by itself at most every 12 hours). */
    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0L)
        set(value) = prefs.edit().putLong("last_update_check", value).apply()

    fun keyOf(provider: Provider): String =
        when (provider) {
            Provider.GOOGLE -> geminiKey
            Provider.OPENAI -> openAiKey
            Provider.ANTHROPIC -> anthropicKey
        }

    fun setKey(provider: Provider, value: String) {
        when (provider) {
            Provider.GOOGLE -> geminiKey = value
            Provider.OPENAI -> openAiKey = value
            Provider.ANTHROPIC -> anthropicKey = value
        }
    }

    /** The company whose AI does everything in the app (one key, one bill), so the student can compare them. */
    var provider: Provider
        get() = Provider.of(prefs.getString("provider", null))
        set(value) = putString("provider", value.id)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun secret(key: String): String =
        cache.getOrPut(key) { prefs.getString(key, null)?.let { cipher.decrypt(it) }.orEmpty() }

    private fun putSecret(key: String, value: String) {
        val v = value.trim()
        prefs.edit().putString(key, if (v.isEmpty()) null else cipher.encrypt(v)).apply()
        cache[key] = v
        changed()
    }

    var quality: Quality
        get() = enumValue("quality", Quality.BEST)
        set(value) = putString("quality", value.name)

    // ---------- Models (editable under "Erweitert") ----------

    var geminiLiveModel by StringPref("live_model", "gemini-3.8-live")
    var geminiVoice by StringPref("voice", "Charon")
    var transcribeModel by StringPref("transcribe_model", "gemini-3.5-transcribe-live")
    var geminiTextModel by StringPref("analysis_model", "gemini-3.8-flash")

    /** The model of the live error check while the student speaks: must answer in a second or two. */
    var geminiFastModel by StringPref("fast_model", "gemini-3.8-flash")

    /** The strongest Gemini model: grades, writes the exam material, coaches and listens to the recordings. */
    var geminiBestModel by StringPref("best_model", "gemini-4-argon")
    var geminiReaderModel by StringPref("reader_model", "gemini-3.8-flash")
    var embeddingModel by StringPref("embedding_model", "gemini-embedding-2")
    var ttsModel by StringPref("tts_model", "gemini-3.8-flash-tts")
    var ttsVoice by StringPref("tts_voice", "Kore")

    // OpenAI
    var oaBrainModel by StringPref("oa_brain_model", "gpt-6.1-sol")
    var oaTextModel by StringPref("oa_text_model", "gpt-6.1-terra")
    var oaFastModel by StringPref("oa_fast_model", "gpt-6-luna")
    var oaAudioModel by StringPref("oa_audio_model", "gpt-audio-1.5")
    var oaLiveModel by StringPref("oa_live_model", "gpt-live-1")

    /** Takes over as examiner if GPT-Live cannot be reached with the key. */
    var oaRealtimeModel by StringPref("oa_realtime_model", "gpt-realtime")
    var oaVoice by StringPref("oa_voice", "cedar")
    var oaTranscribeModel by StringPref("oa_transcribe_model", "gpt-live-transcribe")
    var oaFileTranscribeModel by StringPref("oa_file_transcribe_model", "gpt-transcribe")
    var oaTtsModel by StringPref("oa_tts_model", "gpt-4o-mini-tts")
    var oaTtsVoice by StringPref("oa_tts_voice", "marin")
    var oaEmbeddingModel by StringPref("oa_embedding_model", "text-embedding-3-large")

    // Anthropic (Claude thinks; the phone hears and speaks)
    var clBrainModel by StringPref("cl_brain_model", "claude-opus-5-5")
    var clTextModel by StringPref("cl_text_model", "claude-sonnet-5-5")
    var clFastModel by StringPref("cl_fast_model", "claude-haiku-5-5")

    /** Which model made the search vectors of the course material ("gemini:gemini-embedding-2"); empty = none yet. */
    var vectorSpace by StringPref("vector_space", "")

    // ---------- Exam and course ----------

    /** Exam date as YYYY-MM-DD, empty when unknown. */
    var examDate by StringPref("exam_date", "")

    /** Free text: topics, novels, films and keywords from the student's own course. */
    var courseTopics by StringPref("course_topics", "")

    var studentName by StringPref("student_name", "")

    /** Minutes per day the student wants to practise. */
    var dailyMinutes: Int
        get() = prefs.getInt("daily_minutes", 20)
        set(value) {
            prefs.edit().putInt("daily_minutes", value).apply()
            changed()
        }

    var defaultFeedback: FeedbackMode
        get() = enumValue("default_feedback", FeedbackMode.FLUESTERN)
        set(value) = putString("default_feedback", value.name)

    var uploadAudio by BoolPref("upload_audio", true)
    var showTranscript by BoolPref("show_transcript", false)

    /** With headphones the whisper hint is also spoken very softly. */
    var whisperAudio by BoolPref("whisper_audio", true)

    /** Prepares the next exam text in the background so that "Los geht's" starts at once. */
    var prefetchMaterial by BoolPref("prefetch_material", true)

    /** With Google the evaluation runs in Google's cloud: it goes on when the app is closed or the phone is off. */
    var cloudAnalysis by BoolPref("cloud_analysis", true)

    // ---------- Look and feel ----------

    var themeMode: ThemeMode
        get() = enumValue("theme_mode", ThemeMode.SYSTEM)
        set(value) = putString("theme_mode", value.name)

    var palette: Palette
        get() = enumValue("palette", Palette.STANDARD)
        set(value) = putString("palette", value.name)

    var sound by BoolPref("sound", true)
    var vibration by BoolPref("vibration", true)

    var reminderOn by BoolPref("reminder_on", false)

    var reminderHour: Int
        get() = prefs.getInt("reminder_hour", 18)
        set(value) {
            prefs.edit().putInt("reminder_hour", value).apply()
            changed()
        }

    /** Shown once: the welcome card on the Abi-Weg. */
    var welcomeDone by BoolPref("welcome_done", false)

    /** The setup at the very first start (AI, key, exam date, course, microphone) is through. */
    var onboardingDone by BoolPref("onboarding_done", false)

    /** The guided tour through the app was seen (or skipped). */
    var tourDone by BoolPref("tour_done", false)

    /** In a live session the page follows the transcript to the newest line ("Mitscrollen"); the student can switch it off. */
    var liveFollow by BoolPref("live_follow", true)

    /** How hard the examiner is (and how blunt the written feedback is); the grading itself stays the same. */
    var strictness: Strictness
        get() = enumValue("strictness", Strictness.NORMAL)
        set(value) = putString("strictness", value.name)

    /** "Knopf gedrückt halten" in conversations: the student holds a button while he speaks, the examiner answers on release. */
    var pushToTalk by BoolPref("push_to_talk", false)

    /** How the examiner leads a conversation; can be changed for each session. */
    var talkStyle: TalkStyle
        get() = enumValue("talk_style", TalkStyle.AUSGEWOGEN)
        set(value) = putString("talk_style", value.name)

    /**
     * Which topic fields the course covered in which Kurshalbjahr ("Q1.1" → topic ids), as the student set it.
     * Half-years not set here are taken from the uploaded material.
     */
    var halfYearTopics: Map<String, Set<Int>>
        get() =
            runCatching {
                val o = org.json.JSONObject(prefs.getString("halfyear_topics", null) ?: "{}")
                o.keys().asSequence().associateWith { k ->
                    val a = o.optJSONArray(k) ?: org.json.JSONArray()
                    (0 until a.length()).map { a.optInt(it, -1) }.filter { it in 0..13 }.toSet()
                }
            }.getOrDefault(emptyMap())
        set(value) =
            putString(
                "halfyear_topics",
                org.json.JSONObject().apply { value.forEach { (k, v) -> put(k, org.json.JSONArray(v.sorted())) } }.toString(),
            )

    // ---------- backup ----------

    /** All settings except the API keys, for the export of the learning progress. */
    fun exportPrefs(): org.json.JSONObject {
        val o = org.json.JSONObject()
        prefs.all.forEach { (k, v) ->
            if (k.endsWith("_enc") || v == null) return@forEach
            val entry =
                when (v) {
                    is Boolean -> org.json.JSONObject().put("t", "b").put("v", v)
                    is Int -> org.json.JSONObject().put("t", "i").put("v", v)
                    is Long -> org.json.JSONObject().put("t", "l").put("v", v)
                    is Float -> org.json.JSONObject().put("t", "f").put("v", v.toDouble())
                    is String -> org.json.JSONObject().put("t", "s").put("v", v)
                    else -> null
                } ?: return@forEach
            o.put(k, entry)
        }
        return o
    }

    /** Takes back exported settings; the API keys on this phone stay as they are. */
    fun importPrefs(o: org.json.JSONObject) {
        val edit = prefs.edit()
        o.keys().forEach { k ->
            if (k.endsWith("_enc")) return@forEach
            val e = o.optJSONObject(k) ?: return@forEach
            when (e.optString("t")) {
                "b" -> edit.putBoolean(k, e.optBoolean("v"))
                "i" -> edit.putInt(k, e.optInt("v"))
                "l" -> edit.putLong(k, e.optLong("v"))
                "f" -> edit.putFloat(k, e.optDouble("v").toFloat())
                "s" -> edit.putString(k, e.optString("v"))
            }
        }
        edit.commit()
        changed()
    }

    // ---------- helpers ----------

    private inline fun <reified T : Enum<T>> enumValue(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default

    private fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        changed()
    }

    private inner class StringPref(private val key: String, private val default: String) {
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String =
            prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: default

        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String) {
            prefs.edit().putString(key, value.trim()).apply()
            changed()
        }
    }

    private inner class BoolPref(private val key: String, private val default: Boolean) {
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Boolean = prefs.getBoolean(key, default)

        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) {
            prefs.edit().putBoolean(key, value).apply()
            changed()
        }
    }

    companion object {
        private const val KEY_GEMINI = "gemini_key_enc"
        private const val KEY_OPENAI = "openai_api_key_enc"
        private const val KEY_ANTHROPIC = "anthropic_api_key_enc"
        private const val KEY_GITHUB = "github_token_enc"
        val GEMINI_VOICES = listOf("Charon", "Kore", "Puck", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
        val OPENAI_VOICES = listOf("cedar", "marin", "ash", "ballad", "coral", "sage", "verse", "alloy")
    }
}
