package de.abicoach.session

import de.abicoach.AbiCoachApp
import de.abicoach.ai.LiveEvent
import de.abicoach.ai.LiveSocket
import de.abicoach.ai.VoiceAgent
import de.abicoach.audio.AudioRouting
import de.abicoach.audio.AudioUtil
import de.abicoach.audio.FeedbackType
import de.abicoach.audio.MicStreamer
import de.abicoach.audio.PcmPlayer
import de.abicoach.audio.Tts
import de.abicoach.audio.WavWriter
import de.abicoach.data.FeedbackMode
import de.abicoach.data.LiveError
import de.abicoach.data.Speaker
import de.abicoach.data.Utterance
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
    /** Counts the hints, so the screen can tell a new hint from the old one. */
    val alertSeq: Int = 0,
    val alertAt: Long = 0,
    val elapsedSec: Int = 0,
    val targetSec: Int = 600,
    val micLevel: Float = 0f,
    val examinerSpeaking: Boolean = false,
    val examinerLabel: String = "",
    val hasExaminer: Boolean = false,
    val info: String? = null,
    val error: String? = null,
    /** One line about the live error check ("Gemini 3.8 Flash · 6 Prüfungen"), or the reason it is not working. */
    val checkInfo: String? = null,
    val checkFailed: Boolean = false,
)

/**
 * One live part of a session: the examiner (Gemini Live, optional), the verbatim transcriber
 * running in parallel, the live error check and the recording for the later pronunciation analysis.
 * While it runs, a foreground service keeps the microphone alive when the screen goes off.
 */
class LiveConversation(private val app: AbiCoachApp, private val config: Config) {
    data class Config(
        /** 1 = presentation, 2 = exam conversation, 0 = training. */
        val part: Int,
        /** Instructions for the examiner; null means no examiner (the presentation in part 1). */
        val examinerPrompt: String?,
        /** Instruction that makes the examiner start speaking. */
        val kickoff: String?,
        val feedback: FeedbackMode,
        val vocabulary: List<String>,
        val wavFile: File?,
        val checkerContext: String,
        val targetSeconds: Int,
    )

    data class Result(val utterances: List<Utterance>, val liveErrors: List<LiveError>, val seconds: Int, val examinerSeconds: Double, val examinerLabel: String)

    private val _state =
        MutableStateFlow(LiveUiState(part = config.part, targetSec = config.targetSeconds, hasExaminer = config.examinerPrompt != null))
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val routing = AudioRouting(app)
    private val player = PcmPlayer(24_000)
    private val tts = Tts(app, call = true)
    private var mic: MicStreamer? = null
    private var wav: WavWriter? = null

    @Volatile private var examiner: VoiceAgent? = null

    @Volatile private var transcriber: LiveSocket? = null
    private var checker: LiveChecker? = null

    private val lock = Any()
    private val examinerTurn = StringBuilder()
    private val fallbackStudent = StringBuilder()

    @Volatile private var transcriberWorks = false

    @Volatile private var kickoffSent = false

    @Volatile private var timeUpSent = false

    @Volatile private var lastSpeechAt = System.currentTimeMillis()

    @Volatile private var lastImpulseAt = 0L

    @Volatile private var muteUntil = 0L

    /** A correction the coach wants to say as soon as the student pauses. */
    @Volatile private var pendingCoach: LiveError? = null

    @Volatile private var pendingSince = 0L

    @Volatile private var lastCoachAt = 0L

    @Volatile private var lastBuzzAt = 0L

    /** The correction that was sent to the examiner; cleared as soon as he answers with his voice. */
    @Volatile private var coachSent: LiveError? = null

    @Volatile private var coachSentAt = 0L

    /** The examiner did not answer an app instruction: from now on the coach speaks with the phone's voice. */
    @Volatile private var coachViaPhone = false

    @Volatile private var lastWhisperAt = 0L

    @Volatile private var kickoffAt = 0L

    @Volatile private var kickoffRetried = false

    @Volatile private var examinerHeard = false

    /** Milliseconds of student speech that the transcriber has not answered with any text. */
    @Volatile private var voicedMs = 0L

    @Volatile private var lastRefreshAt = 0L

    /** When the transient message was set (0 = none or permanent); it fades after a few seconds. */
    @Volatile private var infoAt = 0L

    @Volatile private var finished = false
    private val startedAt = System.currentTimeMillis()

    /** Shows a prepared state without microphone or network (screenshots in the emulator test). */
    fun startDemo(demo: LiveUiState) {
        finished = true
        _state.value = demo.copy(part = config.part, targetSec = config.targetSeconds, hasExaminer = config.examinerPrompt != null)
    }

