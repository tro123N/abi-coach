package de.abicoach.session

import de.abicoach.AbiCoachApp
import de.abicoach.audio.AudioRouting
import de.abicoach.audio.AudioUtil
import de.abicoach.audio.MicStreamer
import de.abicoach.audio.PcmPlayer
import de.abicoach.audio.Tts
import de.abicoach.audio.WavWriter
import de.abicoach.data.FeedbackMode
import de.abicoach.data.LiveError
import de.abicoach.data.Speaker
import de.abicoach.data.Utterance
import de.abicoach.gemini.LiveEvent
import de.abicoach.gemini.LiveSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class LiveUiState(
    val part: Int = 0,
    val status: String = "Verbinde …",
    val examinerReady: Boolean = false,
    val transcriberReady: Boolean = false,
    val utterances: List<Utterance> = emptyList(),
    val interim: String = "",
    val examinerLive: String = "",
    val liveErrors: List<LiveError> = emptyList(),
    val lastAlert: LiveError? = null,
    val elapsedSec: Int = 0,
    val micLevel: Float = 0f,
    val examinerSpeaking: Boolean = false,
    val info: String? = null,
    val error: String? = null,
)

/**
 * One live part of a session: the examiner (Gemini Live, optional), the verbatim transcriber running in
 * parallel, the live error check and the recording for the later pronunciation analysis.
 */
class LiveConversation(private val app: AbiCoachApp, private val config: Config) {
    data class Config(
        /** 1 = presentation, 2 = exam conversation, 0 = training. */
        val part: Int,
        /** System instructions for the examiner; null means no examiner (the presentation in part 1). */
        val examinerPrompt: String?,
        /** Text turn that makes the examiner start speaking. */
        val kickoff: String?,
        val feedback: FeedbackMode,
        val vocabulary: List<String>,
        val wavFile: File?,
        val checkerContext: String,
        val targetSeconds: Int,
    )

    data class Result(val utterances: List<Utterance>, val liveErrors: List<LiveError>, val seconds: Int)

    private val _state = MutableStateFlow(LiveUiState(part = config.part))
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val routing = AudioRouting(app)
    private val player = PcmPlayer()
    private val tts = Tts(app)
    private var mic: MicStreamer? = null
    private var wav: WavWriter? = null

    @Volatile private var examiner: LiveSocket? = null

    @Volatile private var transcriber: LiveSocket? = null
    private var checker: LiveChecker? = null

    private val lock = Any()
    private val examinerTurn = StringBuilder()
    private val fallbackStudent = StringBuilder()

    @Volatile private var transcriberWorks = false

    @Volatile private var kickoffSent = false

    @Volatile private var lastSpeechAt = System.currentTimeMillis()

    @Volatile private var lastImpulseAt = 0L

    @Volatile private var muteUntil = 0L

    @Volatile private var finished = false
    private val startedAt = System.currentTimeMillis()

    fun start() {
        val key = app.settings.geminiKey
        if (key.isBlank()) {
            _state.update { it.copy(error = "Kein API-Schlüssel eingetragen (Einstellungen).") }
            return
        }
        routing.enter()
        player.start()
        config.wavFile?.let { wav = WavWriter(it, 16_000) }

        transcriber =
            LiveSocket(
                http = app.http,
                apiKey = key,
                name = "Mitschrift",
                supportsResumption = false,
                setupFactory = { _, minimal -> Prompts.transcriberSetup(app.settings.transcribeModel, config.vocabulary, minimal) },
                onEvent = ::onTranscriberEvent,
            ).also { it.connect() }

        config.examinerPrompt?.let { prompt ->
            examiner =
                LiveSocket(
                    http = app.http,
                    apiKey = key,
                    name = "Prüfer",
                    supportsResumption = true,
                    setupFactory = { handle, minimal ->
                        Prompts.examinerSetup(app.settings.liveModel, app.settings.voice, prompt, handle, minimal)
                    },
                    onEvent = ::onExaminerEvent,
                ).also { it.connect() }
        }

        if (config.feedback != FeedbackMode.PRUEFUNG) {
            checker = LiveChecker(app.rest, app.settings.analysisModel, scope, config.checkerContext, ::onLiveErrors).also { it.start() }
        }

        mic = MicStreamer(::onMic).also { it.start(scope) }

        scope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val seconds = ((now - startedAt) / 1000).toInt()
        _state.update { it.copy(elapsedSec = seconds, status = statusText(it)) }
        // Part 1: like a real examiner, give a short impulse if the student has been silent for a while.
        if (config.part == 1 && seconds > 20 && now - lastSpeechAt > 25_000 && now - lastImpulseAt > 60_000) {
            lastImpulseAt = now
            muteUntil = now + 5_000
            tts.say(Prompts.IMPULSES.random())
        }
    }

    private fun statusText(s: LiveUiState): String =
        when {
            s.error != null -> "Fehler – siehe unten"
            config.examinerPrompt != null && !s.examinerReady -> "Verbinde mit dem Prüfer …"
            transcriber != null && !s.transcriberReady -> "Starte die Mitschrift …"
            s.examinerSpeaking -> "Prüfer spricht …"
            config.part == 1 -> "Du hast das Wort – halte deinen Vortrag."
            else -> "Ich höre zu …"
        }

