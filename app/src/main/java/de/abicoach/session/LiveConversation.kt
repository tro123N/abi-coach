package de.abicoach.session

import de.abicoach.AbiCoachApp
import de.abicoach.ai.LiveEvent
import de.abicoach.ai.LiveTranscriber
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
import de.abicoach.learn.ErrorMemoryStore
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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** What each part of the live chain is doing, shown as small status chips and saved as "Technik-Protokoll". */
data class LiveDiag(
    /** The microphone has heard something louder than silence. */
    val micHeard: Boolean = false,
    /** Utterances the ear cut out at pauses. */
    val segments: Int = 0,
    /** Utterances the ear understood. */
    val heard: Int = 0,
    val checks: Int = 0,
    val checkFailures: Int = 0,
    val checkMs: Int = 0,
    val checkMode: String = "",
    val hints: Int = 0,
    /** State of the verbatim live transcription: "startet", "läuft", "neu verbunden", "aus". */
    val transcript: String = "startet",
    val transcriptTexts: Int = 0,
    /** Utterances the transcript took from the ear because the live transcription missed them. */
    val earTranscripts: Int = 0,
    /** State of the examiner: "" (none), "verbindet", "bereit", "Fehler". */
    val examiner: String = "",
    val coachDue: Int = 0,
    val coachSpoken: Int = 0,
    val coachByPhone: Int = 0,
    val repeatsOk: Int = 0,
    /** Notes of the conversation director to the examiner (topic switch, follow-up questions, last minute …). */
    val director: Int = 0,
)

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
    /** The current hint is the mistake the student set out to avoid in this session (a session goal). */
    val alertGoal: Boolean = false,
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
    val diag: LiveDiag = LiveDiag(),
    /** The corrected words the coach asked the student to say again (null = nothing to repeat). */
    val repeatTarget: String? = null,
    val repeatOk: Boolean = false,
    val repeatAt: Long = 0,
    /** What the app itself said aloud (coach corrections, impulses in the presentation), for the transcript panel. */
    val appSaid: List<AppLine> = emptyList(),
)

/** One sentence the app said aloud, [atMs] after the start of the part. */
data class AppLine(val text: String, val atMs: Long)