    fun start() {
        if (!app.ai.hasGemini) {
            _state.update { it.copy(error = "Kein Google-Schlüssel eingetragen (Einstellungen).") }
            return
        }
        LiveService.start(app)
        routing.enter()
        player.start()
        config.wavFile?.let { wav = WavWriter(it, 16_000) }

        transcriber = app.ai.transcriber(config.vocabulary, ::onTranscriberEvent).also { it.connect() }

        config.examinerPrompt?.let { prompt ->
            val agent = app.ai.examiner(prompt, ::onExaminerEvent)
            examiner = agent
            _state.update { it.copy(examinerLabel = agent.label) }
            agent.connect()
        }

        if (config.feedback != FeedbackMode.PRUEFUNG) {
            checker = LiveChecker(app.ai, scope, config.checkerContext, ::onLiveErrors, ::onCheckerStatus).also { it.start() }
        }

        mic = MicStreamer(::onMic).also { it.start(scope) }

        scope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    /** A short message above the transcript; it fades after a few seconds. */
    private fun info(text: String) {
        infoAt = System.currentTimeMillis()
        _state.update { it.copy(info = text) }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val seconds = ((now - startedAt) / 1000).toInt()
        _state.update { it.copy(elapsedSec = seconds, status = statusText(it)) }
        if (infoAt > 0 && now - infoAt > 12_000) {
            infoAt = 0
            _state.update { it.copy(info = null) }
        }
        // Part 1: like a real examiner, give a short impulse if the student has been silent for a while.
        if (config.part == 1 && seconds > 20 && now - lastSpeechAt > 25_000 && now - lastImpulseAt > 60_000) {
            lastImpulseAt = now
            muteUntil = now + 5_000
            tts.say(Prompts.IMPULSES.random())
        }
        // The transcriber heard speech for a long time but wrote nothing: its connection is probably dead.
        val ear = transcriber
        if (voicedMs > 12_000 && ear != null && ear.ready && !finished && now - lastRefreshAt > 60_000) {
            voicedMs = 0
            lastRefreshAt = now
            ear.reconnectNow()
        }
        // The examiner stayed silent after the start instruction: ask once more.
        if (kickoffSent && !examinerHeard && !kickoffRetried && now - kickoffAt > 8_000) {
            kickoffRetried = true
            config.kickoff?.let { examiner?.sendInstruction(it) }
        }
        deliverCoach(now)
        // The examiner ends the conversation politely when the time is up.
        if (examiner != null && !timeUpSent && seconds >= config.targetSeconds + 20) {
            timeUpSent = true
            examiner?.sendInstruction("TIME_UP")
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
        val headset = routing.usingHeadset
        // On the loudspeaker the examiner would hear (and interrupt) himself: send silence while he talks.
        // With headphones the real microphone signal goes through, so the student can cut in.
        examiner?.sendAudio(if (examinerAudible && !headset) ByteArray(pcm.size) else pcm)
        // While the examiner (or the app) is speaking over the loudspeaker, the microphone mostly hears the
        // loudspeaker: keep that out of the student's transcript and recording.
        if (!examinerAudible || headset) {
            transcriber?.sendAudio(pcm)
            wav?.write(pcm)
            if (level > 0.06f) {
                lastSpeechAt = now
                voicedMs += 100
            }
        }
        _state.update { it.copy(micLevel = level, examinerSpeaking = examinerAudible) }
    }

    private fun onTranscriberEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.Ready -> _state.update { it.copy(transcriberReady = true) }
            is LiveEvent.UserInterim -> {
                if (event.text.isNotBlank()) {
                    lastSpeechAt = System.currentTimeMillis()
                    voicedMs = 0
                }
                _state.update { it.copy(interim = event.text) }
                checker?.feedInterim(event.text)
            }
            is LiveEvent.UserText -> {
                transcriberWorks = true
                voicedMs = 0
                addStudent(event.text)
            }
            is LiveEvent.Failed -> {
                transcriber = null
                infoAt = 0
                _state.update {
                    it.copy(
                        transcriberReady = false,
                        info = "Wortgetreue Mitschrift nicht verfügbar (${event.message}). Die Auswertung nutzt die Aufnahme.",
                    )
                }
            }
            is LiveEvent.Info -> info(event.message)
            else -> Unit
        }
    }

