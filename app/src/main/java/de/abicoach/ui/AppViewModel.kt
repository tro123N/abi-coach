package de.abicoach.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.abicoach.AbiCoachApp
import de.abicoach.ai.OpenAiKeyCheck
import de.abicoach.audio.FeedbackType
import de.abicoach.data.ExamTask
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import de.abicoach.memory.DocKind
import de.abicoach.memory.DocStatus
import de.abicoach.memory.Importer
import de.abicoach.memory.IngestScheduler
import de.abicoach.session.Analyzer
import de.abicoach.session.LiveConversation
import de.abicoach.session.MaterialGenerator
import de.abicoach.session.MaterialQuestion
import de.abicoach.session.Prompts
import de.abicoach.ui.components.Tab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data class Root(val tab: Tab) : Screen

    data object Settings : Screen

    data class Setup(val setup: SessionSetup) : Screen

    data object Generating : Screen

    data object Prep : Screen

    data object Live : Screen

    data object Analyzing : Screen

    data class Debrief(val id: String) : Screen

    data class Doc(val id: String) : Screen

    data object Ask : Screen

    data object Sessions : Screen

    data object PlacementIntro : Screen

    data class Level(val id: String) : Screen
}

/** A tiny back stack; the Abi-Weg always stays at the bottom, like the Kletterweg in Mental Math Plus. */
class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Root(Tab.WEG))
    val current: Screen get() = stack.last()

    /** The tab whose bottom bar entry is highlighted. */
    val tab: Tab get() = stack.filterIsInstance<Screen.Root>().lastOrNull()?.tab ?: Tab.WEG

    fun push(screen: Screen) {
        if (stack.last() != screen) stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replaceTop(screen: Screen) {
        stack[stack.lastIndex] = screen
    }

    fun openTab(tab: Tab) {
        stack.clear()
        stack.add(Screen.Root(Tab.WEG))
        if (tab != Tab.WEG) stack.add(Screen.Root(tab))
    }

    /** Back to the tab root, then show [screen] on top. */
    fun resetTo(screen: Screen? = null) {
        val root = tab
        openTab(root)
        if (screen != null) stack.add(screen)
    }
}

data class AskMessage(val fromUser: Boolean, val text: String, val sources: List<String> = emptyList(), val by: String = "")