/**
 * One live part of a session: the examiner (Gemini Live, optional), the verbatim transcriber running in parallel,
 * the ear (own pause detection + Gemini listening to every utterance) for Flüstern and Coach, the coach's voice and
 * the recording for the later pronunciation analysis. While it runs, a foreground service keeps the microphone alive.
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
        /** The app steers the examiner like an exam director: time, topic switch, follow-up questions. */
        val directed: Boolean = false,
        /**
         * The further topics of the conversation, in order. With one the examiner switches at about half of the time,
         * with two after about a third and two thirds – like part 2 of the Abitur, which links several topic fields.
         */
        val switchTopics: List<String> = emptyList(),
        /** Practice only: structures the examiner should make the student use (from the error memory). */
        val elicit: List<String> = emptyList(),
        /** After this many seconds of uninterrupted talking the examiner cuts in (0 = never, e.g. in a sprint). */
        val monologueSec: Int = 100,
        /** The error pattern (key in the error memory) that is a goal of this session: it gets priority in hints. */
        val goalPattern: String = "",
        /** How the examiner leads: the director gives a responsive examiner more chances to follow the student's ideas. */
        val talkStyle: de.abicoach.data.TalkStyle = de.abicoach.data.TalkStyle.AUSGEWOGEN,
        /** The first topic of the conversation (English), the aim of the director's notes until the first switch. */
        val mainTopic: String = "",
    )

    data class Result(
        val utterances: List<Utterance>,
        val liveErrors: List<LiveError>,
        val seconds: Int,
        val examinerSeconds: Double,
        val examinerLabel: String,
        /** Technical summary and log of this part. */
        val tech: String = "",
    )

    private val _state =
        MutableStateFlow(
            LiveUiState(
                part = config.part,
                targetSec = config.targetSeconds,
                hasExaminer = config.examinerPrompt != null,
                diag = LiveDiag(examiner = if (config.examinerPrompt != null) "verbindet" else ""),
            ),
        )
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val routing = AudioRouting(app)
    private val player = PcmPlayer(24_000)
    private val tts = Tts(app, call = true)
    private var mic: MicStreamer? = null
    private var wav: WavWriter? = null

    @Volatile private var examiner: VoiceAgent? = null

    @Volatile private var transcriber: LiveTranscriber? = null
    private var checker: LiveChecker? = null

    /** The ear's own pause detection; only with Flüstern or Coach. */
    private val segmenter: SpeechSegmenter? = if (config.feedback != FeedbackMode.PRUEFUNG) SpeechSegmenter(::onSegment) else null

    private val lock = Any()
    private val examinerTurn = StringBuilder()
    private val fallbackStudent = StringBuilder()
    private val sessionRules = HashMap<String, Int>()
    private val techLog = ArrayList<String>()

    @Volatile private var transcriberWorks = false

    @Volatile private var lastTranscriberTextAt = 0L

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

    /** The coach asked the student to say this correction again. */
    @Volatile private var repeatTarget: LiveError? = null

    @Volatile private var repeatSince = 0L

    @Volatile private var repeatTries = 0

    @Volatile private var lastWhisperAt = 0L

    @Volatile private var kickoffAt = 0L

    @Volatile private var kickoffRetried = false

    @Volatile private var examinerHeard = false

    /** Milliseconds of student speech that the transcriber has not answered with any text. */
    @Volatile private var voicedMs = 0L

    @Volatile private var lastRefreshAt = 0L

    @Volatile private var micWarned = false

    @Volatile private var maxLevel = 0f

    /** When the transient message was set (0 = none or permanent); it fades after a few seconds. */
    @Volatile private var infoAt = 0L

    @Volatile private var finished = false
    private val startedAt = System.currentTimeMillis()

    // ---------- conversation director ----------

    /** When the student's current answer began (0 = the examiner has the floor). */
    @Volatile private var answerStartAt = 0L

    /** Lengths of the student's last answers in ms, newest last. */
    private val answers = ArrayDeque<Long>()

    @Volatile private var examinerTalking = false

    @Volatile private var lastExaminerAt = 0L

    @Volatile private var lastDirectorAt = 0L

    /** A note waits for the examiner's next turn; if he stays silent after the student spoke, he gets a nudge. */
    @Volatile private var noteAt = 0L

    @Volatile private var noteWatch = false

    /** The examiner did not react to a note: no more notes in this part (only the monologue cut-in). */
    @Volatile private var notesBlocked = false

    /** How many topic switches the director has asked for. */
    @Volatile private var switchesSent = 0

    @Volatile private var finalSent = false

    @Volatile private var lastProbeAt = 0L

    @Volatile private var lastMonologueAt = 0L

    @Volatile private var nextElicitAt = 60_000L

    @Volatile private var nextFollowAt = 75_000L

    @Volatile private var elicitIndex = 0

    /** Shows a prepared state without microphone or network (screenshots in the emulator test). */
    fun startDemo(demo: LiveUiState) {
        finished = true
        _state.value = demo.copy(part = config.part, targetSec = config.targetSeconds, hasExaminer = config.examinerPrompt != null)
    }

    fun start() {
        if (!app.ai.ready) {
            _state.update { it.copy(error = "Kein ${app.ai.provider.company}-Schlüssel eingetragen (Einstellungen → KI).") }
            return
        }
        if (app.ai.provider == de.abicoach.ai.Provider.ANTHROPIC && !app.ai.phone.canRecognize) {
            _state.update {
                it.copy(error = "Mit Claude hört die Spracherkennung des Handys zu – dafür braucht es Android 13 oder neuer. Wähle in den Einstellungen Google oder OpenAI.")
            }
            return
        }
        LiveService.start(app)
        routing.enter()
        log("Start: ${partLabel()}, Rückmeldung ${config.feedback.label}, " + if (routing.usingHeadset) "Kopfhörer" else "Lautsprecher")
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
            checker =
                LiveChecker(app.ai, scope, config.checkerContext, ::onLiveErrors, ::onCheckerStatus, ::onHeard, listen = app.ai.provider != de.abicoach.ai.Provider.ANTHROPIC)
                    .also { it.start() }
        }

        mic = MicStreamer(::onMic).also { it.start(scope) }

        scope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
        // The coach reacts within a tenth of a second once the student takes a breath.
        if (config.feedback == FeedbackMode.COACH) {
            scope.launch {
                while (isActive) {
                    delay(100)
                    deliverCoach(System.currentTimeMillis())
                }
            }
        }
    }

    private fun partLabel(): String =
        when (config.part) {
            1 -> "Teil 1 (Vortrag)"
            2 -> "Teil 2 (Gespräch)"
            else -> "Gesprächstraining"
        }

    private fun log(text: String) {
        val s = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(0)
        synchronized(techLog) {
            techLog += "%d:%02d %s".format(s / 60, s % 60, text.take(160))
            if (techLog.size > 120) techLog.removeAt(0)
        }
    }

    private fun diag(change: (LiveDiag) -> LiveDiag) = _state.update { it.copy(diag = change(it.diag)) }

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
        // Nothing louder than silence for 20 seconds: the microphone is probably muted or blocked.
        if (!micWarned && !finished && seconds >= 20 && maxLevel < 0.03f) {
            micWarned = true
            log("Mikrofon hört nichts (höchster Pegel ${"%.3f".format(maxLevel)})")
            info("Das Mikrofon hört kaum etwas. Ist es stummgeschaltet oder verdeckt? Sprich ruhig etwas lauter.")
        }
        // Part 1: like a real examiner, give a short impulse if the student has been silent for a while.
        if (config.part == 1 && seconds > 20 && now - lastSpeechAt > 25_000 && now - lastImpulseAt > 60_000) {
            lastImpulseAt = now
            muteUntil = now + 5_000
            val impulse = Prompts.IMPULSES.random()
            said(impulse, now)
            tts.say(impulse)
        }
        // The transcriber heard speech for a long time but wrote nothing: its connection is probably dead.
        val ear = transcriber
        if (voicedMs > 12_000 && ear != null && ear.ready && !finished && now - lastRefreshAt > 60_000) {
            voicedMs = 0
            lastRefreshAt = now
            log("Mitschrift schweigt trotz Sprache – verbinde neu")
            diag { it.copy(transcript = "neu verbunden") }
            ear.reconnectNow()
        }
        // The examiner stayed silent after the start instruction: ask once more.
        if (kickoffSent && !examinerHeard && !kickoffRetried && now - kickoffAt > 8_000) {
            kickoffRetried = true
            log("Prüfer schweigt nach dem Start – zweite Startanweisung")
            config.kickoff?.let { examiner?.sendInstruction(it) }
        }
        // The examiner ends the conversation politely when the time is up.
        if (examiner != null && !timeUpSent && seconds >= config.targetSeconds + 20) {
            timeUpSent = true
            examiner?.sendInstruction("TIME_UP")
        }
        // A turn of the examiner that never got its end (e.g. the connection was renewed): he is silent again.
        if (examinerTalking && now - lastExaminerAt > 8_000 && !player.isPlaying(now)) examinerTalking = false
        if (config.directed && !timeUpSent) direct(now)
    }

    /**
     * The conversation director: like the chair of an exam committee it keeps an eye on the time and on the answers and
     * gives the examiner short notes – switch to the second topic at about half of the time, dig deeper after several
     * short answers, make the student use the structures of his error patterns (practice only), one last evaluative
     * question in the final minute. A note is sent only while the examiner is silent and never close to a coach
     * correction; it shapes his next turn without interrupting anyone. Only a very long monologue makes him cut in.
     */
    private fun direct(now: Long) {
        val agent = examiner ?: return
        if (finished || !kickoffSent || !_state.value.examinerReady) return
        val voiceAt = maxOf(segmenter?.lastVoiceAt ?: 0L, lastSpeechAt)
        // A note the examiner did not react to: he may be waiting for more input. Nudge him once and stop the notes.
        if (noteWatch) {
            if (lastExaminerAt > noteAt) {
                noteWatch = false
            } else if (voiceAt > noteAt && now - voiceAt > 6_000 && !player.isPlaying(now)) {
                noteWatch = false
                notesBlocked = true
                lastDirectorAt = now
                log("Regie: Prüfer reagiert nach der Notiz nicht – Anstoß, keine weiteren Notizen")
                agent.sendInstruction(Prompts.DIRECTOR_NUDGE)
                return
            }
        }
        val busy = examinerTalking || player.isPlaying(now) || now < muteUntil || pendingCoach != null || coachSent != null || repeatTarget != null
        if (busy || now - lastCoachAt < 10_000 || now - lastDirectorAt < 10_000) return
        // The student has been talking for a very long time without letting the examiner in: a real examiner cuts in.
        val answer = answerStartAt
        if (config.monologueSec > 0 && answer > 0 && now - answer > config.monologueSec * 1000L && now - voiceAt < 2_000 && now - lastMonologueAt > 90_000) {
            lastMonologueAt = now
            lastDirectorAt = now
            if (agent.sendInstruction(Prompts.DIRECTOR_MONOLOGUE)) {
                diag { it.copy(director = it.director + 1) }
                log("Regie: sehr langer Monolog – der Prüfer hakt ein")
            }
            return
        }
        if (notesBlocked) return
        val note = nextNote(now, now - startedAt) ?: return
        if (agent.sendNote("DIRECTOR: ${note.second}")) {
            lastDirectorAt = now
            noteAt = now
            noteWatch = true
            diag { it.copy(director = it.director + 1) }
            log("Regie: ${note.first}")
        }
    }

    /** The most important note right now: a label for the log and the English instruction. */
    private fun nextNote(now: Long, elapsed: Long): Pair<String, String>? {
        val target = config.targetSeconds * 1000L
        val switches = config.switchTopics
        // A responsive examiner stays a little longer with the student's ideas before he moves on.
        val pace = if (config.talkStyle == de.abicoach.data.TalkStyle.EINGEHEN) 11 else 9
        if (switchesSent < switches.size && elapsed >= target * pace * (switchesSent + 1) / (10 * (switches.size + 1))) {
            val topic = switches[switchesSent]
            switchesSent++
            return "Themenwechsel ${switchesSent}/${switches.size}: ${topic.take(40)}" to Prompts.directorSwitch(topic, last = switchesSent == switches.size && switches.size > 1)
        }
        if (!finalSent && target >= 240_000 && elapsed >= target - 60_000) {
            finalSent = true
            return "letzte Minute – eine wertende Schlussfrage" to Prompts.DIRECTOR_FINAL
        }
        val brief = synchronized(lock) { answers.size >= 3 && answers.takeLast(3).all { it < 12_000 } }
        if (brief && now - lastProbeAt > 90_000) {
            lastProbeAt = now
            synchronized(lock) { answers.clear() }
            return "kurze Antworten – der Prüfer fragt tiefer nach" to Prompts.DIRECTOR_PROBE
        }
        if (config.talkStyle == de.abicoach.data.TalkStyle.EINGEHEN && elapsed >= nextFollowAt && elapsed < target - 60_000) {
            nextFollowAt = elapsed + 110_000
            val topic = switches.getOrNull(switchesSent - 1) ?: config.mainTopic
            return "auf den Gedanken des Schülers eingehen und zum Thema lenken" to Prompts.directorFollow(topic.ifBlank { "the topic of this conversation" })
        }
        if (config.elicit.isNotEmpty() && elapsed >= nextElicitAt && elapsed < target - 75_000) {
            nextElicitAt = elapsed + 150_000
            val line = config.elicit[elicitIndex % config.elicit.size]
            elicitIndex++
            return "Fehlermuster gezielt abfragen: ${line.take(50)}" to Prompts.directorElicit(line)
        }
        return null
    }

    /** The examiner starts or continues a turn: the student's answer before it is over. */
    private fun examinerOutput(now: Long) {
        lastExaminerAt = now
        if (examinerTalking) return
        examinerTalking = true
        val start = answerStartAt
        answerStartAt = 0L
        if (start <= 0L) return
        val voiceAt = maxOf(segmenter?.lastVoiceAt ?: 0L, lastSpeechAt)
        val length = voiceAt - start
        if (length >= 1_500) {
            synchronized(lock) {
                answers.addLast(length)
                while (answers.size > 6) answers.removeFirst()
            }
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
        if (level > maxLevel) maxLevel = level
        val examinerAudible = player.isPlaying(now) || now < muteUntil
        val headset = routing.usingHeadset
        // On the loudspeaker the examiner would hear (and interrupt) himself: send silence while he talks.
        // With headphones the real microphone signal goes through, so the student can cut in.
        examiner?.sendAudio(if (examinerAudible && !headset) ByteArray(pcm.size) else pcm)
        // While the examiner (or the coach) is speaking over the loudspeaker, the microphone mostly hears the
        // loudspeaker: keep that out of the student's transcript, the ear and the recording.
        if (!examinerAudible || headset) {
            transcriber?.sendAudio(pcm)
            wav?.write(pcm)
            segmenter?.feed(pcm, now)
            if (level > 0.06f) {
                lastSpeechAt = now
                voicedMs += 100
                // The student starts answering (for the director: how long his answers are).
                if (answerStartAt == 0L && !examinerTalking && !examinerAudible) answerStartAt = now
            }
        } else {
            segmenter?.flush(now)
        }
        val heard = level > 0.05f
        _state.update { s ->
            if (heard && !s.diag.micHeard) {
                s.copy(micLevel = level, examinerSpeaking = examinerAudible, diag = s.diag.copy(micHeard = true))
            } else {
                s.copy(micLevel = level, examinerSpeaking = examinerAudible)
            }
        }
    }

    private fun onSegment(segment: SpeechSegment) {
        checker?.feedSegment(segment)
        diag { it.copy(segments = it.segments + 1) }
    }

    private fun onTranscriberEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.Ready -> {
                _state.update { it.copy(transcriberReady = true, diag = it.diag.copy(transcript = "läuft")) }
                log("Mitschrift verbunden")
            }
            is LiveEvent.UserInterim -> {
                if (event.text.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    lastSpeechAt = now
                    lastTranscriberTextAt = now
                    voicedMs = 0
                }
                _state.update { it.copy(interim = event.text) }
                checker?.feedInterim(event.text)
            }
            is LiveEvent.UserText -> {
                transcriberWorks = true
                lastTranscriberTextAt = System.currentTimeMillis()
                voicedMs = 0
                addStudent(event.text)
            }
            is LiveEvent.Failed -> {
                transcriber = null
                infoAt = 0
                log("Mitschrift ausgefallen: ${event.message}")
                _state.update {
                    it.copy(
                        transcriberReady = false,
                        info =
                            if (segmenter != null) {
                                "Die Live-Mitschrift ist ausgefallen (${event.message}). Das Ohr hört weiter zu und schreibt mit."
                            } else {
                                "Wortgetreue Mitschrift nicht verfügbar (${event.message}). Die Auswertung nutzt die Aufnahme."
                            },
                        diag = it.diag.copy(transcript = "aus"),
                    )
                }
            }
            is LiveEvent.Info -> {
                log(event.message)
                info(event.message)
            }
            else -> Unit
        }
    }

    private fun onExaminerEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.Ready -> {
                _state.update { it.copy(examinerReady = true, diag = it.diag.copy(examiner = "bereit")) }
                log("Prüfer verbunden")
                if (!kickoffSent) {
                    kickoffSent = true
                    kickoffAt = System.currentTimeMillis()
                    config.kickoff?.let { examiner?.sendInstruction(it) }
                }
            }
            is LiveEvent.Audio -> {
                examinerHeard = true
                examinerOutput(System.currentTimeMillis())
                val sent = coachSent
                if (sent != null) {
                    coachSent = null
                    log("Prüfer sagt die Korrektur (nach ${System.currentTimeMillis() - coachSentAt} ms)")
                    diag { it.copy(coachSpoken = it.coachSpoken + 1) }
                }
                flushFallbackStudent()
                player.enqueue(event.pcm)
            }
            is LiveEvent.ModelText -> {
                examinerHeard = true
                examinerOutput(System.currentTimeMillis())
                flushFallbackStudent()
                val text = synchronized(lock) { examinerTurn.append(event.text).toString() }
                _state.update { it.copy(examinerLive = text) }
            }
            is LiveEvent.UserText -> {
                // The examiner's own (non-verbatim) transcript is only a last resort, when neither the
                // transcriber nor the ear can write along.
                if (!transcriberWorks && segmenter == null) synchronized(lock) { fallbackStudent.append(event.text) }
            }
            is LiveEvent.TurnComplete -> {
                examinerTalking = false
                flushExaminer(interrupted = false)
            }
            is LiveEvent.Interrupted -> {
                examinerTalking = false
                player.flush()
                flushExaminer(interrupted = true)
            }
            is LiveEvent.Failed -> {
                log("Prüfer: ${event.message}")
                _state.update { it.copy(error = event.message, diag = it.diag.copy(examiner = "Fehler")) }
            }
            is LiveEvent.Info -> {
                log(event.message)
                info(event.message)
            }
            else -> Unit
        }
    }

    private fun addStudent(text: String, fromEar: Boolean = false) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        lastSpeechAt = now
        val utterance = Utterance(Speaker.STUDENT, clean, config.part, now - startedAt)
        _state.update {
            it.copy(
                utterances = it.utterances + utterance,
                interim = if (fromEar) it.interim else "",
                diag = if (fromEar) it.diag.copy(earTranscripts = it.diag.earTranscripts + 1) else it.diag.copy(transcriptTexts = it.diag.transcriptTexts + 1),
            )
        }
        if (!fromEar) {
            checker?.feed(clean)
            checkRepeat(clean, countTry = false)
        }
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

    /** Uses the examiner's own (non-verbatim) transcript only if nothing better is writing along. */
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

    /** The ear understood one utterance. */
    private fun onHeard(segment: SpeechSegment, heard: String) {
        diag { it.copy(heard = it.heard + 1) }
        // The live transcription missed this utterance (or is broken): keep the ear's version in the transcript.
        if (!finished && (transcriber == null || !transcriberWorks || lastTranscriberTextAt < segment.startMs)) {
            addStudent(heard, fromEar = true)
        }
        if (segment.startMs >= repeatSince) checkRepeat(heard, countTry = true)
    }

    private fun onCheckerStatus(s: CheckerStatus) {
        val ago = if (s.lastOkAt > 0) ((System.currentTimeMillis() - s.lastOkAt) / 1000).toInt() else -1
        val how = if (s.mode == "Audio") "hört zu" else "liest die Mitschrift"
        val text =
            when {
                s.failure != null -> "Fehlerprüfung gestört (${s.failure}) – die Auswertung findet die Fehler trotzdem."
                s.checks == 0 -> "Fehlerprüfung bereit (${s.label}, $how) – sie startet mit deinen ersten Sätzen."
                else -> "Fehlerprüfung aktiv (${s.label}, $how) · ${s.checks} geprüft · Ø ${"%.1f".format(s.avgMs / 1000.0)} s" + if (ago >= 0) " · zuletzt vor ${ago.coerceAtLeast(0)} s" else ""
            }
        if (s.failure != null) log("Fehlerprüfung: ${s.failure}")
        _state.update {
            it.copy(
                checkInfo = text,
                checkFailed = s.failure != null,
                diag = it.diag.copy(checks = s.checks, checkFailures = s.failures, checkMs = s.avgMs, checkMode = s.mode),
            )
        }
    }

    private fun ruleKey(e: LiveError): String = ErrorMemoryStore.keyOf(e.category, e.rule)

    /** The mistake belongs to the error pattern the student set out to avoid in this session. */
    private fun isGoal(e: LiveError): Boolean {
        val goal = config.goalPattern
        if (goal.isBlank()) return false
        return ruleKey(e) == goal || app.errorMemory.matching(e)?.key == goal
    }

    /** Worth an interruption: a serious mistake, one of the student's known patterns, or one that keeps coming back today. */
    private fun coachWorthy(e: LiveError, now: Long): Boolean {
        if (e.severity == "major" || e.severity == "content" || isGoal(e)) return true
        if (app.errorMemory.isActive(e, now)) return true
        return (synchronized(lock) { sessionRules[ruleKey(e)] } ?: 0) >= 2
    }

    private fun onLiveErrors(errors: List<LiveError>) {
        _state.update { it.copy(liveErrors = it.liveErrors + errors) }
        if (config.feedback == FeedbackMode.PRUEFUNG || finished || errors.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(lock) { errors.forEach { e -> val k = ruleKey(e); sessionRules[k] = (sessionRules[k] ?: 0) + 1 } }
        val worthy = errors.filter { coachWorthy(it, now) }
        // The mistake of the session goal comes first: that is what the student is working on today.
        val goal = errors.firstOrNull { isGoal(it) }
        val alert = goal ?: worthy.firstOrNull() ?: errors.first()
        _state.update { it.copy(lastAlert = alert, alertGoal = goal != null, alertSeq = it.alertSeq + 1, alertAt = now, diag = it.diag.copy(hints = it.diag.hints + errors.size)) }
        log("Hinweis: „${alert.quote}“ → „${alert.correction}“" + if (alert.rule.isNotBlank()) " (${alert.rule})" else "")
        if (now - lastBuzzAt > 3_000) {
            lastBuzzAt = now
            app.feedback.buzz(FeedbackType.ALERT)
        }
        if (config.feedback == FeedbackMode.FLUESTERN) whisperAloud(alert, now)
        if (config.feedback == FeedbackMode.COACH && worthy.isNotEmpty()) {
            val pick = goal ?: worthy.minByOrNull { severityRank(it.severity) } ?: worthy.first()
            if (pendingCoach == null) pendingSince = now
            pendingCoach = pick
            // The coach's sentence is made right now, while the student goes on: at the next breath it plays at once.
            app.ai.prefetchSpeech(Prompts.coachSpeech(pick))
            diag { it.copy(coachDue = it.coachDue + 1) }
        }
    }

    private fun severityRank(severity: String): Int =
        when (severity) {
            "major" -> 0
            "content" -> 1
            else -> 2
        }

    /** With headphones the hint is also spoken very softly; on the loudspeaker it would disturb the microphone. */
    private fun whisperAloud(error: LiveError, now: Long) {
        if (!app.settings.whisperAudio || !routing.usingHeadset) return
        if (player.isPlaying(now) || now - lastWhisperAt < 5_000) return
        lastWhisperAt = now
        tts.whisper(error.correction)
    }

    /**
     * Coach mode, in every part: the correction comes right away – at the student's next breath (a third of a second
     * without voice), after 2.5 seconds at the latest, and at most every 6 seconds. The coach speaks with its own
     * voice (made ahead while the student goes on, so it plays at once); the examiner gets a note, so he does not
     * repeat it and waits for the corrected sentence. Then the coach waits for the student to say it again.
     */
    private fun deliverCoach(now: Long) {
        if (finished) return
        val error = pendingCoach ?: return
        if (now - pendingSince > 20_000) {
            pendingCoach = null
            log("Coach-Hinweis verworfen (der Moment ist vorbei)")
            return
        }
        if (now - lastCoachAt < COACH_GAP_MS || examinerTalking || player.isPlaying(now) || now < muteUntil) return
        val voiceAt = maxOf(segmenter?.lastVoiceAt ?: 0L, lastSpeechAt)
        val paused = now - voiceAt > COACH_PAUSE_MS
        if (!paused && now - pendingSince < COACH_MAX_WAIT_MS) return
        pendingCoach = null
        lastCoachAt = now
        speakCoach(error, now)
        examiner?.sendNote(Prompts.coachNote(error))
        log("Coach unterbricht: „${error.quote}“ → „${error.correction}“ (${now - pendingSince} ms nach dem Fund)")
        expectRepeat(error, now)
    }

    /** The coach's own voice: the AI's voice (usually made ahead already), Android's voice if it is not there within a second. */
    private fun speakCoach(error: LiveError, now: Long) {
        val line = Prompts.coachSpeech(error)
        muteUntil = now + COACH_VOICE_MS + 300
        diag { it.copy(coachSpoken = it.coachSpoken + 1, coachByPhone = it.coachByPhone + 1) }
        said(line, now)
        log("Coach spricht: $line")
        scope.launch {
            // 0 = not decided yet, 1 = the AI's voice plays, 2 = the phone's voice took over.
            val voice = java.util.concurrent.atomic.AtomicInteger(0)
            val first = kotlinx.coroutines.CompletableDeferred<Boolean>()
            launch {
                val pcm =
                    app.ai.speakStream(line, holdMs = 700) { piece ->
                        if (finished || !(voice.compareAndSet(0, 1) || voice.get() == 1)) return@speakStream
                        player.enqueue(piece)
                        muteUntil = System.currentTimeMillis() + 300
                        first.complete(true)
                    }
                if (pcm == null) first.complete(false)
            }
            val ok = withTimeoutOrNull(COACH_VOICE_MS) { first.await() } ?: false
            if (finished || ok) return@launch
            if (voice.compareAndSet(0, 2)) {
                muteUntil = System.currentTimeMillis() + 1_200 + line.length * 70L
                tts.say(line)
            }
        }
    }

    /** Keeps what the app said aloud for the transcript panel (the student's record stays his own words and the examiner's). */
    private fun said(text: String, now: Long) {
        _state.update { it.copy(appSaid = (it.appSaid + AppLine(text, now - startedAt)).takeLast(40)) }
    }

    private fun expectRepeat(error: LiveError, now: Long) {
        repeatTarget = error
        repeatSince = now
        repeatTries = 0
        _state.update { it.copy(repeatTarget = error.correction, repeatOk = false, repeatAt = now) }
    }

    /** Did the student say the correction again? A tick on the screen and a short vibration when he did. */
    private fun checkRepeat(heard: String, countTry: Boolean) {
        val target = repeatTarget ?: return
        val now = System.currentTimeMillis()
        if (now - repeatSince < 600) return
        if (matchesCorrection(heard, target)) {
            repeatTarget = null
            _state.update { it.copy(repeatOk = true, repeatAt = now, diag = it.diag.copy(repeatsOk = it.diag.repeatsOk + 1)) }
            app.feedback.buzz(FeedbackType.CORRECT)
            log("Richtig wiederholt: „${target.correction}“")
            return
        }
        if (countTry) repeatTries++
        if (repeatTries >= 2 || now - repeatSince > 30_000) {
            repeatTarget = null
            _state.update { it.copy(repeatTarget = null) }
        }
    }

    private fun matchesCorrection(heard: String, e: LiveError): Boolean {
        val h = Prompts.normalize(heard)
        val c = Prompts.normalize(e.correction)
        if (c.isBlank() || h.isBlank()) return false
        if (h.contains(c)) return true
        val needed = c.split(" ").filter { it.length > 1 }.toSet()
        if (needed.isEmpty()) return false
        val said = h.split(" ").toSet()
        return needed.count { it in said } >= maxOf(1, (needed.size * 0.8).toInt())
    }

    /** Ends the part, waits briefly for the last transcript and returns everything that was said. */
    suspend fun stop(): Result {
        if (finished) return result()
        segmenter?.flush(System.currentTimeMillis())
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

    private fun techText(seconds: Int): String {
        val d = _state.value.diag
        val sb = StringBuilder()
        sb.append("== ").append(partLabel()).append(" · ").append(seconds / 60).append(":").append("%02d".format(seconds % 60)).append(" Min. · ")
            .append(config.feedback.label).append(" ==\n")
        sb.append("Mikrofon: ").append(if (d.micHeard) "hört dich" else "kaum Pegel").append(" (höchster Pegel ").append("%.2f".format(maxLevel)).append(")\n")
        sb.append("Live-Mitschrift: ").append(d.transcript).append(", ").append(d.transcriptTexts).append(" Sätze")
        if (d.earTranscripts > 0) sb.append(", ").append(d.earTranscripts).append(" Sätze vom Ohr ergänzt")
        sb.append('\n')
        if (segmenter != null) {
            sb.append("Ohr: ").append(d.segments).append(" Äußerungen, ").append(d.heard).append(" verstanden, ")
                .append(d.checks).append(" geprüft (").append(d.checkMode.ifBlank { "–" }).append(", Ø ").append("%.1f".format(d.checkMs / 1000.0)).append(" s), ")
                .append(d.checkFailures).append(" Störungen, ").append(d.hints).append(" Hinweise\n")
        }
        if (config.feedback == FeedbackMode.COACH) {
            sb.append("Coach: ").append(d.coachDue).append(" wichtige Fehler, ").append(d.coachSpoken).append(" gesagt")
            if (d.coachByPhone > 0) sb.append(" (").append(d.coachByPhone).append(" mit Handy-Stimme)")
            sb.append(", ").append(d.repeatsOk).append(" richtig wiederholt\n")
        }
        if (config.examinerPrompt != null) sb.append("Prüfer: ").append(d.examiner.ifBlank { "–" }).append(" (").append(_state.value.examinerLabel).append(")\n")
        if (config.directed && config.examinerPrompt != null) {
            sb.append("Gesprächsregie: ").append(d.director).append(" Hinweise an den Prüfer")
            if (notesBlocked) sb.append(" (Prüfer reagierte nicht auf Notizen – danach nur noch Einhaken bei Monologen)")
            sb.append('\n')
        }
        synchronized(techLog) { techLog.forEach { sb.append(it).append('\n') } }
        return sb.toString().trim()
    }

    private fun result(): Result {
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        return Result(
            utterances = _state.value.utterances,
            liveErrors = _state.value.liveErrors,
            seconds = seconds,
            examinerSeconds = if (config.examinerPrompt != null) seconds.toDouble() else 0.0,
            examinerLabel = _state.value.examinerLabel,
            tech = if (_state.value.elapsedSec == 0 && techLog.isEmpty()) "" else techText(seconds),
        )
    }
}

/** At most one coach interruption in this time. */
private const val COACH_GAP_MS = 6_000L

/** A breath: this long without voice and the coach speaks. */
private const val COACH_PAUSE_MS = 350L

/** The coach does not wait longer for a breath than this. */
private const val COACH_MAX_WAIT_MS = 2_500L

/** How long the AI's voice may take before the phone's voice speaks the correction. */
private const val COACH_VOICE_MS = 1_200L
