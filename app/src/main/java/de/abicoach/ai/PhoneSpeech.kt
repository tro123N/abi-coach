package de.abicoach.ai

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.RequiresApi
import de.abicoach.session.SpeechSegment
import de.abicoach.session.SpeechSegmenter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * The phone's own ears and voice: Android's speech recognition and text to speech. Claude can neither hear nor
 * speak, so with Claude these do the listening and talking while Claude does all the thinking.
 */
class PhoneSpeech(context: Context, private val localeOf: () -> Locale = { Locale.UK }) {
    /** British or American English for the phone's voice and its speech recognition (follows the setting). */
    private val locale: Locale get() = localeOf()

    private val context = context.applicationContext
    private val recognizeLock = Mutex()
    private val speakLock = Mutex()

    @Volatile private var engine: TextToSpeech? = null

    @Volatile private var engineReady: CompletableDeferred<Boolean>? = null

    @Volatile private var engineOk = false

    /** Writing down recordings needs Android 13 (the audio is handed to the recognizer through a pipe). */
    val canRecognize: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isRecognitionAvailable(context)

    /** Writes down 16 kHz mono PCM with Android's speech recognition; empty if nothing was understood. */
    suspend fun transcribe(pcm16k: ByteArray): String {
        if (!canRecognize || pcm16k.size < 3_200) return ""
        return recognizeLock.withLock { withTimeoutOrNull(15_000) { recognize(pcm16k) } ?: "" }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun recognize(pcm16k: ByteArray): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val recognizer =
                    if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    } else {
                        SpeechRecognizer.createSpeechRecognizer(context)
                    }
                val pipe = ParcelFileDescriptor.createPipe()
                val finished = AtomicBoolean(false)

                fun finish(text: String) {
                    if (!finished.compareAndSet(false, true)) return
                    runCatching { recognizer.destroy() }
                    runCatching { pipe[0].close() }
                    if (cont.isActive) cont.resume(text)
                }
                recognizer.setRecognitionListener(
                    object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit

                        override fun onBeginningOfSpeech() = Unit

                        override fun onRmsChanged(rmsdB: Float) = Unit

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() = Unit

                        override fun onError(error: Int) = finish("")

                        override fun onResults(results: Bundle?) =
                            finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())

                        override fun onPartialResults(partialResults: Bundle?) = Unit

                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    },
                )
                val intent =
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (locale == Locale.US) "en-US" else "en-GB")
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                    }
                try {
                    recognizer.startListening(intent)
                } catch (e: Exception) {
                    finish("")
                    return@suspendCancellableCoroutine
                }
                Thread {
                    runCatching {
                        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                            var i = 0
                            while (i < pcm16k.size) {
                                val end = minOf(pcm16k.size, i + 3_200)
                                out.write(pcm16k, i, end - i)
                                i = end
                            }
                            // Half a second of silence, so that the recognizer notices the end of the speech.
                            out.write(ByteArray(16_000))
                        }
                    }
                }.apply { isDaemon = true }.start()
                cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { finish("") } }
            }
        }

    /** Text to 16-bit PCM mono at 24 kHz with Android's voice (British English); null if it did not work. */
    suspend fun synthesize(text: String): ByteArray? =
        speakLock.withLock {
            val tts = engine() ?: return@withLock null
            runCatching { tts.language = locale }
            val file = File(context.cacheDir, "phone_tts_${System.nanoTime()}.wav")
            val id = "abicoach-${System.nanoTime()}"
            val done = CompletableDeferred<Boolean>()
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id) done.complete(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id) done.complete(false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == id) done.complete(false)
                    }
                },
            )
            val started = withContext(Dispatchers.Main) { tts.synthesizeToFile(text.take(3_000), Bundle(), file, id) } == TextToSpeech.SUCCESS
            if (!started) return@withLock null
            val ok = withTimeoutOrNull(12_000) { done.await() } ?: false
            val wav = if (ok && file.exists()) runCatching { file.readBytes() }.getOrNull() else null
            file.delete()
            wav?.let { de.abicoach.audio.Wav.toPcm(it, 24_000) }
        }

    /** Started once; called under [speakLock]. */
    private suspend fun engine(): TextToSpeech? {
        if (engineOk) engine?.let { return it }
        val ready = engineReady ?: CompletableDeferred<Boolean>().also { d ->
            engineReady = d
            withContext(Dispatchers.Main) {
                engine =
                    TextToSpeech(context) { status ->
                        val ok = status == TextToSpeech.SUCCESS
                        if (ok) runCatching { engine?.language = locale }
                        d.complete(ok)
                    }
            }
        }
        val ok = withTimeoutOrNull(5_000) { ready.await() } ?: false
        if (!ok) {
            engineReady = null
            runCatching { engine?.shutdown() }
            engine = null
            return null
        }
        engineOk = true
        return engine
    }
}

/**
 * The phone's ear for one live session: cuts the student's speech at pauses and writes each piece down with
 * Android's speech recognition. Used with Claude; the examiner listens to the same pieces.
 */