    private fun onExaminerEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.Ready -> {
                _state.update { it.copy(examinerReady = true) }
                if (!kickoffSent) {
                    kickoffSent = true
                    kickoffAt = System.currentTimeMillis()
                    config.kickoff?.let { examiner?.sendInstruction(it) }
                }
            }
            is LiveEvent.Audio -> {
                examinerHeard = true
                coachSent = null
                flushFallbackStudent()
                player.enqueue(event.pcm)
            }
            is LiveEvent.ModelText -> {
                examinerHeard = true
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
            is LiveEvent.Info -> info(event.message)
            else -> Unit
        }
    }

    private fun addStudent(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        lastSpeechAt = System.currentTimeMillis()
        val utterance = Utterance(Speaker.STUDENT, clean, config.part, System.currentTimeMillis() - startedAt)
        _state.update { it.copy(utterances = it.utterances + utterance, interim = "") }
        checker?.feed(clean)
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

    private fun onCheckerStatus(s: CheckerStatus) {
        val ago = if (s.lastOkAt > 0) ((System.currentTimeMillis() - s.lastOkAt) / 1000).toInt() else -1
        val text =
            when {
                s.failure != null -> "Fehlerprüfung gestört (${s.failure}) – die Auswertung findet die Fehler trotzdem."
                s.checks == 0 -> "Fehlerprüfung bereit (${s.label}) – sie startet mit deinen ersten Sätzen."
                else -> "Fehlerprüfung aktiv (${s.label}) · ${s.checks} Prüfungen" + if (ago >= 0) " · zuletzt vor ${ago.coerceAtLeast(0)} s" else ""
            }
        _state.update { it.copy(checkInfo = text, checkFailed = s.failure != null) }
    }

    private fun onLiveErrors(errors: List<LiveError>) {
        _state.update { it.copy(liveErrors = it.liveErrors + errors) }
        if (config.feedback == FeedbackMode.PRUEFUNG || finished) return
        val counts = app.store.categoryCounts()
        val important = errors.filter { it.severity == "major" || it.severity == "content" || (counts[it.category] ?: 0) >= 3 }
        val alert = important.firstOrNull() ?: errors.firstOrNull()?.takeIf { config.feedback == FeedbackMode.FLUESTERN }
        if (alert == null) return
        val now = System.currentTimeMillis()
        _state.update { it.copy(lastAlert = alert, alertSeq = it.alertSeq + 1, alertAt = now) }
        if (now - lastBuzzAt > 3_000) {
            lastBuzzAt = now
            app.feedback.buzz(FeedbackType.ALERT)
        }
        whisperAloud(alert, now)
        // Only the conversation with an examiner is interrupted. In the presentation the coach only whispers;
        // the important mistakes are practised right after the talk.
        if (config.feedback == FeedbackMode.COACH && important.isNotEmpty() && config.examinerPrompt != null) {
            if (pendingCoach == null) pendingSince = now
            pendingCoach = important.first()
        }
    }

    /** With headphones the hint is also spoken very softly; on the loudspeaker it would disturb the microphone. */
    private fun whisperAloud(error: LiveError, now: Long) {
        if (!app.settings.whisperAudio || !routing.usingHeadset) return
        if (player.isPlaying(now) || now - lastWhisperAt < 5_000) return
        lastWhisperAt = now
        tts.whisper(error.correction)
    }

    /**
     * Coach mode in a conversation: the examiner says the correction himself as soon as the student pauses (or after
     * 6 seconds at the latest), never while he is talking and at most every 15 seconds. If he does not answer the
     * instruction within 6 seconds, the phone's voice says it and keeps doing so for this conversation.
     */
    private fun deliverCoach(now: Long) {
        val sent = coachSent
        if (sent != null && now - coachSentAt > 6_000) {
            coachSent = null
            coachViaPhone = true
            info("Der Prüfer hat nicht auf den Coach-Hinweis geantwortet – der Coach spricht jetzt mit der Handy-Stimme.")
            speakOnPhone(sent, now)
        }
        val error = pendingCoach ?: return
        if (finished || now - lastCoachAt < 15_000 || player.isPlaying(now) || now < muteUntil) return
        val studentPaused = now - lastSpeechAt > 700
        if (!studentPaused && now - pendingSince < 6_000) return
        pendingCoach = null
        lastCoachAt = now
        val agent = examiner
        if (agent != null && !coachViaPhone) {
            if (agent.sendInstruction(Prompts.coachMessage(error))) {
                coachSent = error
                coachSentAt = now
            } else {
                // The connection is just being renewed: try again in a second.
                pendingCoach = error
                lastCoachAt = 0
            }
        } else {
            speakOnPhone(error, now)
        }
    }

    private fun speakOnPhone(error: LiveError, now: Long) {
        val speech = Prompts.coachSpeech(error)
        muteUntil = now + 1_500 + speech.length * 70L
        tts.say(speech)
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
        LiveService.stop(app)
    }

    private fun result(): Result {
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        return Result(
            utterances = _state.value.utterances,
            liveErrors = _state.value.liveErrors,
            seconds = seconds,
            examinerSeconds = if (config.examinerPrompt != null) seconds.toDouble() else 0.0,
            examinerLabel = _state.value.examinerLabel,
        )
    }
}
