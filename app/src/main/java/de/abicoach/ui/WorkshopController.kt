package de.abicoach.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.abicoach.AbiCoachApp
import de.abicoach.audio.FeedbackType
import de.abicoach.learn.DrillCheck
import de.abicoach.learn.ErrorPattern
import de.abicoach.learn.PatternState
import de.abicoach.learn.TalkCheck
import de.abicoach.learn.TutorMemory
import de.abicoach.learn.WorkshopCheck
import de.abicoach.learn.WorkshopLesson
import de.abicoach.learn.WorkshopMaker
import de.abicoach.learn.WorkshopReflect
import de.abicoach.learn.WorkshopRun
import de.abicoach.practice.ShortRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Fehler-Werkstatt: one error pattern in five steps – understand (the rule on the student's own sentences),
 * recognise (right or wrong?), say it (short speaking exercises), use it freely (an examiner question) and the result.
 * Afterwards the tutor writes down what it learnt about the student; the next round starts there.
 */
class WorkshopController(private val app: AbiCoachApp, private val scope: CoroutineScope, private val onNotice: (String) -> Unit) {
    enum class Stage(val label: String) {
        LOADING("Vorbereiten"),
        LEARN("Verstehen"),
        RECOGNIZE("Erkennen"),
        SPEAK("Richtig sagen"),
        TALK("Frei sprechen"),
        DONE("Ergebnis"),
        EMPTY("Keine Fehler"),
    }

    var stage by mutableStateOf(Stage.LOADING)
        private set
    var pattern by mutableStateOf<ErrorPattern?>(null)
        private set
    var lesson by mutableStateOf<WorkshopLesson?>(null)
        private set
    var memory by mutableStateOf<TutorMemory?>(null)
        private set

    var recognizeIndex by mutableIntStateOf(0)
        private set

    /** What the student tapped for the current sentence (true = "richtig"), null before. */
    var recognizeAnswer by mutableStateOf<Boolean?>(null)
        private set
    private val recognizeOk = mutableStateListOf<Boolean>()

    var drillIndex by mutableIntStateOf(0)
        private set
    var drillResult by mutableStateOf<DrillCheck.Result?>(null)
        private set
    private val drillOk = mutableStateListOf<Boolean>()

    var talkIndex by mutableIntStateOf(0)
        private set
    var talkResult by mutableStateOf<TalkCheck?>(null)
        private set
    private val talkOk = mutableStateListOf<Boolean>()

    var recording by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    /** The finished round and what the tutor learnt from it. */
    var run by mutableStateOf<WorkshopRun?>(null)
        private set
    var reflecting by mutableStateOf(false)
        private set
    var stateBefore by mutableStateOf<PatternState?>(null)
        private set

    private var recorder: ShortRecorder? = null
    private var job: Job? = null
    private val answers = ArrayList<String>()

    /** Opens the workshop for [patternKey], or for the most urgent pattern (due first, then the most costly). */
    fun open(patternKey: String?) {
        reset()
        val now = System.currentTimeMillis()
        val p =
            patternKey?.let { app.errorMemory.get(it) }
                ?: app.errorMemory.due(now).firstOrNull()
                ?: app.errorMemory.top(1, now).firstOrNull()
        if (p == null) {
            stage = Stage.EMPTY
            return
        }
        pattern = p
        stateBefore = p.state
        memory = app.workshops.get(p.key)
        stage = Stage.LOADING
        job =
            scope.launch {
                val made =
                    try {
                        withContext(Dispatchers.Default) { WorkshopMaker.make(app, p, memory) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onNotice("Die KI konnte die Lerneinheit nicht schreiben (${e.message?.take(80)}) – du übst mit deinen eigenen Sätzen.")
                        WorkshopMaker.local(p, memory?.level ?: 1)
                    }
                lesson = made
                stage = Stage.LEARN
            }
    }

    /** The next round on the same pattern (new sentences, the level the tutor chose). */
    fun again() = open(pattern?.key)

    /** The next pattern that needs work (not the one just done). */
    fun nextPattern() {
        val now = System.currentTimeMillis()
        val done = pattern?.key
        val next = (app.errorMemory.due(now) + app.errorMemory.top(6, now)).firstOrNull { it.key != done && it.state != PatternState.BEHOBEN }
        open(next?.key ?: done)
    }

    fun next() {
        val l = lesson ?: return
        when (stage) {
            Stage.LEARN -> stage = if (l.recognize.isNotEmpty()) Stage.RECOGNIZE else Stage.SPEAK
            Stage.RECOGNIZE -> {
                if (recognizeAnswer == null) return
                recognizeAnswer = null
                if (recognizeIndex + 1 < l.recognize.size) recognizeIndex++ else stage = if (l.drills.isNotEmpty()) Stage.SPEAK else Stage.TALK
            }
            Stage.SPEAK -> {
                drillResult = null
                if (drillIndex + 1 < l.drills.size) drillIndex++ else if (l.talk.isNotEmpty()) stage = Stage.TALK else finish()
            }
            Stage.TALK -> {
                talkResult = null
                if (talkIndex + 1 < l.talk.size) talkIndex++ else finish()
            }
            else -> Unit
        }
    }

    /** "Richtig" or "Falsch" for the current sentence. */
    fun answer(saysCorrect: Boolean) {
        val l = lesson ?: return
        if (stage != Stage.RECOGNIZE || recognizeAnswer != null) return
        val item = l.recognize.getOrNull(recognizeIndex) ?: return
        recognizeAnswer = saysCorrect
        val ok = saysCorrect == item.correct
        recognizeOk += ok
        answers += "Erkennen: \"${item.sentence}\" – er sagte ${if (saysCorrect) "richtig" else "falsch"} (${if (ok) "stimmt" else "stimmt nicht"})"
        app.feedback.play(if (ok) FeedbackType.CORRECT else FeedbackType.WRONG)
    }

    fun startRecording() {
        if (recording || busy || (stage != Stage.SPEAK && stage != Stage.TALK)) return
        recorder?.release()
        recorder = ShortRecorder(maxSeconds = if (stage == Stage.TALK) 60 else 20).also { it.start() }
        recording = true
        if (stage == Stage.SPEAK) drillResult = null else talkResult = null
        app.feedback.play(FeedbackType.START)
    }

    fun stopRecording() {
        val r = recorder ?: return
        val wav = r.stop()
        r.release()
        recorder = null
        recording = false
        app.feedback.play(FeedbackType.STOP)
        val l = lesson ?: return
        if (wav.size < 44 + 16_000) {
            if (stage == Stage.SPEAK) {
                drillResult = DrillCheck.Result(false, "", "Die Aufnahme war zu kurz. Tippe auf „Sprechen“, sag es und tippe dann auf „Fertig“.", "")
            } else {
                talkResult = TalkCheck("", false, false, "Die Aufnahme war zu kurz. Antworte in drei, vier Sätzen.", "")
            }
            return
        }
        busy = true
        val atStage = stage
        job =
            scope.launch {
                try {
                    if (atStage == Stage.SPEAK) {
                        val drill = l.drills.getOrNull(drillIndex) ?: return@launch
                        val result = withContext(Dispatchers.Default) { DrillCheck.check(app, drill, wav) }
                        drillResult = result
                        if (drillOk.size <= drillIndex) {
                            drillOk += result.ok
                            answers += "Sagen (${drill.type.label}): Vorgabe \"${drill.cue}\" – er sagte \"${result.heard}\" (${if (result.ok) "richtig" else "falsch"})"
                        }
                        app.feedback.play(if (result.ok) FeedbackType.CORRECT else FeedbackType.WRONG)
                    } else {
                        val prompt = l.talk.getOrNull(talkIndex) ?: return@launch
                        val result = withContext(Dispatchers.Default) { WorkshopCheck.check(app, l, prompt, wav) }
                        talkResult = result
                        if (talkOk.size <= talkIndex) {
                            talkOk += result.ok
                            answers += "Frei sprechen: \"${prompt.question}\" – er sagte \"${result.heard}\" (Struktur ${if (result.used) "verwendet" else "nicht verwendet"}, ${if (result.ok) "richtig" else "falsch"})"
                        }
                        app.feedback.play(if (result.ok) FeedbackType.CORRECT else FeedbackType.WRONG)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val message = "Das Zuhören hat nicht geklappt: ${e.message?.take(100)}"
                    if (atStage == Stage.SPEAK) drillResult = DrillCheck.Result(false, "", message, "") else talkResult = TalkCheck("", false, false, message, "")
                } finally {
                    busy = false
                }
            }
    }

    /** The round is over: the error memory gets the result, the tutor notes what it learnt. */
    private fun finish() {
        val p = pattern ?: return
        val l = lesson ?: return
        val result =
            WorkshopRun(
                at = System.currentTimeMillis(),
                level = l.level,
                recognize = recognizeOk.count { it } to recognizeOk.size,
                speak = drillOk.count { it } to drillOk.size,
                talk = talkOk.count { it } to talkOk.size,
            )
        run = result
        stage = Stage.DONE
        app.feedback.play(if (result.score >= 70) FeedbackType.RESULT_GOOD else FeedbackType.STOP)
        // A good round counts like a right answer in the speaking training (the pattern moves on in the review plan).
        app.errorMemory.reviewDrill(p.key, result.score >= 70)
        reflecting = true
        job =
            scope.launch {
                try {
                    val learnt = withContext(Dispatchers.Default) { WorkshopReflect.reflect(app, p, l, result, answers.toList(), memory) }
                    app.workshops.put(learnt)
                    memory = learnt
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val base = memory ?: TutorMemory(p.key)
                    app.workshops.put(base.copy(runs = (base.runs + result).takeLast(12)))
                } finally {
                    reflecting = false
                    pattern = app.errorMemory.get(p.key) ?: p
                }
            }
    }

    fun close() {
        job?.cancel()
        recorder?.release()
        recorder = null
        recording = false
    }

    private fun reset() {
        close()
        lesson = null
        pattern = null
        memory = null
        run = null
        stateBefore = null
        recognizeIndex = 0
        recognizeAnswer = null
        recognizeOk.clear()
        drillIndex = 0
        drillResult = null
        drillOk.clear()
        talkIndex = 0
        talkResult = null
        talkOk.clear()
        busy = false
        reflecting = false
        answers.clear()
    }

    /** For the screenshots of the app test: a finished lesson without the AI. */
    fun demo(p: ErrorPattern, l: WorkshopLesson, at: Stage, tutor: TutorMemory? = null) {
        reset()
        pattern = p
        lesson = l
        memory = tutor
        stateBefore = p.state
        stage = at
        if (at == Stage.DONE) run = WorkshopRun(System.currentTimeMillis(), l.level, 4 to 5, 2 to 3, 1 to 2)
    }

    /** Right answers so far in this round, for the progress line. */
    val stepsDone: Int get() = recognizeOk.size + drillOk.size + talkOk.size

    val stepsTotal: Int get() = lesson?.let { it.recognize.size + it.drills.size + it.talk.size } ?: 0
}