class PhoneEar(private val phone: PhoneSpeech) : LiveTranscriber {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + de.abicoach.data.CrashLog.handler)
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val segmenter =
        SpeechSegmenter { segment ->
            writing.incrementAndGet()
            scope.launch {
                try {
                    write(segment)
                } finally {
                    writing.decrementAndGet()
                }
            }
        }

    /** Where the events of the transcript go (set by the hub). */
    @Volatile var onEvent: (LiveEvent) -> Unit = {}

    @Volatile override var ready = false
        private set

    /** When the student was last heard speaking. */
    val lastVoiceAt: Long get() = segmenter.lastVoiceAt

    /** Pieces still being written down by the phone's recognizer. */
    private val writing = java.util.concurrent.atomic.AtomicInteger(0)

    val busy: Boolean get() = writing.get() > 0

    fun addListener(listener: (String) -> Unit) {
        listeners += listener
    }

    override fun connect() {
        ready = phone.canRecognize
        if (ready) {
            onEvent(LiveEvent.Ready)
        } else {
            onEvent(LiveEvent.Failed("Die Spracherkennung des Handys braucht Android 13 oder neuer."))
        }
    }

    override fun sendAudio(pcm16k: ByteArray) {
        if (ready) segmenter.feed(pcm16k, System.currentTimeMillis())
    }

    override fun sendStreamEnd() = segmenter.flush(System.currentTimeMillis())

    override fun close() {
        ready = false
        scope.cancel()
    }

    private suspend fun write(segment: SpeechSegment) {
        val text = runCatching { phone.transcribe(segment.pcm) }.getOrDefault("").trim()
        if (text.isBlank()) return
        onEvent(LiveEvent.UserText(text))
        listeners.forEach { runCatching { it(text) } }
    }
}

/**
 * Claude as the examiner: the phone's ear writes down what the student says, Claude answers in text, the phone's
 * voice speaks it. Claude answers when the student has really finished (no speech for about 1.4 seconds).
 */
class PhoneVoiceAgent(
    private val phone: PhoneSpeech,
    private val ear: PhoneEar?,
    private val instructions: String,
    private val answer: suspend (system: String, turns: List<Pair<String, String>>) -> String,
    private val onEvent: (LiveEvent) -> Unit,
    override val label: String,
    /** "Knopf gedrückt halten": Claude answers when the student lets go, not after a pause. */
    private val manualTurns: Boolean = false,
) : VoiceAgent {
    override val outputRate: Int = 24_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + de.abicoach.data.CrashLog.handler)
    private val lock = Any()
    private val talk = Mutex()
    private val history = ArrayList<Pair<String, String>>()
    private val notes = ArrayList<String>()
    private val heard = StringBuilder()
    private var waiting: Job? = null

    @Volatile private var lastHeardAt = 0L

    override fun connect() {
        ear?.addListener { text ->
            synchronized(lock) { heard.append(' ').append(text) }
            lastHeardAt = System.currentTimeMillis()
            if (!manualTurns) replySoon()
        }
        onEvent(LiveEvent.Ready)
    }

    private fun replySoon() {
        waiting?.cancel()
        waiting =
            scope.launch {
                while (true) {
                    delay(300)
                    val quiet = System.currentTimeMillis() - maxOf(lastHeardAt, ear?.lastVoiceAt ?: 0L)
                    if (quiet > 1_400) break
                }
                val said = synchronized(lock) { heard.toString().trim().also { heard.setLength(0) } }
                if (said.isNotBlank()) reply(said, null)
            }
    }

    private suspend fun reply(said: String?, instruction: String?) =
        talk.withLock {
            val turns =
                synchronized(lock) {
                    if (said != null) history += "user" to said
                    val t = history.takeLast(30).toMutableList()
                    if (instruction != null) t += "user" to "[Note from the app, not said by the student] $instruction"
                    t
                }
            val system =
                instructions + synchronized(lock) { if (notes.isEmpty()) "" else "\n\nNotes from the app (follow them, never read them out):\n" + notes.joinToString("\n") } +
                    "\n\nYou speak through a phone's voice: answer with one or two short spoken sentences, no lists, no markdown, no stage directions."
            val text =
                try {
                    answer(system, turns).trim()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onEvent(LiveEvent.Info("$label antwortet gerade nicht: ${e.message?.take(80)}"))
                    return@withLock
                }
            if (text.isBlank()) return@withLock
            synchronized(lock) { history += "assistant" to text }
            onEvent(LiveEvent.ModelText(text))
            val pcm = phone.synthesize(text)
            if (pcm != null && pcm.size > 2_000) onEvent(LiveEvent.Audio(pcm)) else onEvent(LiveEvent.Info("Die Handy-Stimme konnte den Satz nicht sprechen."))
            onEvent(LiveEvent.TurnComplete)
        }

    /** Handled by the phone's ear. */
    override fun sendAudio(pcm16k: ByteArray) = Unit

    override fun sendInstruction(text: String): Boolean {
        scope.launch { reply(null, text) }
        return true
    }

    override fun sendNote(text: String): Boolean {
        synchronized(lock) {
            notes += text
            while (notes.size > 6) notes.removeAt(0)
        }
        return true
    }

    override fun sendStreamEnd() = Unit

    override fun turnStart() {
        waiting?.cancel()
    }

    /** Released: the last piece is written down at once, then Claude answers – no waiting for a pause. */
    override fun turnEnd() {
        if (!manualTurns) return
        ear?.sendStreamEnd()
        waiting?.cancel()
        waiting =
            scope.launch {
                // The recognizer needs a moment for the last piece (at most about four seconds).
                var waited = 0
                delay(60)
                while ((ear?.busy == true) && waited < 4_000) {
                    delay(50)
                    waited += 50
                }
                val said = synchronized(lock) { heard.toString().trim().also { heard.setLength(0) } }
                if (said.isNotBlank()) reply(said, null)
            }
    }

    override fun close() = scope.cancel()
}
