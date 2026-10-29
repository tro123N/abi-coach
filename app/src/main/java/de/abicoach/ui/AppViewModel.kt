package de.abicoach.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.abicoach.AbiCoachApp
import de.abicoach.data.ExamTask
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import de.abicoach.session.Analyzer
import de.abicoach.session.LiveConversation
import de.abicoach.session.MaterialGenerator
import de.abicoach.session.Prompts
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed interface Screen {
    data object Home : Screen

    data object Settings : Screen

    data object SimSetup : Screen

    data object TrainingSetup : Screen

    data object Prep : Screen

    data object Live : Screen

    data object Analyzing : Screen

    data class Debrief(val id: String) : Screen

    data object History : Screen

    data object Profile : Screen
}

/** A tiny back stack; enough for this app and free of extra dependencies. */
class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replaceTop(screen: Screen) {
        stack[stack.lastIndex] = screen
    }

    fun resetTo(vararg screens: Screen) {
        stack.clear()
        stack.addAll(screens)
    }
}

/** Holds the flow of a session across screens: material, notes, the live parts and the analysis. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as AbiCoachApp
    val nav = Navigator()

    var hasKey by mutableStateOf(app.settings.geminiKey.isNotBlank())
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    var task by mutableStateOf<ExamTask?>(null)
        private set
    var notes by mutableStateOf("")
    var feedbackMode by mutableStateOf(FeedbackMode.PRUEFUNG)
        private set
    var prepEndsAt by mutableLongStateOf(0L)
        private set
    var live by mutableStateOf<LiveConversation?>(null)
        private set
    var analyzingId by mutableStateOf<String?>(null)
        private set

    private var draft: SessionRecord? = null

    fun refreshKey() {
        hasKey = app.settings.geminiKey.isNotBlank()
    }

    fun daysUntilExam(): Long? =
        runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(app.settings.examDate.trim())) }
            .getOrNull()
            ?.takeIf { it >= 0 }

    private fun ensureKey(): Boolean {
        if (hasKey) return true
        errorMessage = "Bitte trage zuerst deinen Gemini-API-Schlüssel in den Einstellungen ein."
        return false
    }

    private fun vocabulary(): List<String> {
        val own =
            app.settings.courseTopics.split(",", ";", "\n").map { it.trim() }.filter { it.length in 2..40 }
        val fromTask = task?.title?.let { listOf(it) } ?: emptyList()
        return (own + fromTask + Knowledge.DEFAULT_VOCABULARY).distinct().take(100)
    }

    // ---------- Exam simulation ----------

    fun startSimulation(topic: String, textType: String, prepMinutes: Int, mode: FeedbackMode) {
        if (!ensureKey() || busy != null) return
        errorMessage = null
        busy = "Erstelle neues Prüfungsmaterial …"
        feedbackMode = mode
        viewModelScope.launch {
            try {
                val area = if (topic == Knowledge.RANDOM) Knowledge.TOPIC_AREAS.random() else topic
                val generated = MaterialGenerator.generate(app, area, textType)
                task = generated
                notes = ""
                prepEndsAt = System.currentTimeMillis() + prepMinutes * 60_000L
                draft = SessionRecord.create(SessionKind.SIMULATION, mode, area, generated)
                nav.push(Screen.Prep)
            } catch (e: Exception) {
                errorMessage = "Material konnte nicht erstellt werden: ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    fun startPart1() {
        val current = draft ?: return
        val t = task ?: return
        draft = current.copy(notes = notes)
        val conversation =
            LiveConversation(
                app,
                LiveConversation.Config(
                    part = 1,
                    examinerPrompt = null,
                    kickoff = null,
                    feedback = feedbackMode,
                    vocabulary = vocabulary(),
                    wavFile = app.store.audioFile(current.id, 1),
                    checkerContext = "Presentation on the text \"${t.title}\" (${t.topicArea})",
                    targetSeconds = 600,
                ),
            )
        live = conversation
        conversation.start()
        nav.push(Screen.Live)
    }

    fun finishPart1() {
        val conversation = live ?: return
        if (busy != null) return
        busy = "Wechsel zu Teil 2 …"
        viewModelScope.launch {
            try {
                val result = conversation.stop()
                val previous = draft ?: return@launch
                val updated =
                    previous.copy(
                        utterances = previous.utterances + result.utterances,
                        liveErrors = previous.liveErrors + result.liveErrors,
                        part1Sec = result.seconds,
                        durationSec = result.seconds,
                    )
                draft = updated
                app.store.save(updated)
                val t = updated.task ?: return@launch
                val excerpt =
                    result.utterances.filter { it.speaker == Speaker.STUDENT }
                        .joinToString(" ") { it.text }
                        .take(1500)
                        .ifBlank { "(no transcript available)" }
                val next =
                    LiveConversation(
                        app,
                        LiveConversation.Config(
                            part = 2,
                            examinerPrompt = Prompts.part2Examiner(t, excerpt, app.settings.courseTopics),
                            kickoff = "START: Begin part 2 now.",
                            feedback = feedbackMode,
                            vocabulary = vocabulary(),
                            wavFile = app.store.audioFile(updated.id, 2),
                            checkerContext = "Exam conversation: ${t.part2Topics.joinToString("; ")}",
                            targetSeconds = 600,
                        ),
                    )
                live = next
                next.start()
            } finally {
                busy = null
            }
        }
    }

    // ---------- Training ----------

    fun startTraining(topic: String, mode: FeedbackMode, focus: List<String> = emptyList()) {
        if (!ensureKey()) return
        errorMessage = null
        val area = if (topic == Knowledge.RANDOM) Knowledge.TOPIC_AREAS.random() else topic
        feedbackMode = mode
        task = null
        notes = ""
        val record = SessionRecord.create(SessionKind.TRAINING, mode, area, null)
        draft = record
        val prompt =
            Prompts.trainingExaminer(area, mode, focus.map { Knowledge.categoryEnglish(it) }, app.settings.courseTopics)
        val conversation =
            LiveConversation(
                app,
                LiveConversation.Config(
                    part = 0,
                    examinerPrompt = prompt,
                    kickoff = "START: Greet the student in one short sentence and ask your first open question.",
                    feedback = mode,
                    vocabulary = vocabulary(),
                    wavFile = app.store.audioFile(record.id, 0),
                    checkerContext = "Practice conversation about: $area",
                    targetSeconds = 600,
                ),
            )
        live = conversation
        conversation.start()
        nav.push(Screen.Live)
    }

    // ---------- Ending, cancelling, analysing ----------

    fun finishSession() {
        val conversation = live ?: return
        if (busy != null) return
        busy = "Beende die Sitzung …"
        viewModelScope.launch {
            val result = conversation.stop()
            live = null
            val previous = draft
            draft = null
            busy = null
            if (previous == null) {
                nav.resetTo(Screen.Home)
                return@launch
            }
            val record =
                previous.copy(
                    utterances = previous.utterances + result.utterances,
                    liveErrors = previous.liveErrors + result.liveErrors,
                    durationSec = previous.durationSec + result.seconds,
                )
            app.store.save(record)
            nav.resetTo(Screen.Home, Screen.Analyzing)
            analyze(record.id)
        }
    }

    fun cancelLive() {
        live?.abort()
        live = null
        draft?.let { d -> (0..2).forEach { app.store.audioFile(d.id, it).delete() } }
        draft = null
        busy = null
        nav.resetTo(Screen.Home)
    }

    fun analyze(id: String) {
        val record = app.store.get(id) ?: return
        errorMessage = null
        analyzingId = id
        busy = "Bereite die Auswertung vor …"
        viewModelScope.launch {
            try {
                val debrief = Analyzer(app).analyze(record) { message -> busy = message }
                app.store.save(record.copy(debrief = debrief))
                if (nav.current == Screen.Analyzing) nav.replaceTop(Screen.Debrief(id)) else nav.push(Screen.Debrief(id))
            } catch (e: Exception) {
                errorMessage = "Auswertung fehlgeschlagen: ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    fun openAnalysis(id: String) {
        nav.push(Screen.Analyzing)
        analyze(id)
    }

    override fun onCleared() {
        live?.abort()
        super.onCleared()
    }
}
