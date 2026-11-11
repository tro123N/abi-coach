package de.abicoach.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.abicoach.AbiCoachApp
import de.abicoach.audio.FeedbackType
import de.abicoach.data.ExamTask
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.LiveError
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import de.abicoach.memory.DocKind
import de.abicoach.memory.DocStatus
import de.abicoach.memory.Importer
import de.abicoach.memory.IngestScheduler
import de.abicoach.learn.Drill
import de.abicoach.learn.DrillCheck
import de.abicoach.learn.DrillMaker
import de.abicoach.learn.DrillOutcome
import de.abicoach.learn.LearningBrain
import de.abicoach.memory.DossierMaker
import de.abicoach.memory.DossierScheduler
import de.abicoach.practice.SayItCheck
import de.abicoach.practice.SayTarget
import de.abicoach.session.MicTest
import de.abicoach.session.CheckLine
import de.abicoach.session.LiveConversation
import de.abicoach.session.MaterialGenerator
import de.abicoach.session.MaterialPrefetch
import de.abicoach.session.MaterialQuestion
import de.abicoach.session.MaterialScheduler
import de.abicoach.session.PendingMaterialStore
import de.abicoach.session.PrepAlarm
import de.abicoach.session.Prompts
import de.abicoach.session.SystemCheck
import de.abicoach.ui.components.Tab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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

    data object Vocab : Screen

    data object Pronunciation : Screen

    /** The short round after a presentation: say the most important mistakes correctly. */
    data class Corrections(val id: String) : Screen

    /** Speaking training of the student's own error patterns. */
    data object Drills : Screen

    /** The error memory: every kind of mistake with its state. */
    data object Errors : Screen

    /** The learning dossier of one topic field. */
    data class Dossier(val topic: Int) : Screen

    /** The original exam tasks found in the uploaded Abitur material. */
    data object RealTasks : Screen

    /** The Fehler-Werkstatt: one error pattern, step by step. */
    data object Workshop : Screen

    /** Every competence on its own: level, course and a matching exercise. */
    data object Skills : Screen

    /** Which topic field the course covered in which Kurshalbjahr. */
    data object HalfYears : Screen

    /** The setup at the first start: what the app needs to work. */
    data object Onboarding : Screen
}

/** A tiny back stack; the Abi-Weg always stays at the bottom, like the Kletterweg in Mental Math Plus. */
class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Root(Tab.WEG))
    val current: Screen get() = stack.last()

    /** The tab whose bottom bar entry is highlighted. */
    val tab: Tab get() = stack.filterIsInstance<Screen.Root>().lastOrNull()?.tab ?: Tab.WEG

    /** Which way the last change went: 1 = a page opened on top, -1 = back, 0 = another area. Drives the page motion. */
    var direction: Int = 0
        private set

    /** Called before the page changes, with the page that is being left (the view model stops sound and microphone there). */
    var onChange: ((Screen) -> Unit)? = null

    fun push(screen: Screen) {
        if (stack.last() != screen) {
            onChange?.invoke(stack.last())
            direction = 1
            stack.add(screen)
        }
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        onChange?.invoke(stack.last())
        direction = -1
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replaceTop(screen: Screen) {
        onChange?.invoke(stack.last())
        direction = 0
        stack[stack.lastIndex] = screen
    }

    fun openTab(tab: Tab) {
        onChange?.invoke(stack.last())
        direction = if (stack.last() is Screen.Root) 0 else -1
        stack.clear()
        stack.add(Screen.Root(Tab.WEG))
        if (tab != Tab.WEG) stack.add(Screen.Root(tab))
    }

    /** Back to the tab root, then show [screen] on top. */
    fun resetTo(screen: Screen? = null) {
        val root = tab
        openTab(root)
        if (screen != null) {
            direction = 1
            stack.add(screen)
        }
    }
}

data class AskMessage(
    val fromUser: Boolean,
    val text: String,
    val sources: List<String> = emptyList(),
    val by: String = "",
    val actions: List<de.abicoach.session.CoachAction> = emptyList(),
)

