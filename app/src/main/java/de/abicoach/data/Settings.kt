package de.abicoach.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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

/** All AI comes from Google. "Beste Qualität" lets the strongest Gemini model grade and write; "Sparsam" uses the cheaper Flash model. */
enum class Quality(val label: String, val description: String) {
    BEST("Beste Qualität", "Gemini 4 Argon bewertet, schreibt die Aufgaben und coacht – am genauesten."),
    SPARSAM("Sparsam", "Gemini 3.8 Flash für alles – schneller und deutlich günstiger."),
}

/** All user settings. API keys are stored encrypted; everything else is plain preferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("abicoach", Context.MODE_PRIVATE)
    private val cipher = KeyCipher()

    init {
        // The app uses Google only: keys and settings of the former OpenAI and Anthropic support are removed.
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

    /** The evaluation runs in Google's cloud: it goes on when the app is closed or the phone is off. */
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
        val GEMINI_VOICES = listOf("Charon", "Kore", "Puck", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
    }
}