/** Holds the flow of a session across screens: material, notes, the live parts and the analysis. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as AbiCoachApp
    val nav = Navigator()

    var busy by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var task by mutableStateOf<ExamTask?>(null)
        private set
    var notes by mutableStateOf("")
    var prepEndsAt by mutableLongStateOf(0L)
        private set
    var live by mutableStateOf<LiveConversation?>(null)
        private set
    var draft by mutableStateOf<SessionRecord?>(null)
        private set
    var progressText by mutableStateOf("")
        private set
    var analyzingId by mutableStateOf<String?>(null)
        private set

    /** Tasks of part 1 the student ticked off during the presentation. */
    val tasksDone = mutableStateListOf<Int>()

    val askMessages = mutableStateListOf<AskMessage>()
    var askBusy by mutableStateOf(false)
        private set

    /** Files picked in "Mein Kurs" that wait for the "Was ist das?" dialog. */
    var pendingImport by mutableStateOf<List<Importer.Picked>?>(null)
    var importBusy by mutableStateOf(false)
        private set

    /** Results of the key checks: provider → message (null = ok). */
    var keyChecks by mutableStateOf<Map<String, String?>>(emptyMap())
        private set
    var keyChecking by mutableStateOf<String?>(null)
        private set

    private var setup: SessionSetup? = null
    private var job: Job? = null

    /** The text currently read aloud (for the "Vorlesen" buttons). */
    var speaking by mutableStateOf<String?>(null)
        private set
    private var speechJob: Job? = null
    private var speechPlayer: de.abicoach.audio.PcmPlayer? = null
    private val androidTts by lazy { de.abicoach.audio.Tts(app) }

    fun tap() = app.feedback.play(FeedbackType.TAP)

    // ---------- navigation helpers ----------

    fun open(target: Target) {
        tap()
        when (target) {
            Target.Keys, Target.ExamDate -> nav.push(Screen.Settings)
            Target.Course -> nav.openTab(Tab.KURS)
            Target.Placement -> nav.push(Screen.PlacementIntro)
            is Target.Setup -> nav.push(Screen.Setup(target.setup))
        }
    }

    fun back(): Boolean {
        return when (nav.current) {
            Screen.Live, Screen.Generating -> false
            is Screen.Root -> {
                if ((nav.current as Screen.Root).tab != Tab.WEG) {
                    nav.openTab(Tab.WEG)
                    true
                } else {
                    false
                }
            }
            else -> nav.pop()
        }
    }

    // ---------- starting a session ----------

    private fun resolveTopic(choice: Int): Int =
        when (choice) {
            Knowledge.FROM_COURSE -> app.course.pickTopicFromCourse()
            Knowledge.RANDOM -> Knowledge.TOPICS.random().id
            else -> choice
        }

    private fun vocabulary(): List<String> {
        val own = app.settings.courseTopics.split(",", ";", "\n").map { it.trim() }.filter { it.length in 2..40 }
        val fromTask = task?.title?.let { listOf(it) } ?: emptyList()
        return (own + fromTask + app.course.vocabulary(40) + Knowledge.DEFAULT_VOCABULARY).distinct().take(100)
    }

    fun start(s: SessionSetup) {
        if (busy != null) return
        if (!app.ai.hasGemini) {
            errorMessage = "Bitte trage zuerst deinen Gemini-Schlüssel ein (Einstellungen → KI)."
            nav.push(Screen.Settings)
            return
        }
        errorMessage = null
        notice = null
        setup = s
        app.feedback.play(FeedbackType.START)
        val topicId = resolveTopic(s.topicChoice)
        if (s.kind.hasPart1) {
            progressText = "Bereite das Material vor …"
            nav.push(Screen.Generating)
            job =
                viewModelScope.launch {
                    try {
                        val request =
                            MaterialGenerator.Request(
                                topicId = topicId,
                                textType = s.textType,
                                source = if (s.fixedDocId != null) Knowledge.Source.MATERIAL else s.source,
                                short = s.kind == SessionKind.PLACEMENT,
                                instruction = s.instruction,
                            )
                        val (generated, note) =
                            withContext(Dispatchers.Default) {
                                MaterialGenerator.generate(app, request, fixedDocId = s.fixedDocId, fixedPage = s.fixedPage) { progressText = it }
                            }
                        task = generated
                        notes = ""
                        tasksDone.clear()
                        notice = note ?: app.ai.lastNotice.also { app.ai.lastNotice = null }
                        prepEndsAt = System.currentTimeMillis() + s.prepMinutes * 60_000L
                        draft = SessionRecord.create(s.kind, s.feedback, topicId, generated, s.focusTask, s.focusCategories).copy(levelId = s.levelId.orEmpty())
                        app.feedback.play(FeedbackType.ENABLE)
                        if (nav.current == Screen.Generating) nav.replaceTop(Screen.Prep)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorMessage = "Material konnte nicht erstellt werden: ${e.message}"
                        if (nav.current == Screen.Generating) nav.pop()
                    }
                }
        } else {
            startConversation(s, topicId)
        }
    }

    fun cancelGenerating() {
        job?.cancel()
        job = null
        if (nav.current == Screen.Generating) nav.pop()
    }

    private fun startConversation(s: SessionSetup, topicId: Int) {
        task = null
        notes = ""
        val record = SessionRecord.create(s.kind, s.feedback, topicId, null, 0, s.focusCategories).copy(levelId = s.levelId.orEmpty())
        draft = record
        viewModelScope.launch {
            val context = withContext(Dispatchers.Default) { runCatching { app.course.forTopic(topicId, maxChars = 4_000) }.getOrDefault("") }
            val prompt =
                Prompts.trainingExaminer(
                    topicId = topicId,
                    mode = s.feedback,
                    focus = s.focusCategories.map { Knowledge.categoryEnglish(it) },
                    courseOverview = app.course.overview(1_200),
                    topicContext = context,
                    teacherNotes = app.course.teacherNotes(6),
                    part2Style = s.kind == SessionKind.PART2,
                    extra = s.instruction,
                )
            val conversation =
                LiveConversation(
                    app,
                    LiveConversation.Config(
                        part = if (s.kind == SessionKind.PART2) 2 else 0,
                        examinerPrompt = prompt,
                        kickoff = Prompts.KICKOFF_TRAINING,
                        feedback = s.feedback,
                        vocabulary = vocabulary(),
                        wavFile = app.store.audioFile(record.id, if (s.kind == SessionKind.PART2) 2 else 0),
                        checkerContext = "Conversation about: ${Knowledge.topic(topicId)?.english ?: "the course"}",
                        targetSeconds = s.talkMinutes * 60,
                    ),
                )
            live = conversation
            conversation.start()
            nav.push(Screen.Live)
        }
    }

    // ---------- part 1 and part 2 ----------

    fun startPart1() {
        val current = draft ?: return
        val t = task ?: return
        draft = current.copy(notes = notes)
        val target =
            when {
                current.kind == SessionKind.PLACEMENT -> 240
                current.focusTask in 1..3 -> 180
                else -> 600
            }
        val conversation =
            LiveConversation(
                app,
                LiveConversation.Config(
                    part = 1,
                    examinerPrompt = null,
                    kickoff = null,
                    feedback = current.feedback,
                    vocabulary = vocabulary(),
                    wavFile = app.store.audioFile(current.id, 1),
                    checkerContext = "Presentation on the text \"${t.title}\" (${Knowledge.topic(t.topicId)?.english ?: ""})",
                    targetSeconds = target,
                ),
            )
        live = conversation
        app.feedback.play(FeedbackType.START)
        conversation.start()
        nav.replaceTop(Screen.Live)
    }

    /** Ends the presentation; in a full simulation the exam conversation follows directly. */
    fun finishPart1() {
        val conversation = live ?: return
        if (busy != null) return
        val current = draft ?: return
        if (!current.kind.hasPart2) {
            finishSession()
            return
        }
        busy = "Wechsel zu Teil 2 …"
        viewModelScope.launch {
            try {
                val result = conversation.stop()
                val updated =
                    current.copy(
                        utterances = current.utterances + result.utterances,
                        liveErrors = current.liveErrors + result.liveErrors,
                        part1Sec = result.seconds,
                        durationSec = result.seconds,
                    )
                draft = updated
                app.store.save(updated)
                val t = updated.task ?: return@launch
                val excerpt =
                    result.utterances.filter { it.speaker == Speaker.STUDENT }.joinToString(" ") { it.text }.take(1500).ifBlank { "(no transcript available)" }
                val topicContext =
                    withContext(Dispatchers.Default) {
                        t.part2.firstOrNull()?.let { runCatching { app.course.forTopic(it.topicId, maxChars = 3_000) }.getOrDefault("") }.orEmpty()
                    }
                val minutes = if (updated.kind == SessionKind.PLACEMENT) 4 else 10
                val next =
                    LiveConversation(
                        app,
                        LiveConversation.Config(
                            part = 2,
                            examinerPrompt = Prompts.part2Examiner(t, excerpt, app.course.overview(1_200), topicContext, app.course.teacherNotes(6), minutes),
                            kickoff = Prompts.KICKOFF_PART2,
                            feedback = updated.feedback,
                            vocabulary = vocabulary(),
                            wavFile = app.store.audioFile(updated.id, 2),
                            checkerContext = "Exam conversation: ${t.part2.joinToString("; ") { it.question }}",
                            targetSeconds = minutes * 60,
                        ),
                    )
                live = next
                next.start()
            } finally {
                busy = null
            }
        }
    }

    fun finishSession() {
        val conversation = live ?: return
        if (busy != null) return
        busy = "Beende die Sitzung …"
        app.feedback.play(FeedbackType.STOP)
        viewModelScope.launch {
            val result = conversation.stop()
            live = null
            val previous = draft
            draft = null
            busy = null
            if (previous == null) {
                nav.resetTo()
                return@launch
            }
            val record =
                previous.copy(
                    utterances = previous.utterances + result.utterances,
                    liveErrors = previous.liveErrors + result.liveErrors,
                    durationSec = previous.durationSec + result.seconds,
                    part1Sec = if (previous.kind == SessionKind.PART1) result.seconds else previous.part1Sec,
                    examiner = result.examinerLabel.ifBlank { previous.examiner },
                    examinerSeconds = previous.examinerSeconds + result.examinerSeconds,
                )
            app.store.save(record)
            nav.resetTo(Screen.Analyzing)
            analyze(record.id)
        }
    }

    fun cancelLive() {
        live?.abort()
        live = null
        draft?.let { d -> (0..2).forEach { app.store.audioFile(d.id, it).delete() } }
        draft = null
        busy = null
        nav.resetTo()
    }

    fun cancelPrep() {
        draft = null
        task = null
        nav.pop()
    }

    // ---------- analysis ----------

    fun analyze(id: String) {
        val record = app.store.get(id) ?: return
        errorMessage = null
        analyzingId = id
        progressText = "Bereite die Auswertung vor …"
        job =
            viewModelScope.launch {
                try {
                    val debrief = withContext(Dispatchers.Default) { Analyzer(app).analyze(record) { message -> progressText = message } }
                    app.store.save(record.copy(debrief = debrief))
                    app.profiles.update { p ->
                        val rated = p.withRatings(debrief.skills, record.kind.profileWeight, System.currentTimeMillis(), record.kind.short)
                        if (record.kind == SessionKind.PLACEMENT) rated.copy(placementDone = true, placementAt = System.currentTimeMillis()) else rated
                    }
                    if (record.levelId.isNotBlank()) {
                        de.abicoach.path.Curriculum.level(record.levelId)?.let { level ->
                            val points = de.abicoach.path.PathEngine.pointsFor(level, debrief)
                            app.path.update { de.abicoach.path.PathEngine.applyResult(it, level, points) }
                        }
                    }
                    if (record.kind == SessionKind.PLACEMENT) {
                        val profile = app.profiles.profile.value
                        app.path.update { de.abicoach.path.PathEngine.applyPlacement(it, app.worlds(), profile) }
                    }
                    notice = app.ai.lastNotice.also { app.ai.lastNotice = null }
                    app.feedback.play(de.abicoach.audio.Feedback.forPoints(debrief.totalPoints))
                    if (nav.current == Screen.Analyzing) nav.replaceTop(Screen.Debrief(id)) else nav.push(Screen.Debrief(id))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorMessage = "Auswertung fehlgeschlagen: ${e.message}"
                } finally {
                    progressText = ""
                }
            }
    }

    fun openAnalysis(id: String) {
        nav.push(Screen.Analyzing)
        analyze(id)
    }

    // ---------- course material ----------

    fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pendingImport = uris.map { app.importer.describe(it) }
    }

    fun confirmImport(kind: DocKind, title: String, halbjahr: String) {
        val files = pendingImport ?: return
        pendingImport = null
        importBusy = true
        viewModelScope.launch {
            try {
                val created = withContext(Dispatchers.IO) { app.importer.import(files, kind, title.trim(), halbjahr) }
                if (created.isEmpty()) {
                    errorMessage = "Die Dateien konnten nicht gelesen werden."
                } else {
                    app.feedback.play(FeedbackType.ENABLE)
                    notice =
                        if (app.ai.hasGemini) {
                            "${created.size} ${if (created.size == 1) "Dokument" else "Dokumente"} hinzugefügt – die KI liest jetzt im Hintergrund."
                        } else {
                            "Gespeichert. Sobald dein Gemini-Schlüssel eingetragen ist, liest die KI alles ein."
                        }
                    IngestScheduler.kick(app)
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Import fehlgeschlagen"
            } finally {
                importBusy = false
            }
        }
    }

    fun pauseDoc(id: String) {
        app.memory.update(id) { it.copy(status = DocStatus.PAUSED) }
    }

    fun resumeDoc(id: String) {
        app.memory.update(id) { it.copy(status = DocStatus.QUEUED, failures = 0, error = null) }
        IngestScheduler.kick(app)
    }

    fun deleteDoc(id: String) {
        app.memory.delete(id)
        if (nav.current == Screen.Doc(id)) nav.pop()
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || askBusy) return
        if (!app.ai.hasGemini && !app.ai.useClaude) {
            errorMessage = "Für den KI-Coach brauchst du einen Schlüssel (Einstellungen → KI)."
            return
        }
        val history = askMessages.map { (if (it.fromUser) "user" else "assistant") to it.text }
        askMessages += AskMessage(true, q)
        askBusy = true
        viewModelScope.launch {
            try {
                val answer = withContext(Dispatchers.Default) { MaterialQuestion.ask(app, q, history) }
                askMessages += AskMessage(false, answer.text, answer.hits.mapIndexed { i, h -> "[${i + 1}] ${h.citation}" }, answer.by)
            } catch (e: Exception) {
                askMessages += AskMessage(false, "Das hat nicht geklappt: ${e.message}")
            } finally {
                askBusy = false
            }
        }
    }

    fun clearAsk() {
        askMessages.clear()
    }

    // ---------- keys ----------

    fun checkKey(provider: String) {
        keyChecking = provider
        viewModelScope.launch {
            val result =
                when (provider) {
                    "gemini" -> app.ai.gemini.checkKey()
                    "anthropic" -> app.ai.claude.checkKey()
                    else -> OpenAiKeyCheck.check(app.http, app.settings.openAiKey)
                }
            keyChecks = keyChecks + (provider to result)
            keyChecking = null
            if (provider == "gemini" && result == null) app.resumeReading()
        }
    }

    fun keySaved(provider: String) {
        keyChecks = keyChecks - provider
        if (provider == "gemini") app.resumeReading()
    }

    // ---------- Abi-Weg ----------

    fun openLevel(id: String) {
        tap()
        nav.push(Screen.Level(id))
    }

    fun markLearned(id: String) {
        val level = de.abicoach.path.Curriculum.level(id) ?: return
        app.path.update { de.abicoach.path.PathEngine.markLearned(it, level) }
        app.feedback.play(FeedbackType.CORRECT)
    }

    /** Starts the exercise of a level with everything the level defines. */
    fun startLevel(id: String, feedback: FeedbackMode) {
        val level = de.abicoach.path.Curriculum.level(id) ?: return
        start(levelSetup(level, feedback))
    }

    fun levelSetup(level: de.abicoach.path.Level, feedback: FeedbackMode): SessionSetup {
        val mode = if (level.strict) FeedbackMode.PRUEFUNG else feedback
        val topic = level.topicId ?: Knowledge.FROM_COURSE
        val textType = if (level.literary) Knowledge.TextType.LITERARISCH else Knowledge.TextType.GEMISCHT
        return when (level.type) {
            de.abicoach.path.LevelType.TASK ->
                SessionSetup(SessionKind.PART1, topicChoice = topic, textType = textType, prepMinutes = if (level.strict) 10 else 8, feedback = mode, focusTask = level.focusTask, levelId = level.id, instruction = level.instruction)
            de.abicoach.path.LevelType.PRESENTATION ->
                SessionSetup(SessionKind.PART1, topicChoice = topic, textType = textType, prepMinutes = if (level.strict) 20 else 15, feedback = mode, levelId = level.id, instruction = level.instruction)
            de.abicoach.path.LevelType.TALK ->
                SessionSetup(
                    if (level.strict || level.topicId != null) SessionKind.PART2 else SessionKind.TRAINING,
                    topicChoice = topic,
                    feedback = mode,
                    levelId = level.id,
                    instruction = level.instruction,
                    talkMinutes = level.minutes.coerceIn(3, 12),
                )
            de.abicoach.path.LevelType.SIMULATION ->
                SessionSetup(SessionKind.SIMULATION, topicChoice = topic, prepMinutes = 30, feedback = FeedbackMode.PRUEFUNG, levelId = level.id)
            de.abicoach.path.LevelType.LEARN -> SessionSetup(SessionKind.TRAINING, topicChoice = topic, feedback = mode, levelId = level.id)
        }
    }

    // ---------- demo hooks for the emulator test (screenshots without network) ----------

    fun demoPrep(demoTask: ExamTask, kind: SessionKind, focusTask: Int = 0, minutes: Int = 30) {
        task = demoTask
        notes = "• Summary: Lagos, young creatives, Nollywood\n• Analysis: optimistic tone, contrasts\n• Comment: link to class (Adichie)"
        prepEndsAt = System.currentTimeMillis() + minutes * 60_000L
        draft = SessionRecord.create(kind, FeedbackMode.FLUESTERN, demoTask.topicId, demoTask, focusTask)
        nav.push(Screen.Prep)
    }

    fun demoLive(state: de.abicoach.session.LiveUiState, part: Int, withExaminer: Boolean) {
        val conversation =
            LiveConversation(
                app,
                LiveConversation.Config(
                    part = part,
                    examinerPrompt = if (withExaminer) "demo" else null,
                    kickoff = null,
                    feedback = FeedbackMode.FLUESTERN,
                    vocabulary = emptyList(),
                    wavFile = null,
                    checkerContext = "",
                    targetSeconds = 600,
                ),
            )
        conversation.startDemo(state)
        live = conversation
        nav.push(Screen.Live)
    }

    fun demoClear() {
        live = null
        draft = null
        task = null
        nav.openTab(Tab.WEG)
    }

    // ---------- reading aloud ----------

    /** Reads an English sentence aloud: Gemini's voice, or Android's own voice as a fallback. */
    fun speak(text: String) {
        stopSpeaking()
        speaking = text
        speechJob =
            viewModelScope.launch {
                try {
                    val pcm = if (app.ai.hasGemini) app.ai.speak(text) else null
                    if (pcm != null && pcm.size > 1_000) {
                        val player = de.abicoach.audio.PcmPlayer(24_000, android.media.AudioAttributes.USAGE_MEDIA)
                        speechPlayer = player
                        player.start()
                        player.enqueue(pcm)
                        kotlinx.coroutines.delay(player.remainingMs() + 400)
                        player.stop()
                        speechPlayer = null
                    } else {
                        androidTts.say(text)
                        kotlinx.coroutines.delay(400L + text.length * 70L)
                    }
                } finally {
                    if (speaking == text) speaking = null
                }
            }
    }

    fun stopSpeaking() {
        speechJob?.cancel()
        speechJob = null
        speechPlayer?.stop()
        speechPlayer = null
        androidTts.stop()
        speaking = null
    }

    // ---------- data ----------

    fun deleteSession(id: String) {
        app.store.delete(id)
        if (nav.current == Screen.Debrief(id)) nav.pop()
    }

    override fun onCleared() {
        live?.abort()
        stopSpeaking()
        androidTts.shutdown()
        super.onCleared()
    }

    companion object {
        val ANY_FEEDBACK = FeedbackMode.entries
    }
}
