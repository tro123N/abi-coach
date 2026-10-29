package de.abicoach.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the API key with an AES key that never leaves the Android Keystore. */
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

/** All user settings. The Gemini key is stored encrypted; everything else is plain preferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("abicoach", Context.MODE_PRIVATE)
    private val cipher = KeyCipher()

    var geminiKey: String
        get() = prefs.getString(KEY_GEMINI, null)?.let { cipher.decrypt(it) }.orEmpty()
        set(value) {
            val v = value.trim()
            prefs.edit().putString(KEY_GEMINI, if (v.isEmpty()) null else cipher.encrypt(v)).apply()
        }

    var liveModel: String
        get() = string("live_model", DEFAULT_LIVE_MODEL)
        set(value) = put("live_model", value, DEFAULT_LIVE_MODEL)

    var transcribeModel: String
        get() = string("transcribe_model", DEFAULT_TRANSCRIBE_MODEL)
        set(value) = put("transcribe_model", value, DEFAULT_TRANSCRIBE_MODEL)

    var analysisModel: String
        get() = string("analysis_model", DEFAULT_ANALYSIS_MODEL)
        set(value) = put("analysis_model", value, DEFAULT_ANALYSIS_MODEL)

    var voice: String
        get() = string("voice", DEFAULT_VOICE)
        set(value) = put("voice", value, DEFAULT_VOICE)

    /** Exam date as YYYY-MM-DD, empty when unknown. */
    var examDate: String
        get() = prefs.getString("exam_date", "").orEmpty()
        set(value) = prefs.edit().putString("exam_date", value.trim()).apply()

    /** Free text: topics, novels, films and keywords from the student's own course. */
    var courseTopics: String
        get() = prefs.getString("course_topics", "").orEmpty()
        set(value) = prefs.edit().putString("course_topics", value.trim()).apply()

    var uploadAudio: Boolean
        get() = prefs.getBoolean("upload_audio", true)
        set(value) = prefs.edit().putBoolean("upload_audio", value).apply()

    var showTranscript: Boolean
        get() = prefs.getBoolean("show_transcript", false)
        set(value) = prefs.edit().putBoolean("show_transcript", value).apply()

    private fun string(key: String, default: String): String =
        prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: default

    private fun put(key: String, value: String, default: String) {
        prefs.edit().putString(key, value.trim().ifEmpty { default }).apply()
    }

    companion object {
        private const val KEY_GEMINI = "gemini_key_enc"
        const val DEFAULT_LIVE_MODEL = "gemini-3.8-live"
        const val DEFAULT_TRANSCRIBE_MODEL = "gemini-3.5-transcribe-live"
        const val DEFAULT_ANALYSIS_MODEL = "gemini-3.8-flash"
        const val DEFAULT_VOICE = "Charon"
        val VOICES = listOf("Charon", "Kore", "Puck", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
    }
}