    private fun onMic(pcm: ByteArray) {
        val now = System.currentTimeMillis()
        val level = AudioUtil.level(pcm)
        val examinerAudible = player.isPlaying(now) || now < muteUntil
        // On the loudspeaker the examiner would hear (and interrupt) himself: send silence while he talks.
        // With headphones the real microphone signal goes through, so the student can cut in.
        examiner?.sendAudio(if (examinerAudible && !routing.usingHeadset) ByteArray(pcm.size) else pcm)
        // While the examiner (or the app) is speaking, the microphone mostly hears the loudspeaker:
        // keep that out of the student's transcript and recording.
        if (!examinerAudible) {
            transcriber?.sendAudio(pcm)
            wav?.write(pcm)
            if (level > 0.06f) lastSpeechAt = now
        }
        _state.update { it.copy(micLevel = level, examinerSpeaking = examinerAudible) }
    }

    private fun onTranscriberEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.Ready -> _state.update { it.copy(transcriberReady = true) }
            is LiveEvent.UserInterim -> {
                if (event.text.isNotBlank()) lastSpeechAt = System.currentTimeMillis()
                _state.update { it.copy(interim = event.text) }
            }
            is LiveEvent.UserText -> {
                transcriberWorks = true
                addStudent(event.text)
            }
            is LiveEvent.Failed -> {
                transcriber = null
                _state.update {
                    it.copy(
                        transcriberReady = false,
                        info = "Wortgetreue Mitschrift nicht verfügbar (${event.message}). Die Auswertung nutzt die Aufnahme.",
                    )
                }
            }
            is LiveEvent.Info -> _state.update { it.copy(info = event.message) }
            else -> Unit
        }
    }

    private fun onExaminerEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.Ready -> {
                _state.update { it.copy(examinerReady = true) }
                if (!kickoffSent) {
                    kickoffSent = true
                    config.kickoff?.let { examiner?.sendUserTurn(it) }
                }
            }
            is LiveEvent.Audio -> {
                flushFallbackStudent()
                player.enqueue(event.pcm)
            }
            is LiveEvent.ModelText -> {
                flushFallbackStudent()
                val text = synchronized(lock) { examinerTurn.append(event.text).toString() }
                _state.update { it.copy(examinerLive = text) }
            }
            is LiveEvent.UserText -> {
                if (!transcriberWorks) synchronized(lock) { fallbackStudent.append(event.text) }
            }
            is LiveEvent.TurnComplete -> flushExaminer(interrupted = false)
            is LiveEvent.Interrupted -> {
                player.flush()
                flushExaminer(interrupted = true)
            }
            is LiveEvent.Failed -> _state.update { it.copy(error = event.message) }
            is LiveEvent.Info -> _state.update { it.copy(info = event.message) }
            else -> Unit
        }
    }

    private fun addStudent(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        lastSpeechAt = System.currentTimeMillis()
        val utterance = Utterance(Speaker.STUDENT, clean, config.part, System.currentTimeMillis() - startedAt)
        _state.update { it.copy(utterances = it.utterances + utterance, interim = "") }
        checker?.submit(clean)
    }

    private fun flushExaminer(interrupted: Boolean) {
        val text =
            synchronized(lock) {
                val t = examinerTurn.toString().trim()
                examinerTurn.setLength(0)
                t
            }
        if (text.isEmpty()) return
        val shown = if (interrupted) "$text …" else text
        val utterance = Utterance(Speaker.EXAMINER, shown, config.part, System.currentTimeMillis() - startedAt)
        _state.update { it.copy(utterances = it.utterances + utterance, examinerLive = "") }
    }

    /** Uses the examiner's own (non-verbatim) transcript only if the verbatim transcriber is not working. */
    private fun flushFallbackStudent() {
        if (transcriberWorks) return
        val text =
            synchronized(lock) {
                val t = fallbackStudent.toString().trim()
                fallbackStudent.setLength(0)
                t
            }
        if (text.isNotEmpty()) addStudent(text)
    }

    private fun onLiveErrors(errors: List<LiveError>) {
        _state.update { it.copy(liveErrors = it.liveErrors + errors) }
        if (config.feedback == FeedbackMode.PRUEFUNG || finished) return
        val counts = app.store.categoryCounts()
        val important = errors.filter { it.severity == "major" || it.severity == "content" || (counts[it.category] ?: 0) >= 3 }
        val alert = important.firstOrNull() ?: errors.firstOrNull()?.takeIf { config.feedback == FeedbackMode.FLUESTERN }
        if (alert == null) return
        _state.update { it.copy(lastAlert = alert) }
        AudioUtil.vibrate(app)
        if (config.feedback == FeedbackMode.COACH && important.isNotEmpty()) {
            val examinerSocket = examiner
            if (examinerSocket != null) {
                examinerSocket.sendUserTurn(Prompts.coachMessage(alert))
            } else {
                muteUntil = System.currentTimeMillis() + 4_000
                tts.say("Quick correction: ${alert.correction}")
            }
        }
    }

    /** Ends the part, waits briefly for the last transcript and returns everything that was said. */
    suspend fun stop(): Result {
        if (finished) return result()
        finished = true
        mic?.stop()
        mic = null
        player.flush()
        transcriber?.sendStreamEnd()
        delay(1_800)
        flushFallbackStudent()
        flushExaminer(interrupted = false)
        _state.value.interim.takeIf { it.isNotBlank() }?.let { addStudent(it) }
        shutdown()
        return result()
    }

    /** Immediate stop without waiting, e.g. when the session is cancelled. */
    fun abort() {
        if (finished) return
        finished = true
        mic?.stop()
        mic = null
        shutdown()
    }

    private fun shutdown() {
        checker?.stop()
        examiner?.close()
        transcriber?.close()
        player.stop()
        tts.shutdown()
        wav?.close()
        routing.exit()
        scope.cancel()
    }

    private fun result(): Result =
        Result(
            utterances = _state.value.utterances,
            liveErrors = _state.value.liveErrors,
            seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt(),
        )
}