/** Holds the flow of a session across screens: material, notes, the live parts and the analysis. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as AbiCoachApp

    /**
     * An error in background work must never close the app: it is noted in the crash log, the student sees a
     * message, and the app is usable again.
     */
    private val guard =
        kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
            de.abicoach.data.CrashLog.nonFatal("App", e)
            busy = null
            errorMessage = "Das hat nicht geklappt (${e.message?.take(160) ?: e.javaClass.simpleName}). Die App läuft weiter – versuch es bitte noch einmal."
        }

    /** The Fehler-Werkstatt (its state lives as long as the app screen). */
    val workshop = WorkshopController(app, CoroutineScope(viewModelScope.coroutineContext + guard)) { notice = it }
    /** Leaving a page stops anything that is being read aloud on it. */
    val nav =
        Navigator().also { n ->
            n.onChange = { left ->
                de.abicoach.data.CrashLog.breadcrumb("verlässt Seite $left")
                leavePage(left)
            }
        }

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

    /** An evaluation that finished while the student was elsewhere: shown as a banner. */
    var readyDebrief by mutableStateOf<Pair<String, Int>?>(null)

    /** "Üben": 0 = free practice tiles, 1 = the Abi-Weg with its worlds and levels. */
    var practiceView by mutableIntStateOf(0)

    /** "Fortschritt": 0 = overview, 1 = competences, 2 = mistakes, 3 = topic fields. */
    var progressView by mutableIntStateOf(0)

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

    /** The test of all Google parts (Settings → System prüfen). */
    var systemCheck by mutableStateOf<List<CheckLine>>(emptyList())
        private set
    var systemChecking by mutableStateOf(false)
        private set

    /** Exam material is ready, but the preparation time has not been started yet (the minutes it will have). */
    var readyMaterialMinutes by mutableIntStateOf(0)
        private set

    // "Sag es richtig": the student says a corrected sentence and the AI listens.
    var sayTarget by mutableStateOf<SayTarget?>(null)
        private set
    var sayRecording by mutableStateOf(false)
        private set
    var sayBusy by mutableStateOf(false)
        private set
    var sayResult by mutableStateOf<SayItCheck.Result?>(null)
        private set

    /** Corrected sentences the student has said right since the app was started. */
    val sayDone = mutableStateListOf<String>()
    private var sayRecorder: de.abicoach.practice.ShortRecorder? = null

    private var pendingKey: String? = null
    private var prepMinutes = 10
    private var saveJob: Job? = null

    /** The text currently read aloud (for the "Vorlesen" buttons). */
    var speaking by mutableStateOf<String?>(null)
        private set
    private var speechJob: Job? = null
    private var speechPlayer: de.abicoach.audio.PcmPlayer? = null
    private var ttsInstance: de.abicoach.audio.Tts? = null

    /** Android's own voice, started only when it is needed for the first time. */
    private val androidTts: de.abicoach.audio.Tts
        get() = ttsInstance ?: de.abicoach.audio.Tts(app) { app.settings.accent.locale }.also { ttsInstance = it }

    fun tap() = app.feedback.play(FeedbackType.TAP)

    // ---------- navigation helpers ----------

    fun open(target: Target) {
        tap()
        when (target) {
            Target.Keys, Target.ExamDate -> nav.push(Screen.Settings)
            Target.Course -> nav.openTab(Tab.KURS)
            Target.Placement -> nav.push(Screen.PlacementIntro)
            Target.Vocab -> openVocab()
            is Target.Setup -> nav.push(Screen.Setup(target.setup))
            Target.Drills -> openDrills()
            Target.Pronunciation -> openPronunciation()
            is Target.Dossier -> openDossier(target.topic)
        }
    }

    fun back(): Boolean {
        return when (val current = nav.current) {
            Screen.Live, Screen.Generating -> false
            is Screen.Corrections -> {
                finishCorrections(current.id)
                true
            }
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

    /** "Aus meinem Kurs": the learning brain picks a covered topic field that needs practice (weak, unsure or new). */
    private fun resolveTopic(choice: Int): Int =
        when (choice) {
            Knowledge.FROM_COURSE -> LearningBrain.pickTopic(app)
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
        if (!app.ai.ready) {
            errorMessage = "Bitte trage zuerst deinen ${app.ai.provider.company}-Schlüssel ein (Einstellungen → KI)."
            nav.push(Screen.Settings)
            return
        }
        errorMessage = null
        notice = null
        app.feedback.play(FeedbackType.START)
        if (s.kind.hasPart1) {
            startMaterial(s)
        } else {
            startConversation(s, resolveTopic(s.topicChoice))
        }
    }

    private fun pendingFor(key: String, s: SessionSetup, topicId: Int) =
        PendingMaterialStore.Pending(
            key, s.kind, s.feedback, topicId, s.focusTask, s.focusCategories, s.levelId.orEmpty(), s.prepMinutes, System.currentTimeMillis(),
            focusSkill = s.focusSkill, talkStyle = (s.talkStyle ?: app.settings.talkStyle).name,
        )

    /** The exam text is written by a background job: it keeps going when the student leaves the app. */
    private fun startMaterial(s: SessionSetup) {
        // A text that was prepared in advance: the preparation starts at once.
        MaterialPrefetch.takeFor(app, s)?.let { ready ->
            adoptMaterial(pendingFor("", s, ready.task.topicId), ready.task, ready.note, openNow = true)
            viewModelScope.launch(guard + Dispatchers.Default) { MaterialPrefetch.maybeStart(app) }
            return
        }
        val topicId = resolveTopic(s.topicChoice)
        val key = UUID.randomUUID().toString()
        app.pendingMaterial.save(pendingFor(key, s, topicId))
        pendingKey = key
        progressText = "Bereite das Material vor …"
        app.material.reset()
        nav.push(Screen.Generating)
        val request =
            MaterialGenerator.Request(
                topicId = topicId,
                textType = s.textType,
                source = if (s.fixedDocId != null) Knowledge.Source.MATERIAL else s.source,
                short = s.kind == SessionKind.PLACEMENT,
                instruction = s.instruction,
                unitId = s.unitId,
                part2Topics = if (s.kind.hasPart2) s.extraTopics.filter { it != topicId }.take(MAX_EXTRA_TOPICS) else emptyList(),
            )
        MaterialScheduler.enqueue(app, key, request, s.fixedDocId, s.fixedPage, prefetch = false)
    }

    /** Material is ready: show the preparation page, or (if the student is elsewhere) a banner. */
    private fun adoptMaterial(p: PendingMaterialStore.Pending, generated: ExamTask, note: String?, openNow: Boolean) {
        task = generated
        notes = ""
        tasksDone.clear()
        notice = note
        draft =
            SessionRecord.create(p.kind, p.feedback, p.topicId, generated, p.focusTask, p.focusCategories)
                .copy(
                    levelId = p.levelId,
                    goals = LearningBrain.goals(app, p.kind, p.focusTask, de.abicoach.profile.Skill.byKey(p.focusSkill)),
                    focusSkill = p.focusSkill,
                    talkStyle = p.talkStyle,
                    strictness = app.settings.strictness.name,
                )
        prepMinutes = p.prepMinutes
        prepEndsAt = 0L
        if (openNow) {
            openPrep()
        } else {
            readyMaterialMinutes = p.prepMinutes
            persistDraft()
            app.feedback.play(FeedbackType.ENABLE)
        }
    }

    /** Starts the preparation time (it begins now, not when the text was written) and shows the preparation page. */
    fun openPrep() {
        if (task == null || draft == null) return
        readyMaterialMinutes = 0
        prepEndsAt = System.currentTimeMillis() + prepMinutes * 60_000L
        persistDraft()
        PrepAlarm.schedule(app, prepEndsAt)
        app.feedback.play(FeedbackType.ENABLE)
        if (nav.current == Screen.Generating) nav.replaceTop(Screen.Prep) else nav.push(Screen.Prep)
    }

    /** Keeps the preparation (text, notes, timer) on disk, so nothing is lost if Android closes the app. */
    private fun persistDraft() {
        val record = draft ?: return
        val end = prepEndsAt
        val minutes = prepMinutes
        val text = notes
        viewModelScope.launch(guard + Dispatchers.IO) { app.drafts.save(record.copy(notes = text), end, minutes) }
    }

    fun updateNotes(text: String) {
        notes = text
        saveJob?.cancel()
        saveJob =
            viewModelScope.launch(guard) {
                delay(700)
                persistDraft()
            }
    }

    private fun finishMaterial(key: String) {
        val pending = app.pendingMaterial.load() ?: return
        if (pending.key != key) return
        val entry = app.shelf.take(key) ?: return
        app.pendingMaterial.clear()
        pendingKey = null
        adoptMaterial(pending, entry.task, entry.note, openNow = nav.current == Screen.Generating)
    }

    /** Follows the background job that writes the material: progress, result and errors. */
    private fun watchMaterial() {
        viewModelScope.launch(guard) {
            app.material.status.collect { st ->
                val key = pendingKey
                if (key != null && st.key == key && st.text.isNotBlank()) progressText = st.text
                st.readyKey?.let { ready ->
                    app.material.consume()
                    finishMaterial(ready)
                }
                val failed = st.key
                if (st.error != null && failed != null && failed == key) {
                    app.material.consume()
                    app.pendingMaterial.clear()
                    pendingKey = null
                    errorMessage = st.error
                    if (nav.current == Screen.Generating) nav.pop()
                }
            }
        }
    }

    /** After the app was closed: bring back a running preparation or material that was finished in the meantime. */
    private fun restoreWork() {
        val now = System.currentTimeMillis()
        val saved = app.drafts.load()
        if (saved != null) {
            if (now - saved.savedAt > 6 * 3_600_000L) {
                app.drafts.clear()
            } else {
                task = saved.record.task
                notes = saved.record.notes
                draft = saved.record
                prepMinutes = saved.prepMinutes
                tasksDone.clear()
                if (saved.prepEndsAt > 0L) {
                    prepEndsAt = saved.prepEndsAt
                    nav.push(Screen.Prep)
                    notice = "Deine Vorbereitung ist wieder da – die Zeit ist weitergelaufen."
                } else {
                    readyMaterialMinutes = saved.prepMinutes
                }
                return
            }
        }
        val pending = app.pendingMaterial.load() ?: return
        if (now - pending.startedAt > 30 * 60_000L) {
            app.pendingMaterial.clear()
            return
        }
        pendingKey = pending.key
        if (app.shelf.has(pending.key)) {
            finishMaterial(pending.key)
            return
        }
        // Still being written in the background?
        viewModelScope.launch(guard) {
            val infos =
                withContext(Dispatchers.IO) {
                    runCatching { androidx.work.WorkManager.getInstance(app).getWorkInfosForUniqueWork(MaterialScheduler.workName(pending.key)).get() }.getOrNull()
                }
            if (infos != null && infos.any { !it.state.isFinished }) {
                progressText = "Das Material wird noch geschrieben …"
                if (nav.current is Screen.Root) nav.push(Screen.Generating)
            } else {
                app.pendingMaterial.clear()
                pendingKey = null
            }
        }
    }

    fun cancelGenerating() {
        pendingKey?.let { MaterialScheduler.cancel(app, it) }
        pendingKey = null
        app.pendingMaterial.clear()
        app.material.reset()
        if (nav.current == Screen.Generating) nav.pop()
    }

    private fun startConversation(s: SessionSetup, topicId: Int) {
        task = null
        notes = ""
        // Like in the Abitur, the exam conversation links several topic fields: the ones the student chose, or else
        // a second one he needs most. A training covers further topic fields only if the student chose them.
        val chosen = s.extraTopics.filter { it in 0..13 && it != topicId }.distinct().take(MAX_EXTRA_TOPICS)
        // Without a choice the second topic field comes from another Kurshalbjahr (the Abitur links the terms).
        val extras = if (chosen.isEmpty() && s.kind == SessionKind.PART2) de.abicoach.memory.HalfYears.part2Topics(app, topicId, 1) else chosen
        val style = s.talkStyle ?: app.settings.talkStyle
        val record =
            SessionRecord.create(s.kind, s.feedback, topicId, null, 0, s.focusCategories)
                .copy(
                    levelId = s.levelId.orEmpty(),
                    goals = LearningBrain.goals(app, s.kind, 0, de.abicoach.profile.Skill.byKey(s.focusSkill)),
                    extraTopics = extras,
                    focusSkill = s.focusSkill,
                    talkStyle = style.name,
                    strictness = app.settings.strictness.name,
                )
        draft = record
        viewModelScope.launch(guard) {
            val context =
                withContext(Dispatchers.Default) {
                    val main = runCatching { app.course.forTopic(topicId, maxChars = if (extras.isEmpty()) 4_000 else 2_600) }.getOrDefault("")
                    val more = extras.map { id -> runCatching { app.course.forTopic(id, maxChars = 1_400) }.getOrDefault("") }.filter { it.isNotBlank() }.joinToString("\n\n")
                    if (more.isBlank()) main else "$main\n\n$more"
                }
            val others = extras.mapNotNull { Knowledge.topic(it)?.english }
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
                    patterns = patternBlockFor(s.feedback),
                    otherTopics = others,
                    talkStyle = style,
                    strictness = app.settings.strictness,
                    accent = app.settings.accent.english,
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
                        checkerContext = "Conversation about: " + (listOf(Knowledge.topic(topicId)?.english ?: "the course") + others).joinToString("; "),
                        targetSeconds = s.talkMinutes * 60,
                        directed = true,
                        switchTopics = others,
                        elicit = if (s.feedback != FeedbackMode.PRUEFUNG) examinerLines(4) else emptyList(),
                        monologueSec = if (s.isSprint) 0 else if (style == de.abicoach.data.TalkStyle.EINGEHEN) 140 else 100,
                        goalPattern = goalPattern(record),
                        talkStyle = style,
                        mainTopic = Knowledge.topic(topicId)?.english.orEmpty(),
                        pushToTalk = app.settings.pushToTalk,
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
        app.drafts.clear()
        PrepAlarm.cancel(app)
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
                    goalPattern = goalPattern(current),
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
        viewModelScope.launch(guard) {
            try {
                val result = conversation.stop()
                val updated =
                    current.copy(
                        utterances = current.utterances + result.utterances,
                        liveErrors = current.liveErrors + result.liveErrors,
                        part1Sec = result.seconds,
                        durationSec = result.seconds,
                        tech = joinTech(current.tech, result.tech),
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
                val part2Style = de.abicoach.data.TalkStyle.of(updated.talkStyle) ?: app.settings.talkStyle
                val next =
                    LiveConversation(
                        app,
                        LiveConversation.Config(
                            part = 2,
                            examinerPrompt =
                                Prompts.part2Examiner(
                                    t, excerpt, app.course.overview(1_200), topicContext, app.course.teacherNotes(6), minutes, patternBlockFor(updated.feedback),
                                    talkStyle = part2Style,
                                    termNote = de.abicoach.memory.HalfYears.promptNote(app, t.topicId, t.part2.map { it.topicId }),
                                    strictness = de.abicoach.data.Strictness.of(updated.strictness) ?: app.settings.strictness,
                                    accent = app.settings.accent.english,
                                ),
                            kickoff = Prompts.KICKOFF_PART2,
                            feedback = updated.feedback,
                            vocabulary = vocabulary(),
                            wavFile = app.store.audioFile(updated.id, 2),
                            checkerContext = "Exam conversation: ${t.part2.joinToString("; ") { it.question }}",
                            targetSeconds = minutes * 60,
                            directed = true,
                            switchTopics = t.part2.drop(1).map { "${Knowledge.topic(it.topicId)?.english ?: "another topic field"} – ${it.question}" },
                            elicit = if (updated.feedback != FeedbackMode.PRUEFUNG) examinerLines(4) else emptyList(),
                            goalPattern = goalPattern(updated),
                            talkStyle = part2Style,
                            mainTopic = t.part2.firstOrNull()?.let { "${Knowledge.topic(it.topicId)?.english ?: "another topic field"} – ${it.question}" }.orEmpty(),
                            monologueSec = if (part2Style == de.abicoach.data.TalkStyle.EINGEHEN) 140 else 100,
                            pushToTalk = app.settings.pushToTalk,
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
        de.abicoach.data.CrashLog.breadcrumb("Sitzung beenden")
        viewModelScope.launch(guard) {
            // Whatever goes wrong while stopping: what was said is kept and the app does not hang.
            val result =
                try {
                    conversation.stop()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    de.abicoach.data.CrashLog.nonFatal("Sitzung beenden", e)
                    conversation.peek().also { runCatching { conversation.abort() } }
                }
            live = null
            val previous = draft
            draft = null
            busy = null
            if (previous == null) {
                app.liveDrafts.clear()
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
                    tech = joinTech(previous.tech, result.tech),
                )
            app.store.save(record)
            app.liveDrafts.clear()
            // The evaluation starts in the background right away, whatever the student does next.
            analyze(record.id)
            val practice = record.feedback != FeedbackMode.PRUEFUNG && record.kind != SessionKind.SIMULATION && record.kind != SessionKind.PLACEMENT
            if (practice && correctionTargets(record).isNotEmpty()) {
                nav.resetTo(Screen.Corrections(record.id))
            } else {
                nav.resetTo(Screen.Analyzing)
            }
            // The next exam text is prepared while the student reads the result.
            launch(Dispatchers.Default) { MaterialPrefetch.maybeStart(app) }
        }
    }

    /** The running session as it would be saved right now (null when nothing real is running). */
    private fun liveSnapshot(): SessionRecord? {
        val conversation = live ?: return null
        val current = draft ?: return null
        if (!conversation.real) return null
        val r = conversation.peek()
        return current.copy(
            utterances = current.utterances + r.utterances,
            liveErrors = current.liveErrors + r.liveErrors,
            durationSec = current.durationSec + r.seconds,
            part1Sec = if (conversation.part == 1) r.seconds else current.part1Sec,
            examiner = r.examinerLabel.ifBlank { current.examiner },
            examinerSeconds = current.examinerSeconds + r.examinerSeconds,
            tech = joinTech(current.tech, r.tech),
        )
    }

    /** Writes the safety copy of the running session (every few seconds and when the app goes to the background). */
    fun saveLiveDraft() {
        val snapshot = runCatching { liveSnapshot() }.getOrNull() ?: return
        viewModelScope.launch(guard + Dispatchers.IO) { app.liveDrafts.save(snapshot) }
    }

    /**
     * A live session that was cut off because the app was closed: it comes back as a normal session, ready to be
     * evaluated. Too little speech is not worth keeping.
     */
    internal fun recoverLive() {
        val saved = app.liveDrafts.load() ?: return
        app.liveDrafts.clear()
        val words = saved.studentText.split(Regex("\\s+")).count { it.isNotBlank() }
        if (words < MIN_RECOVER_WORDS) {
            if (app.store.get(saved.id) == null) (0..2).forEach { app.store.audioFile(saved.id, it).delete() }
            return
        }
        (0..2).forEach { part -> runCatching { de.abicoach.audio.Wav.repair(app.store.audioFile(saved.id, part)) } }
        app.store.save(saved.copy(tech = joinTech(saved.tech, "Hinweis: Diese Übung wurde unterbrochen (die App wurde geschlossen) und aus der Sicherheitskopie wiederhergestellt.")))
        de.abicoach.data.CrashLog.breadcrumb("Übung wiederhergestellt: ${saved.id}")
        recoveredSession = saved.id
    }

    /** The id of a session that was restored after the app was closed in the middle (for a banner on the start page). */
    var recoveredSession by mutableStateOf<String?>(null)

    /** The report of a crash in the last run that the student has not seen yet. */
    var crashReport by mutableStateOf<String?>(null)
        private set

    fun dismissCrash() {
        de.abicoach.data.CrashLog.markSeen()
        crashReport = null
    }

    /** For the emulator test: shows the messages of the safety net (a rescued session, a crash report, the backup reminder). */
    fun demoSystem(on: Boolean) {
        if (on) {
            recoverLive()
            crashReport = "ABSTURZ am 12.11.2026 00:41:07\nAbiCoach 1.0.44 · Android 16 (API 36) · Beispiel-Handy\nThread: main\n\njava.lang.IllegalStateException: Beispiel für einen Fehlerbericht\n\tat de.abicoach.Beispiel.zeile(Beispiel.kt:42)\nDavor:\n12.11.2026 00:41:02 verlässt Seite Root\n"
            backupDue = true
        } else {
            recoveredSession = null
            crashReport = null
            backupDue = false
        }
    }

    /** The most important language mistakes of a session for the correction round: serious or recurring ones, no duplicates, at most three. */
    fun correctionTargets(record: SessionRecord): List<LiveError> {
        val counts = app.store.categoryCounts()
        val rank = mapOf("major" to 0, "minor" to 1)
        return record.liveErrors
            .filter { it.quote.isNotBlank() && it.correction.isNotBlank() && it.severity != "content" && it.category != "CONTENT" }
            .filter { it.severity == "major" || (counts[it.category] ?: 0) >= 3 }
            .sortedBy { rank[it.severity] ?: 2 }
            .distinctBy { e -> e.correction.lowercase().filter { c -> c.isLetterOrDigit() } }
            .take(3)
    }

    /** Leaves the correction round: to the result if it is ready, otherwise to the waiting page. */
    fun finishCorrections(id: String) {
        sayClose()
        if (app.store.get(id)?.debrief != null) {
            readyDebrief = null
            nav.replaceTop(Screen.Debrief(id))
        } else {
            nav.replaceTop(Screen.Analyzing)
        }
    }

    /** "Zweiter Versuch": the same text, only the weakest task, a short preparation. */
    fun startRetry(recordId: String, taskNumber: Int) {
        val original = app.store.get(recordId) ?: return
        val originalTask = original.task ?: return
        if (busy != null) return
        errorMessage = null
        val feedback = if (original.feedback == FeedbackMode.PRUEFUNG) FeedbackMode.FLUESTERN else original.feedback
        task = originalTask
        notes = original.notes
        tasksDone.clear()
        draft =
            SessionRecord.create(SessionKind.PART1, feedback, original.topicId, originalTask, taskNumber)
                .copy(retryOf = recordId, goals = LearningBrain.goals(app, SessionKind.PART1, taskNumber))
        prepMinutes = 2
        notice = "Zweiter Versuch: derselbe Text, nur Aufgabe $taskNumber. Du hast 2 Minuten – dann sprichst du es besser."
        app.feedback.play(FeedbackType.START)
        openPrep()
    }

    /** What went wrong with the repeated task the first time (for the preparation page of a second attempt). */
    fun retryHints(): Pair<de.abicoach.data.TaskReview?, List<LiveError>> {
        val current = draft ?: return null to emptyList()
        if (current.retryOf.isBlank()) return null to emptyList()
        val original = app.store.get(current.retryOf) ?: return null to emptyList()
        val review = original.debrief?.taskReviews?.firstOrNull { it.number == current.focusTask }
        val errors = original.debrief?.errors.orEmpty().filter { it.severity != "content" }.take(3)
        return review to errors
    }

    /**
     * Stops a live session without an evaluation: nothing is kept – no recording, no transcript, and in part 2 of a
     * simulation also not the part 1 already saved.
     */
    fun cancelLive() {
        live?.abort()
        live = null
        app.liveDrafts.clear()
        draft?.let { d ->
            if (app.store.get(d.id) != null) app.store.delete(d.id) else (0..2).forEach { app.store.audioFile(d.id, it).delete() }
        }
        app.feedback.play(FeedbackType.STOP)
        draft = null
        busy = null
        nav.resetTo()
    }

    fun cancelPrep() {
        app.drafts.clear()
        PrepAlarm.cancel(app)
        draft = null
        task = null
        readyMaterialMinutes = 0
        nav.pop()
    }

    // ---------- analysis ----------

    // ---------- AI daily plan ----------

    var briefingBusy by mutableStateOf(false)
        private set
    var briefingError by mutableStateOf<String?>(null)
        private set

    /** Makes today's plan if there is none yet (or after a new evaluated session); [force] renews it. */
    fun refreshBriefing(force: Boolean = false) {
        if (briefingBusy || !de.abicoach.coach.BriefingMaker.worthIt(app)) return
        val current = app.briefings.current.value
        if (!force && current != null && current.signature == de.abicoach.coach.BriefingMaker.signature(app)) return
        briefingBusy = true
        briefingError = null
        viewModelScope.launch(guard) {
            try {
                val made = withContext(Dispatchers.Default) { de.abicoach.coach.BriefingMaker.make(app) }
                app.briefings.save(made)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                briefingError = "Der Tagesplan konnte nicht erstellt werden: ${e.message}"
            } finally {
                briefingBusy = false
            }
        }
    }

    /** Opens the coach chat with a question already asked (e.g. from the debrief). */
    fun askCoach(question: String) {
        tap()
        nav.push(Screen.Ask)
        ask(question)
    }

    /** Starts the evaluation as background work: it keeps running when the app is closed. */
    fun analyze(id: String) {
        if (app.store.get(id) == null) return
        errorMessage = null
        readyDebrief = null
        app.analysis.begin(id)
        de.abicoach.session.AnalysisScheduler.enqueue(app, id)
    }

    /** Stops a running evaluation – the background job and the work in the cloud. The session stays and can be evaluated later. */
    fun cancelAnalysis(id: String) {
        tap()
        de.abicoach.session.AnalysisScheduler.cancel(app, id)
        app.analysis.cancel(id)
        readyDebrief = null
        viewModelScope.launch(guard + Dispatchers.IO) { runCatching { de.abicoach.session.CloudAnalysis.cancel(app, id) } }
        notice = "Auswertung abgebrochen. Die Sitzung bleibt gespeichert – du kannst sie jederzeit über „Zuletzt“ auswerten."
        if (nav.current == Screen.Analyzing) nav.pop()
    }

    /**
     * A tap on a session: its result – or, if it was not evaluated yet, the page where the student decides whether
     * to evaluate or delete it. Nothing is evaluated without being asked (only a running evaluation is shown).
     */
    fun openSession(id: String) {
        tap()
        val status = app.analysis.status.value
        if (status.id == id && status.error == null) nav.push(Screen.Analyzing) else nav.push(Screen.Debrief(id))
    }

    fun openAnalysis(id: String) {
        nav.push(Screen.Analyzing)
        val status = app.analysis.status.value
        // Already running (e.g. opened from the notification): just show the progress.
        if (status.id == id && status.error == null) return
        analyze(id)
    }

    /** A tap on the "Auswertung fertig" notification. */
    fun openFromNotification(id: String) {
        val record = app.store.get(id) ?: return
        if (record.debrief != null) nav.push(Screen.Debrief(id)) else openAnalysis(id)
    }

    /** Reacts when the background evaluation finishes: jump to the result or show a banner. */
    private fun watchAnalysis() {
        viewModelScope.launch(guard) {
            app.analysis.status.collect { s ->
                val finished = s.finishedId ?: return@collect
                app.analysis.consumeFinished()
                app.pendingNotice?.let { notice = it }
                app.pendingNotice = null
                if (app.isVisible) app.feedback.play(de.abicoach.audio.Feedback.forPoints(s.points))
                if (nav.current == Screen.Analyzing) {
                    nav.replaceTop(Screen.Debrief(finished))
                } else {
                    readyDebrief = finished to s.points
                }
            }
        }
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
        viewModelScope.launch(guard) {
            try {
                val created = withContext(Dispatchers.IO) { app.importer.import(files, kind, title.trim(), halbjahr) }
                if (created.isEmpty()) {
                    errorMessage = "Die Dateien konnten nicht gelesen werden."
                } else {
                    app.feedback.play(FeedbackType.ENABLE)
                    notice =
                        if (app.ai.ready) {
                            "${created.size} ${if (created.size == 1) "Dokument" else "Dokumente"} hinzugefügt – die KI liest jetzt im Hintergrund."
                        } else {
                            "Gespeichert. Sobald dein ${app.ai.provider.company}-Schlüssel eingetragen ist, liest die KI alles ein."
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
        if (!app.ai.ready) {
            errorMessage = "Für den KI-Coach brauchst du den ${app.ai.provider.company}-Schlüssel (Einstellungen → KI)."
            return
        }
        val history = askMessages.map { (if (it.fromUser) "user" else "assistant") to it.text }
        askMessages += AskMessage(true, q)
        askBusy = true
        viewModelScope.launch(guard) {
            try {
                val answer = withContext(Dispatchers.Default) { MaterialQuestion.ask(app, q, history) }
                askMessages += AskMessage(false, answer.text, answer.hits.mapIndexed { i, h -> "[${i + 1}] ${h.citation}" }, answer.by, answer.actions)
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

    /** Runs a button the coach suggested under its answer. */
    fun runAction(action: de.abicoach.session.CoachAction) {
        if (action.kind == "wortschatz") return openVocab()
        if (action.kind == "aussprache") return openPronunciation()
        tap()
        val f = app.settings.defaultFeedback
        when (action.kind) {
            "aufgabe1", "aufgabe2", "aufgabe3" -> nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.PART1, action.kind.last().digitToInt(), f)))
            "vortrag" -> nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.PART1, 0, f)))
            "gespraech" -> nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.PART2, 0, f).copy(topicChoice = action.arg.toIntOrNull() ?: Knowledge.FROM_COURSE)))
            "einstufung" -> nav.push(Screen.PlacementIntro)
            "sprint" -> nav.push(Screen.Setup(SessionSetup.sprint(f)))
            "simulation" -> nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.SIMULATION, 0, f)))
            "level" -> nav.push(Screen.Level(action.arg))
            "training" -> openDrills()
            "dossier" -> action.arg.toIntOrNull()?.let { openDossier(it) }
            "echt" -> openRealTasks()
        }
    }

    // ---------- vocabulary ----------

    /** The cards of the current review round; the first one is shown. */
    val vocabQueue = mutableStateListOf<de.abicoach.practice.VocabCard>()
    var vocabRevealed by mutableStateOf(false)
    var vocabDone by mutableStateOf(0)
        private set

    fun openVocab() {
        tap()
        vocabQueue.clear()
        vocabQueue.addAll(app.vocab.due())
        vocabRevealed = false
        vocabDone = 0
        nav.push(Screen.Vocab)
    }

    fun gradeCard(grade: de.abicoach.practice.Grade) {
        val card = vocabQueue.firstOrNull() ?: return
        val next = de.abicoach.practice.Scheduler.review(card, grade)
        app.vocab.update(next)
        vocabQueue.removeAt(0)
        // "Nochmal" comes back at the end of this round.
        if (grade == de.abicoach.practice.Grade.AGAIN) vocabQueue.add(next)
        vocabDone++
        vocabRevealed = false
        app.feedback.play(if (grade == de.abicoach.practice.Grade.AGAIN) FeedbackType.WRONG else FeedbackType.CORRECT)
        if (vocabQueue.isEmpty()) app.feedback.play(FeedbackType.RESULT_GOOD)
    }

    fun deleteCard(id: String) {
        app.vocab.delete(id)
        vocabQueue.removeAll { it.id == id }
        vocabRevealed = false
    }

    // ---------- pronunciation lab ----------

    var pronTarget by mutableStateOf("")
    var pronRecording by mutableStateOf(false)
        private set
    var pronBusy by mutableStateOf(false)
        private set
    var pronResult by mutableStateOf<de.abicoach.practice.PronunciationCheck.Result?>(null)
        private set
    private var recorder: de.abicoach.practice.ShortRecorder? = null

    fun openPronunciation(word: String = "") {
        tap()
        if (word.isNotBlank()) pronTarget = word
        pronResult = null
        nav.push(Screen.Pronunciation)
    }

    fun pronSelect(word: String) {
        tap()
        pronTarget = word
        pronResult = null
    }

    fun pronStart() {
        if (pronBusy || pronRecording || pronTarget.isBlank()) return
        if (!app.ai.ready) {
            errorMessage = "Für das Aussprache-Labor brauchst du den ${app.ai.provider.company}-Schlüssel (Einstellungen → KI)."
            return
        }
        if (app.ai.provider == de.abicoach.ai.Provider.ANTHROPIC && notice == null) {
            notice = "Mit Claude hört die Spracherkennung des Handys zu: Sie prüft, ob dein Wort verstanden wird – feine Laute hört nur Google oder OpenAI."
        }
        stopSpeaking()
        recorder?.release()
        recorder = de.abicoach.practice.ShortRecorder().also { it.start() }
        pronRecording = true
        pronResult = null
        app.feedback.play(FeedbackType.START)
    }

    fun pronStop() {
        val r = recorder ?: return
        val wav = r.stop()
        r.release()
        recorder = null
        pronRecording = false
        app.feedback.play(FeedbackType.STOP)
        if (wav.size < 44 + 16_000) {
            errorMessage = "Die Aufnahme war zu kurz. Tippe auf „Aufnehmen“, sprich und tippe dann auf „Fertig“."
            return
        }
        val target = pronTarget
        pronBusy = true
        viewModelScope.launch(guard) {
            try {
                val result = withContext(Dispatchers.Default) { de.abicoach.practice.PronunciationCheck.check(app, target, wav) }
                pronResult = result
                app.pron.record(target, result.score)
                app.feedback.play(de.abicoach.audio.Feedback.forPoints(result.score * 15 / 100))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Die Prüfung der Aussprache hat nicht geklappt: ${e.message}"
            } finally {
                pronBusy = false
            }
        }
    }

    /** Words to practise: the pronunciation tips of the last sessions, newest first. */
    fun pronWords(): List<de.abicoach.data.PronTip> =
        app.store.sessions.value.mapNotNull { it.debrief }.flatMap { it.pronunciation }.distinctBy { it.word.lowercase().trim() }.filter { it.word.isNotBlank() }.take(20)

    // ---------- "Sag es richtig" ----------

    fun sayOpen(wrong: String, right: String, why: String = "") {
        tap()
        sayRecorder?.release()
        sayRecorder = null
        sayRecording = false
        sayResult = null
        sayTarget = SayTarget(wrong, right, why)
    }

    fun sayClose() {
        sayRecorder?.release()
        sayRecorder = null
        sayRecording = false
        sayTarget = null
        sayResult = null
        stopSpeaking()
    }

    fun sayStart() {
        if (sayBusy || sayRecording || sayTarget == null) return
        stopSpeaking()
        sayRecorder?.release()
        sayRecorder = de.abicoach.practice.ShortRecorder().also { it.start() }
        sayRecording = true
        sayResult = null
        app.feedback.play(FeedbackType.START)
    }

    fun sayStop() {
        val recorder = sayRecorder ?: return
        val wav = recorder.stop()
        recorder.release()
        sayRecorder = null
        sayRecording = false
        app.feedback.play(FeedbackType.STOP)
        val target = sayTarget ?: return
        if (wav.size < 44 + 16_000) {
            sayResult = SayItCheck.Result(false, "", "Die Aufnahme war zu kurz. Tippe auf „Aufnehmen“, sag den Satz und tippe dann auf „Fertig“.", "")
            return
        }
        sayBusy = true
        viewModelScope.launch(guard) {
            try {
                val result = withContext(Dispatchers.Default) { SayItCheck.check(app, target, wav) }
                if (sayTarget == target) {
                    sayResult = result
                    if (result.ok) {
                        if (target.right !in sayDone) sayDone.add(target.right)
                        app.feedback.play(FeedbackType.CORRECT)
                    } else {
                        app.feedback.play(FeedbackType.WRONG)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (sayTarget == target) sayResult = SayItCheck.Result(false, "", "Das hat nicht geklappt: ${e.message}", "")
            } finally {
                sayBusy = false
            }
        }
    }

    // ---------- system check ----------

    fun runSystemCheck() {
        if (systemChecking) return
        systemChecking = true
        systemCheck = emptyList()
        viewModelScope.launch(guard) {
            try {
                withContext(Dispatchers.Default) { SystemCheck(app).runAll { systemCheck = it } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Der Systemtest wurde abgebrochen: ${e.message}"
            } finally {
                systemChecking = false
            }
        }
    }

    // ---------- keys ----------

    fun checkKey(provider: String) {
        keyChecking = provider
        viewModelScope.launch(guard) {
            val result = app.ai.checkKey(de.abicoach.ai.Provider.of(provider))
            keyChecks = keyChecks + (provider to result)
            keyChecking = null
            if (provider == app.ai.provider.id && result == null) app.resumeReading()
        }
    }

    fun keySaved(provider: String) {
        keyChecks = keyChecks - provider
        if (provider == app.ai.provider.id) app.resumeReading()
    }

    /** Switches the company whose AI does everything; material waiting for a key or new vectors goes on. */
    fun chooseProvider(provider: de.abicoach.ai.Provider) {
        if (provider == app.settings.provider) return
        tap()
        stopSpeaking()
        app.settings.provider = provider
        app.resumeReading()
        if (app.ai.ready) de.abicoach.memory.DossierScheduler.kick(app)
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

    // ---------- learning system: error memory, speaking training, dossiers, real exam tasks ----------

    /**
     * What the examiner of a practice conversation should know: the student's persistent error patterns and his level,
     * so that he elicits exactly these structures and asks at the right difficulty. Never in an exam simulation.
     */
    /** The student's top error patterns for the examiner, with what the Fehler-Werkstatt tutor found still hard. */
    private fun examinerLines(limit: Int): List<String> = app.errorMemory.examinerLines(limit) { key -> app.workshops.get(key)?.stillHard.orEmpty() }

    private fun patternBlockFor(feedback: FeedbackMode): String {
        if (feedback == FeedbackMode.PRUEFUNG) return ""
        val patterns = Prompts.patternBlock(examinerLines(5), feedback == FeedbackMode.COACH) + Prompts.habitBlock(app.insights.weaknesses(3).map { it.text })
        val level =
            app.profiles.profile.value.prognosis()?.let { p ->
                "\n\nThe student's current level is about ${p.points} of 15 points. " +
                    if (p.points >= 11) {
                        "Challenge him: demanding AFB III questions, counter-positions and links between topic fields."
                    } else {
                        "Start with clear, concrete questions and raise the level step by step (describe → explain → compare → evaluate)."
                    }
            }.orEmpty()
        return patterns + level
    }

    /** The model version at 15-point level is being written for this session. */
    var modelBusy by mutableStateOf<String?>(null)
        private set

    fun makeModel(id: String) {
        val record = app.store.get(id) ?: return
        if (record.debrief == null || modelBusy != null) return
        if (!app.ai.ready) {
            errorMessage = "Dafür brauchst du den ${app.ai.provider.company}-Schlüssel (Einstellungen → KI)."
            return
        }
        modelBusy = id
        viewModelScope.launch(guard) {
            try {
                val parts = withContext(Dispatchers.Default) { de.abicoach.session.ModelTalk.make(app, record) }
                app.store.get(id)?.let { r -> r.debrief?.let { d -> app.store.save(r.copy(debrief = d.copy(model = parts))) } }
                app.feedback.play(FeedbackType.ENABLE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Die Musterfassung konnte nicht erstellt werden: ${e.message}"
            } finally {
                modelBusy = null
            }
        }
    }

    private fun joinTech(a: String, b: String): String = listOf(a, b).filter { it.isNotBlank() }.joinToString("\n\n")

    /** The error pattern the student set out to avoid in this session (the live hints give it priority). */
    private fun goalPattern(record: SessionRecord): String = record.goals.firstOrNull { it.kind == "pattern" }?.target.orEmpty()

    /** The exercises of the current speaking training. */
    val drills = mutableStateListOf<Drill>()
    var drillIndex by mutableIntStateOf(0)
        private set
    var drillLoading by mutableStateOf(false)
        private set
    var drillRecording by mutableStateOf(false)
        private set
    var drillBusy by mutableStateOf(false)
        private set
    var drillResult by mutableStateOf<DrillCheck.Result?>(null)
        private set

    /** First answers of this round (they count for the error memory). */
    val drillOutcomes = mutableStateListOf<DrillOutcome>()

    /** The error patterns this round trains. */
    var drillFocus by mutableStateOf<List<String>>(emptyList())
        private set
    private var drillRecorder: de.abicoach.practice.ShortRecorder? = null
    private var drillJob: Job? = null
    private val drillRetried = HashSet<String>()

    val drillDone: Boolean get() = drills.isNotEmpty() && drillIndex >= drills.size

    /** Opens the speaking training: the given patterns, or the ones that are due (else the most important ones). */
    fun openDrills(patternKeys: List<String> = emptyList()) {
        tap()
        stopSpeaking()
        nav.push(Screen.Drills)
        startDrills(patternKeys)
    }

    fun startDrills(patternKeys: List<String> = emptyList()) {
        drillJob?.cancel()
        drillRecorder?.release()
        drillRecorder = null
        drills.clear()
        drillOutcomes.clear()
        drillRetried.clear()
        drillIndex = 0
        drillResult = null
        drillRecording = false
        drillBusy = false
        val now = System.currentTimeMillis()
        val chosen =
            if (patternKeys.isNotEmpty()) {
                patternKeys.mapNotNull { app.errorMemory.get(it) }
            } else {
                app.errorMemory.due(now).take(4).ifEmpty { app.errorMemory.top(4, now) }
            }
        drillFocus = chosen.map { it.key }
        if (chosen.isEmpty()) return
        drillLoading = true
        drillJob =
            viewModelScope.launch(guard) {
                try {
                    val made = withContext(Dispatchers.Default) { DrillMaker.make(app, chosen, count = if (chosen.size == 1) 5 else 8) }
                    drills.addAll(made)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    drills.addAll(DrillMaker.local(chosen, 6))
                    notice = "Die KI konnte keine neuen Übungen schreiben (${e.message}) – du übst mit deinen eigenen Sätzen."
                } finally {
                    drillLoading = false
                }
            }
    }

    fun drillStart() {
        if (drillBusy || drillRecording || drillDone || drills.isEmpty()) return
        if (!app.ai.ready) {
            errorMessage = "Für das Sprech-Training brauchst du den ${app.ai.provider.company}-Schlüssel (Einstellungen → KI)."
            return
        }
        stopSpeaking()
        drillRecorder?.release()
        drillRecorder = de.abicoach.practice.ShortRecorder().also { it.start() }
        drillRecording = true
        drillResult = null
        app.feedback.play(FeedbackType.START)
    }

    fun drillStop() {
        val recorder = drillRecorder ?: return
        val wav = recorder.stop()
        recorder.release()
        drillRecorder = null
        drillRecording = false
        app.feedback.play(FeedbackType.STOP)
        val drill = drills.getOrNull(drillIndex) ?: return
        if (wav.size < 44 + 16_000) {
            drillResult = DrillCheck.Result(false, "", "Die Aufnahme war zu kurz. Tippe auf „Sprechen“, sag den Satz und tippe dann auf „Fertig“.", "")
            return
        }
        drillBusy = true
        viewModelScope.launch(guard) {
            try {
                val result = withContext(Dispatchers.Default) { DrillCheck.check(app, drill, wav) }
                if (drills.getOrNull(drillIndex)?.id == drill.id) {
                    drillResult = result
                    if (drillOutcomes.none { it.drill.id == drill.id }) drillOutcomes += DrillOutcome(drill, result.ok, result.heard, result.tip)
                    app.feedback.play(if (result.ok) FeedbackType.CORRECT else FeedbackType.WRONG)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                drillResult = DrillCheck.Result(false, "", "Das hat nicht geklappt: ${e.message}", "")
            } finally {
                drillBusy = false
            }
        }
    }

    /** Next exercise; a wrong one comes back once at the end of the round. */
    fun drillNext() {
        val drill = drills.getOrNull(drillIndex) ?: return
        val result = drillResult
        if (result != null && !result.ok && drill.id !in drillRetried && !drill.id.endsWith("-again")) {
            drillRetried += drill.id
            drills.add(drill.copy(id = drill.id + "-again"))
        }
        drillResult = null
        drillIndex++
        if (drillDone) finishDrills()
    }

    fun drillSkip() {
        drillResult = null
        drillIndex++
        if (drillDone) finishDrills()
    }

    /** The first answers go into the error memory: a pattern counts as right if all its first answers were right. */
    private fun finishDrills() {
        app.feedback.play(FeedbackType.RESULT_GOOD)
        val now = System.currentTimeMillis()
        drillOutcomes.filter { !it.drill.id.endsWith("-again") }.groupBy { it.drill.patternKey }.forEach { (key, list) ->
            app.errorMemory.reviewDrill(key, list.all { it.ok }, now)
        }
    }

    /** Opens the Fehler-Werkstatt for one pattern, or for the most urgent one. */
    fun openWorkshop(patternKey: String? = null) {
        tap()
        stopSpeaking()
        workshop.open(patternKey)
        if (nav.current != Screen.Workshop) nav.push(Screen.Workshop)
    }

    fun closeWorkshop() {
        tap()
        workshop.close()
        stopSpeaking()
        if (nav.current == Screen.Workshop) nav.pop()
    }

    fun closeDrills() {
        drillJob?.cancel()
        drillRecorder?.release()
        drillRecorder = null
        drillRecording = false
        stopSpeaking()
        nav.pop()
    }

    /** The end-to-end microphone test (Settings → System prüfen). */
    var micTest by mutableStateOf<MicTest?>(null)
        private set

    fun openMicTest() {
        micTest?.stop()
        micTest = MicTest(app).also { it.start() }
    }

    fun closeMicTest() {
        micTest?.stop()
        micTest = null
    }

    var dossierBusy by mutableStateOf<Int?>(null)
        private set

    fun openDossier(topic: Int) {
        tap()
        nav.push(Screen.Dossier(topic))
    }

    /** Builds or renews the dossier of one topic field now. */
    fun makeDossier(topic: Int) {
        if (dossierBusy != null) return
        if (!app.ai.ready) {
            errorMessage = "Für die Dossiers brauchst du den ${app.ai.provider.company}-Schlüssel (Einstellungen → KI)."
            return
        }
        dossierBusy = topic
        viewModelScope.launch(guard) {
            try {
                withContext(Dispatchers.Default) { DossierMaker.make(app, topic) }
                app.feedback.play(FeedbackType.ENABLE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Das Dossier konnte nicht erstellt werden: ${e.message}"
            } finally {
                dossierBusy = null
            }
        }
    }

    fun refreshDossiers() {
        DossierScheduler.kick(app)
        notice = "Die Dossiers werden im Hintergrund erstellt und aktualisiert – das dauert pro Themenfeld etwa eine halbe Minute."
    }

    fun openRealTasks() {
        tap()
        nav.push(Screen.RealTasks)
    }

    /** One original exam task: as a presentation (part 1) or as a whole exam. */
    fun startRealTask(unitId: String, whole: Boolean) {
        tap()
        val setup =
            if (whole) {
                SessionSetup.defaults(SessionKind.SIMULATION, 0, app.settings.defaultFeedback).copy(source = Knowledge.Source.REAL, unitId = unitId)
            } else {
                SessionSetup.defaults(SessionKind.PART1, 0, app.settings.defaultFeedback).copy(source = Knowledge.Source.REAL, unitId = unitId, prepMinutes = 20)
            }
        nav.push(Screen.Setup(setup))
    }

    // ---------- demo hooks for the emulator test (screenshots without network) ----------

    fun demoPrep(demoTask: ExamTask, kind: SessionKind, focusTask: Int = 0, minutes: Int = 30) {
        task = demoTask
        notes = "• Summary: Lagos, young creatives, Nollywood\n• Analysis: optimistic tone, contrasts\n• Comment: link to class (Adichie)"
        prepEndsAt = System.currentTimeMillis() + minutes * 60_000L
        draft = SessionRecord.create(kind, FeedbackMode.FLUESTERN, demoTask.topicId, demoTask, focusTask).copy(goals = LearningBrain.goals(app, kind, focusTask))
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
        if (draft == null) draft = SessionRecord.create(SessionKind.PART2, FeedbackMode.FLUESTERN, 4, null).copy(goals = LearningBrain.goals(app, SessionKind.PART2, 0))
        nav.push(Screen.Live)
    }

    fun demoPronResult(result: de.abicoach.practice.PronunciationCheck.Result) {
        pronResult = result
    }

    fun demoDrills(list: List<Drill>, index: Int = 0, result: DrillCheck.Result? = null) {
        drillJob?.cancel()
        drills.clear()
        drills.addAll(list)
        drillIndex = index
        drillResult = result
        drillLoading = false
        drillFocus = list.map { it.patternKey }.distinct()
        nav.push(Screen.Drills)
    }

    fun demoClear() {
        live = null
        draft = null
        task = null
        nav.openTab(Tab.WEG)
    }

    // ---------- reading aloud ----------

    /**
     * Reads an English sentence aloud with the voice of the chosen AI. The speech is streamed (playback starts with
     * the first piece) and kept, so the second tap plays at once. If the AI's voice does not come within three
     * seconds, Android's own voice reads instead – and the AI's voice is kept for the next time.
     */
    fun speak(text: String, voice: String? = null) {
        stopSpeaking()
        speaking = text
        speechJob =
            viewModelScope.launch(guard) {
                try {
                    if (!app.ai.ready || app.ai.provider == de.abicoach.ai.Provider.ANTHROPIC) {
                        androidTts.say(text)
                        kotlinx.coroutines.delay(600L + text.length * 70L)
                        return@launch
                    }
                    val player = de.abicoach.audio.PcmPlayer(24_000, android.media.AudioAttributes.USAGE_MEDIA)
                    player.start()
                    speechPlayer = player
                    val first = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    var phoneVoice = false
                    val synth =
                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            val pcm =
                                app.ai.speakStream(text, voice = voice) { piece ->
                                    if (phoneVoice) return@speakStream
                                    player.enqueue(piece)
                                    first.complete(true)
                                }
                            if (pcm == null) first.complete(false)
                        }
                    val voiced = kotlinx.coroutines.withTimeoutOrNull(FIRST_SPEECH_MS) { first.await() } ?: false
                    if (!voiced) {
                        phoneVoice = true
                        player.stop()
                        speechPlayer = null
                        androidTts.say(text)
                        kotlinx.coroutines.delay(600L + text.length * 70L)
                        return@launch
                    }
                    synth.join()
                    // A moment of silence at the end, so that nothing clicks when the player stops.
                    player.enqueue(ByteArray(4_800))
                    kotlinx.coroutines.delay(player.remainingMs() + 150)
                    player.stop()
                    if (speechPlayer === player) speechPlayer = null
                } finally {
                    if (speaking == text) speaking = null
                }
            }
    }

    // ---------- update inside the app ----------

    fun checkUpdate() {
        tap()
        viewModelScope.launch(guard) { de.abicoach.update.AppUpdater.check(app) }
    }

    /** Downloads and installs [release]; the installation goes on in Android's installer. */
    fun installUpdate(release: de.abicoach.update.AppUpdater.Release) {
        tap()
        viewModelScope.launch(guard) { de.abicoach.update.AppUpdater.update(app, release) }
    }

    /** The open area of the settings (SettingsSection name), null = the overview. */
    private var settingsSectionState by mutableStateOf<String?>(null)

    var settingsSection: String?
        get() = settingsSectionState
        set(value) {
            // Changing the area of the settings is like leaving a page: a sample that is playing stops.
            if (value != settingsSectionState) stopSpeaking()
            settingsSectionState = value
        }

    /** "Mitscrollen" in live sessions: the page follows the transcript (the choice is kept for the next session). */
    var liveFollow by mutableStateOf(app.settings.liveFollow)
        private set

    fun toggleLiveFollow() {
        tap()
        liveFollow = !liveFollow
        app.settings.liveFollow = liveFollow
    }

    // ---------- backup of the learning progress ----------

    /** What the backup is doing right now (shown on the settings page), null when idle. */
    var backupBusy by mutableStateOf<String?>(null)
        private set

    /** A backup file the student picked, waiting for the confirmation to load it. */
    var pendingRestore by mutableStateOf<Pair<android.net.Uri, de.abicoach.data.Backup.Info>?>(null)
        private set

    /** A real grade for speaking from school, to check the app's forecast against. */
    fun addRealGrade(points: Int, label: String) {
        app.profiles.update { it.withRealGrade(points, label, System.currentTimeMillis()) }
    }

    fun removeRealGrade(at: Long) {
        app.profiles.update { it.withoutRealGrade(at) }
    }

    /** True when the learning progress has grown for a while without being saved as a file. */
    var backupDue by mutableStateOf(false)
        private set

    private fun checkBackupDue() {
        val now = System.currentTimeMillis()
        val graded = app.store.graded()
        val since = maxOf(app.settings.lastBackupAt, graded.minOfOrNull { it.startedAt } ?: now)
        val newSessions = graded.count { it.startedAt > app.settings.lastBackupAt }
        backupDue = newSessions >= BACKUP_MIN_SESSIONS && now - since > BACKUP_REMIND_DAYS * 86_400_000L && now > app.settings.backupSnoozeUntil
    }

    fun backupDueText(): String =
        if (app.settings.lastBackupAt == 0L) {
            "Dein Lernstand liegt nur auf diesem Handy. Sichere ihn als Datei, damit er bei einem Defekt oder Handywechsel nicht verloren geht – tippe hier."
        } else {
            val days = ((System.currentTimeMillis() - app.settings.lastBackupAt) / 86_400_000L).toInt()
            "Deine letzte Sicherung ist $days Tage alt. Tippe hier, um den Lernstand neu zu sichern."
        }

    /** "Not now": the reminder comes back in a week. */
    fun snoozeBackup() {
        app.settings.backupSnoozeUntil = System.currentTimeMillis() + 7 * 86_400_000L
        backupDue = false
    }

    /** Text for the settings: how much room the recordings take. */
    fun audioUsage(): String {
        val mb = app.store.audioBytes() / 1_048_576.0
        return if (mb < 1) "Aufnahmen: unter 1 MB" else "Aufnahmen: ${"%.0f".format(mb)} MB"
    }

    /** Deletes old recordings now, after the number of days was changed. */
    fun tidyAudioNow() {
        viewModelScope.launch(guard + Dispatchers.IO) {
            val freed = app.store.tidyAudio(app.settings.keepAudioDays)
            if (freed > 1_048_576L) notice = "${freed / 1_048_576L} MB alte Aufnahmen gelöscht. Auswertungen und Mitschriften bleiben."
        }
    }

    fun exportBackup(uri: android.net.Uri, withMaterial: Boolean) {
        backupBusy = "Sichere deinen Lernstand …"
        viewModelScope.launch(guard) {
            try {
                val info =
                    withContext(Dispatchers.IO) {
                        app.contentResolver.openOutputStream(uri)?.use { de.abicoach.data.Backup.export(app, it, withMaterial) }
                            ?: throw IllegalStateException("Die Datei konnte nicht geschrieben werden.")
                    }
                app.settings.lastBackupAt = System.currentTimeMillis()
                backupDue = false
                notice = "Lernstand gesichert: ${info.sessions} Sitzungen, Lernprofil, Fehlergedächtnis und Einstellungen" + if (withMaterial) " – mit deinem Kursmaterial." else "."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Sichern hat nicht geklappt: ${e.message}"
            } finally {
                backupBusy = null
            }
        }
    }

    fun inspectBackup(uri: android.net.Uri) {
        viewModelScope.launch(guard) {
            val info = withContext(Dispatchers.IO) { runCatching { app.contentResolver.openInputStream(uri)?.use { de.abicoach.data.Backup.inspect(it) } }.getOrNull() }
            if (info == null) {
                errorMessage = "Diese Datei ist keine AbiCoach-Sicherung."
            } else {
                pendingRestore = uri to info
            }
        }
    }

    fun cancelRestore() {
        pendingRestore = null
    }

    /** Loads the picked backup and restarts the app, so that every part reads the restored progress. */
    fun restoreBackup() {
        val (uri, _) = pendingRestore ?: return
        pendingRestore = null
        backupBusy = "Lade deinen Lernstand …"
        viewModelScope.launch(guard) {
            try {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { de.abicoach.data.Backup.restore(app, it) } ?: throw IllegalStateException("Die Datei konnte nicht gelesen werden.")
                }
                val intent =
                    app.packageManager.getLaunchIntentForPackage(app.packageName)
                        ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (intent != null) app.startActivity(intent)
                Runtime.getRuntime().exit(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Laden hat nicht geklappt: ${e.message}"
                backupBusy = null
            }
        }
    }

    // ---------- first start: setup and guided tour ----------

    /** The guided tour through the app (spotlight on the real screens). */
    val tour = TourController()

    fun openOnboarding() {
        tap()
        if (nav.current != Screen.Onboarding) nav.push(Screen.Onboarding)
    }

    /** The setup is through; [withTour] starts the guided tour right away. */
    fun finishOnboarding(withTour: Boolean) {
        tap()
        app.settings.onboardingDone = true
        nav.openTab(Tab.WEG)
        if (withTour) startTour()
    }

    fun startTour() {
        tap()
        stopSpeaking()
        enterTourStep(0)
    }

    fun tourNext() {
        val i = tour.step ?: return
        tap()
        if (i + 1 >= TourSteps.all.size) endTour() else enterTourStep(i + 1)
    }

    fun tourBack() {
        val i = tour.step ?: return
        tap()
        if (i > 0) enterTourStep(i - 1)
    }

    fun endTour() {
        if (tour.step == null) return
        tap()
        tour.step = null
        app.settings.tourDone = true
    }

    /** Shows the area of a tour step (the overview of the tab, scrolled by the tour itself). */
    private fun enterTourStep(i: Int) {
        val step = TourSteps.all[i]
        step.tab?.let { t ->
            when (t) {
                Tab.TRAINING -> practiceView = 0
                Tab.STATS -> progressView = 0
                else -> Unit
            }
            if (nav.current != Screen.Root(t)) nav.openTab(t)
        }
        tour.step = i
    }

    /** Makes the speech of a sentence the student will probably want to hear, so that the tap plays at once. */
    fun prefetchSpeech(text: String) {
        if (text.isNotBlank()) app.ai.prefetchSpeech(text)
    }

    /**
     * A page is left: nothing keeps talking or listening there – the read-aloud voice stops, and a microphone that
     * is still open on that page (Aussprache-Labor, "Sag es richtig", Sprech-Training, Fehler-Werkstatt) is closed.
     */
    private fun leavePage(left: Screen) {
        stopSpeaking()
        if (pronRecording) {
            recorder?.release()
            recorder = null
            pronRecording = false
        }
        if (sayTarget != null) sayClose()
        if (left == Screen.Drills && drillRecording) {
            drillRecorder?.release()
            drillRecorder = null
            drillRecording = false
        }
        if (left == Screen.Workshop && workshop.recording) workshop.close()
    }

    fun stopSpeaking() {
        speechJob?.cancel()
        speechJob = null
        speechPlayer?.stop()
        speechPlayer = null
        ttsInstance?.stop()
        speaking = null
    }

    // ---------- data ----------

    fun deleteSession(id: String) = deleteSessions(listOf(id))

    /** Deletes sessions completely: record, recording and any evaluation that is still waiting or running. */
    fun deleteSessions(ids: Collection<String>) {
        val gone = ids.toSet()
        if (gone.isEmpty()) return
        gone.forEach { id ->
            de.abicoach.session.AnalysisScheduler.cancel(app, id)
            app.analysis.cancel(id)
            app.store.delete(id)
        }
        viewModelScope.launch(guard + Dispatchers.IO) { gone.forEach { id -> runCatching { de.abicoach.session.CloudAnalysis.cancel(app, id) } } }
        if (readyDebrief?.first in gone) readyDebrief = null
        notice = if (gone.size == 1) "Sitzung gelöscht." else "${gone.size} Sitzungen gelöscht."
        (nav.current as? Screen.Debrief)?.let { if (it.id in gone) nav.pop() }
    }

    override fun onCleared() {
        recorder?.release()
        sayRecorder?.release()
        drillRecorder?.release()
        micTest?.stop()
        live?.abort()
        stopSpeaking()
        ttsInstance?.shutdown()
        super.onCleared()
    }

    init {
        // Last, so that everything above is ready.
        watchAnalysis()
        watchMaterial()
        crashReport = de.abicoach.data.CrashLog.unseenCrash()
        // An error that was caught somewhere in the background is never silent.
        de.abicoach.data.CrashLog.onIssue = { what ->
            if (errorMessage == null) errorMessage = "Im Hintergrund ist ein Fehler aufgetreten ($what). Die App läuft weiter; Einzelheiten unter Einstellungen → Update, Hilfe, Über → Fehlerberichte."
        }
        runCatching { checkBackupDue() }
        runCatching { recoverLive() }.onFailure { de.abicoach.data.CrashLog.nonFatal("Wiederherstellen", it) }
        runCatching { restoreWork() }.onFailure { de.abicoach.data.CrashLog.nonFatal("Vorbereitung wiederherstellen", it) }
        // The safety copy of a running session, every few seconds.
        viewModelScope.launch(guard) {
            while (true) {
                kotlinx.coroutines.delay(LIVE_SAVE_MS)
                saveLiveDraft()
            }
        }
        // A new version on GitHub? Asked by itself at most every 12 hours, quietly (a banner only if there is one).
        if (System.currentTimeMillis() - app.settings.lastUpdateCheck > 12 * 3_600_000L) {
            viewModelScope.launch(guard) { runCatching { de.abicoach.update.AppUpdater.check(app, quiet = true) } }
        }
        // The very first start: the setup shows what the app needs to work (someone who already uses it skips it).
        if (!app.settings.onboardingDone) {
            if (app.ai.ready && app.store.sessions.value.isNotEmpty()) app.settings.onboardingDone = true
            else if (nav.current is Screen.Root) nav.push(Screen.Onboarding)
        }
    }

    companion object {
        val ANY_FEEDBACK = FeedbackMode.entries

        /** How long the AI's voice may take before the phone's own voice reads instead. */
        private const val FIRST_SPEECH_MS = 3_000L

        /** Topic fields that can be added to a conversation (with the main one: up to three, as many as fit in 10–15 minutes). */
        const val MAX_EXTRA_TOPICS = 2

        /** How often the safety copy of a running session is written. */
        private const val LIVE_SAVE_MS = 12_000L

        /** A cut-off session with fewer words than this is not worth keeping. */
        private const val MIN_RECOVER_WORDS = 25

        /** The reminder to save the progress: after this many days and this many new evaluated sessions. */
        private const val BACKUP_REMIND_DAYS = 14
        private const val BACKUP_MIN_SESSIONS = 5
    }
}
