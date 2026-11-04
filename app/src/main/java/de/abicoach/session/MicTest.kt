package de.abicoach.session

import de.abicoach.AbiCoachApp
import de.abicoach.audio.AudioRouting
import de.abicoach.audio.MicStreamer
import de.abicoach.audio.PcmPlayer
import de.abicoach.audio.Tts
import de.abicoach.data.LiveError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tests the whole Flüstern/Coach chain with the real phone: microphone (call mode, echo cancellation) → own pause
 * detection → Gemini listening to the utterance → hint → the coach's voice through the loudspeaker. Exactly the parts
 * a live session uses, so a failing step shows where the problem is.
 */
class MicTest(private val app: AbiCoachApp) {
    enum class Phase { LISTENING, CHECKING, SPEAKING, DONE, FAILED }

    data class State(
        val phase: Phase = Phase.LISTENING,
        val level: Float = 0f,
        val maxLevel: Float = 0f,
        val seconds: Int = 0,
        val transcript: String = "",
        val errors: List<LiveError> = emptyList(),
        val voice: String = "",
        val ms: Long = 0,
        val message: String = "Sag jetzt laut und deutlich: „Yesterday I goed to the cinema and my friends was there.“",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val routing = AudioRouting(app)
    private val player = PcmPlayer(24_000)
    private val tts = Tts(app, call = true)
    private var mic: MicStreamer? = null

    @Volatile private var segment: SpeechSegment? = null

    @Volatile private var stopped = false
    private val segmenter = SpeechSegmenter { s -> if (segment == null && s.durationMs >= 1_200) segment = s }

    fun start() {
        routing.enter()
        player.start()
        mic =
            MicStreamer { pcm ->
                val now = System.currentTimeMillis()
                segmenter.feed(pcm, now)
                _state.update { it.copy(level = segmenter.level, maxLevel = segmenter.maxLevel) }
            }.also { it.start(scope) }
        scope.launch {
            val started = System.currentTimeMillis()
            // Wait for one complete utterance (cut at the pause after it), at most 15 seconds.
            while (segment == null && System.currentTimeMillis() - started < 15_000 && !stopped) {
                delay(200)
                _state.update { it.copy(seconds = ((System.currentTimeMillis() - started) / 1000).toInt()) }
            }
            if (segment == null) segmenter.flush(System.currentTimeMillis())
            mic?.stop()
            mic = null
            val heard = segment
            if (heard == null) {
                val max = _state.value.maxLevel
                fail(
                    if (max < 0.05f) {
                        "Das Mikrofon hört so gut wie nichts (Pegel ${"%.2f".format(max)}). Erlaube AbiCoach das Mikrofon, nimm Hüllen oder Finger vom Mikrofon und teste noch einmal."
                    } else {
                        "Ich habe Geräusche gehört, aber keinen ganzen Satz mit Pause danach. Sprich den Satz zusammenhängend und mach dann eine kurze Pause."
                    },
                )
                return@launch
            }
            _state.update { it.copy(phase = Phase.CHECKING, message = "Gemini hört sich deinen Satz an …") }
            val result =
                try {
                    EarCheck.check(app.ai, heard, "microphone test", "")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail("Das Zuhören hat nicht geklappt: ${e.message}")
                    return@launch
                }
            if (result.transcript.isBlank()) {
                fail("Gemini hat keine Sprache erkannt. Sprich etwas lauter und näher am Handy.")
                return@launch
            }
            _state.update { it.copy(transcript = result.transcript, errors = result.errors, ms = result.ms, phase = Phase.SPEAKING, message = "Der Coach spricht …") }
            val first = result.errors.firstOrNull()
            val line = if (first != null) Prompts.coachSpeech(first) else "Well done – that sounded correct."
            val pcm = withTimeoutOrNull(4_000) { app.ai.speak(line) }
            val voice =
                if (pcm != null && pcm.size > 4_000) {
                    player.enqueue(pcm)
                    delay(player.remainingMs() + 300)
                    "Gemini-Stimme"
                } else {
                    tts.say(line)
                    delay(1_500L + line.length * 70L)
                    "Handy-Stimme (Gemini-Stimme kam nicht rechtzeitig)"
                }
            val message =
                when {
                    first == null -> "Alles angekommen, aber Gemini fand keinen Fehler. Sag ruhig den Satz mit „goed“ – dann muss ein Hinweis kommen."
                    else -> "Alles funktioniert: Mikrofon, Ohr, Hinweis und Coach-Stimme."
                }
            _state.update { it.copy(phase = Phase.DONE, voice = voice, message = message) }
            finishAudio()
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(phase = Phase.FAILED, message = message) }
        finishAudio()
    }

    private fun finishAudio() {
        runCatching { player.stop() }
        runCatching { tts.shutdown() }
        runCatching { routing.exit() }
    }

    fun stop() {
        stopped = true
        mic?.stop()
        mic = null
        finishAudio()
        scope.cancel()
    }
}
