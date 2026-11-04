package de.abicoach

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.abicoach.data.Debrief
import de.abicoach.learn.Drill
import de.abicoach.learn.DrillCheck
import de.abicoach.learn.DrillType
import de.abicoach.learn.ErrorPattern
import de.abicoach.learn.PatternExample
import de.abicoach.memory.DossierArgument
import de.abicoach.memory.DossierItem
import de.abicoach.memory.DossierLink
import de.abicoach.memory.DossierMaker
import de.abicoach.memory.ExamUnit
import de.abicoach.memory.TopicDossier
import de.abicoach.session.LiveDiag
import de.abicoach.data.ExamTask
import de.abicoach.data.FeedbackMode
import de.abicoach.data.LiveError
import de.abicoach.data.Part2Topic
import de.abicoach.data.Phrase
import de.abicoach.data.PronTip
import de.abicoach.data.Progress
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import de.abicoach.data.TaskItem
import de.abicoach.data.TaskReview
import de.abicoach.data.Upgrade
import de.abicoach.data.Utterance
import de.abicoach.memory.Chunk
import de.abicoach.memory.CourseDoc
import de.abicoach.memory.Digest
import de.abicoach.memory.DocKind
import de.abicoach.memory.DocStatus
import de.abicoach.memory.ExamTextRef
import de.abicoach.memory.Fact
import de.abicoach.memory.KlausurInfo
import de.abicoach.memory.PageText
import de.abicoach.memory.Vocab
import de.abicoach.profile.ForecastPoint
import de.abicoach.profile.Profile
import de.abicoach.profile.Skill
import de.abicoach.profile.SkillState
import de.abicoach.session.LiveUiState
import de.abicoach.ui.Screen
import de.abicoach.ui.SessionSetup
import de.abicoach.ui.components.Tab
import de.abicoach.ui.theme.ThemeMode
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.Locale

/**
 * Clicks through AbiCoach on a virtual phone and saves screenshots (GitHub Action "smoke",
 * pictures end up on the branch "ci-smoke"). Uses demo data, no network.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SmokeTest {
    @get:Rule(order = 0)
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val watcher =
        object : TestWatcher() {
            override fun failed(e: Throwable?, description: Description?) {
                Log.e(TAG, "Test fehlgeschlagen: ${description?.methodName}", e)
                runCatching { shot("FEHLER_${description?.methodName ?: "test"}") }
            }
        }

    private val app: AbiCoachApp get() = compose.activity.application as AbiCoachApp

    private fun ui(block: () -> Unit) {
        compose.runOnUiThread { block() }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(700)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val scaled = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
        counter++
        val file = File(screenDir(), String.format(Locale.ROOT, "%03d_%s.png", counter, name))
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "Bild: ${file.name}")
    }

    private fun screenDir(): File {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens")
        dir.mkdirs()
        return dir
    }

    private fun scrollShot(tag: String, index: Int, name: String) {
        runCatching { compose.onNodeWithTag(tag).performScrollToIndex(index) }
        shot(name)
    }

    private fun swipeShot(tag: String, name: String, times: Int = 1) {
        repeat(times) { runCatching { compose.onNodeWithTag(tag).performTouchInput { swipeUp() } } }
        shot(name)
    }

    @Test
    fun a_freshStart() {
        ui {
            app.store.deleteAll()
            app.profiles.reset()
            app.insights.reset()
            app.path.reset()
            app.briefings.clear()
            app.memory.docs.value.forEach { app.memory.delete(it.id) }
            app.settings.themeMode = ThemeMode.LIGHT
            app.settings.geminiKey = ""
            compose.activity.vm.nav.openTab(Tab.WEG)
        }
        shot("start_abiweg")
        compose.onNodeWithTag("tab_training").performClick()
        shot("start_training")
        compose.onNodeWithTag("tab_kurs").performClick()
        shot("start_kurs")
        compose.onNodeWithTag("tab_stats").performClick()
        shot("start_statistik")
        compose.onNodeWithTag("tab_weg").performClick()
        compose.onNodeWithTag("action_settings").performClick()
        shot("start_einstellungen")
        ui { compose.activity.vm.nav.push(Screen.PlacementIntro) }
        shot("start_einstufung")
    }

    @Test
    fun b_withDemoData() {
        ui { Demo.seed(app) }
        val vm = compose.activity.vm
        ui { vm.nav.openTab(Tab.WEG) }
        shot("abiweg")
        swipeShot("list_weg", "abiweg_weg", 1)
        swipeShot("list_weg", "abiweg_welten", 1)
        swipeShot("list_weg", "abiweg_unten", 3)
        ui { vm.nav.push(Screen.Level("w3.perceive")) }
        shot("level_perceive")
        swipeShot("level", "level_perceive_unten", 2)
        ui { vm.nav.pop(); vm.nav.push(Screen.Level("w1.ablauf")) }
        shot("level_lernen")
        ui { vm.nav.pop(); vm.nav.push(Screen.Level("t4.wissen")) }
        shot("level_themenfeld")
        swipeShot("level", "level_themenfeld_unten", 1)
        ui { vm.nav.pop() }

        ui { vm.nav.openTab(Tab.TRAINING) }
        shot("training")
        swipeShot("list_training", "training_unten", 2)

        ui { vm.nav.openTab(Tab.KURS) }
        shot("kurs")
        swipeShot("list_kurs", "kurs_mitte", 1)
        swipeShot("list_kurs", "kurs_unten", 2)
        ui { vm.nav.push(Screen.Doc(Demo.BOOK)) }
        shot("dokument_buch")
        swipeShot("doc", "dokument_buch_unten", 2)
        ui { vm.nav.pop(); vm.nav.push(Screen.Doc(Demo.KLAUSUR)) }
        shot("dokument_klausur")
        ui {
            vm.nav.pop()
            vm.clearAsk()
            vm.askMessages += de.abicoach.ui.AskMessage(true, "Wie werde ich in Aufgabe 2 besser?")
            vm.askMessages +=
                de.abicoach.ui.AskMessage(
                    false,
                    "In deinen letzten Auswertungen fehlen bei der Analyse vor allem **Textbelege**. Geh in drei Schritten vor:\n\n1. Behauptung: *The author presents the young people as resourceful.*\n2. Beleg: Zitat mit Zeile (\"Nobody is waiting for permission anymore\", l. 3)\n3. Wirkung: *This quote gives them a confident voice.*\n\nIn eurer Klausur Q1.2 hat deine Lehrkraft genau das angemerkt [2].",
                    listOf("[1] Green Line Oberstufe NRW, S. 142", "[2] Klausur Q1.2 – Brexit, S. 2"),
                    "Gemini 4 Argon",
                    listOf(de.abicoach.session.CoachAction("aufgabe2"), de.abicoach.session.CoachAction("level", "w3.perceive")),
                )
            vm.nav.push(Screen.Ask)
        }
        shot("ki_coach")
        ui { vm.nav.pop() }
        ui { vm.openVocab() }
        shot("wortschatz")
        runCatching { compose.onNodeWithTag("vocab_reveal").performClick() }
        shot("wortschatz_antwort")
        ui { vm.nav.pop(); vm.openPronunciation("thoroughly") }
        ui { vm.demoPronResult(de.abicoach.practice.PronunciationCheck.Result(72, "sorougly", listOf("„th“ klang wie „s“", "Betonung richtig auf der ersten Silbe"), "Leg die Zungenspitze leicht zwischen die Zähne und puste – wie bei „think“.", "Gemini 4 Argon")) }
        shot("aussprache")
        swipeShot("pron", "aussprache_unten", 2)
        ui { vm.nav.pop(); vm.nav.push(Screen.Setup(SessionSetup.sprint(FeedbackMode.FLUESTERN))) }
        shot("einrichten_sprint")
        ui { vm.nav.pop() }

        ui { vm.nav.openTab(Tab.KURS); vm.nav.push(Screen.Dossier(4)) }
        shot("dossier")
        swipeShot("dossier", "dossier_unten", 2)
        ui { vm.nav.pop(); vm.nav.push(Screen.Doc(Demo.REAL)) }
        shot("dokument_pruefung")
        ui { vm.nav.pop(); vm.nav.openTab(Tab.TRAINING); vm.nav.push(Screen.RealTasks) }
        shot("echte_aufgaben")
        ui { vm.nav.pop(); vm.nav.push(Screen.Errors) }
        shot("fehlergedaechtnis")
        swipeShot("errors", "fehlergedaechtnis_unten", 2)
        ui { vm.nav.pop(); vm.demoDrills(Demo.drills(), 0) }
        shot("sprech_training")
        ui {
            vm.nav.pop()
            vm.demoDrills(
                Demo.drills(),
                1,
                DrillCheck.Result(false, "Since independence in 1960, Nigeria's population grew enormously", "Fast! Bei einer Entwicklung bis heute brauchst du das present perfect: „has grown“.", "Gemini 3.8 Flash"),
            )
        }
        shot("sprech_training_ergebnis")
        ui { vm.nav.pop() }

        ui { vm.nav.openTab(Tab.STATS) }
        shot("statistik")
        swipeShot("list_stats", "statistik_mitte", 1)
        scrollShot("list_stats", 4, "statistik_lerner_gedaechtnis")
        swipeShot("list_stats", "statistik_unten", 2)

        ui { vm.nav.push(Screen.Debrief(Demo.SIMULATION)) }
        shot("auswertung")
        runCatching { compose.onNodeWithTag("debrief_goals").performScrollTo() }
        shot("auswertung_ziele")
        runCatching { compose.onNodeWithTag("debrief_next").performScrollTo() }
        shot("auswertung_naechster_schritt")
        runCatching { compose.onNodeWithTag("debrief_observations").performScrollTo() }
        shot("auswertung_beobachtungen")
        runCatching { compose.onNodeWithTag("debrief_talk").performScrollTo() }
        shot("auswertung_vortrags_coach")
        swipeShot("debrief", "auswertung_2", 1)
        swipeShot("debrief", "auswertung_3", 1)
        swipeShot("debrief", "auswertung_4", 2)
        swipeShot("debrief", "auswertung_5", 2)

        ui { vm.nav.openTab(Tab.TRAINING); vm.nav.push(Screen.Corrections(Demo.SIMULATION)) }
        shot("korrekturrunde")
        ui { vm.sayOpen("the author describe", "the author describes", "3. Person Singular braucht -s") }
        shot("sag_es_richtig")
        ui { vm.sayClose(); vm.nav.openTab(Tab.TRAINING) }

        ui { vm.nav.openTab(Tab.TRAINING); vm.nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.SIMULATION, 0, FeedbackMode.FLUESTERN))) }
        shot("einrichten_simulation")
        swipeShot("setup", "einrichten_simulation_unten", 2)
        ui { vm.nav.pop(); vm.nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.PART1, 2, FeedbackMode.COACH))) }
        shot("einrichten_aufgabe2")

        ui { vm.nav.openTab(Tab.TRAINING); vm.demoPrep(Demo.task(), SessionKind.SIMULATION) }
        shot("vorbereitung")
        swipeShot("prep", "vorbereitung_unten", 2)

        // Going back keeps the scroll position: the training list stays where it was.
        ui { vm.demoClear(); vm.nav.openTab(Tab.TRAINING) }
        swipeShot("list_training", "zurueck_vorher", 2)
        ui { vm.nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.PART1, 1, FeedbackMode.FLUESTERN))) }
        shot("zurueck_einrichten")
        ui { vm.back() }
        shot("zurueck_nachher_gleiche_stelle")

        ui { vm.demoClear(); vm.demoLive(Demo.liveState(part = 2), part = 2, withExaminer = true) }
        shot("gespraech_live")
        ui { vm.demoClear(); vm.demoPrep(Demo.task(), SessionKind.SIMULATION) }
        ui { vm.demoLive(Demo.liveState(part = 1), part = 1, withExaminer = false) }
        shot("vortrag_live")
        ui { vm.demoClear(); vm.nav.push(Screen.Analyzing) }
        shot("auswertung_laeuft")

        ui { vm.nav.openTab(Tab.WEG); vm.nav.push(Screen.Settings) }
        shot("einstellungen")
        swipeShot("settings", "einstellungen_2", 1)
        swipeShot("settings", "einstellungen_3", 2)

        // Dark mode
        ui { app.settings.themeMode = ThemeMode.DARK; vm.nav.openTab(Tab.WEG) }
        shot("dunkel_abiweg")
        ui { vm.nav.openTab(Tab.KURS) }
        shot("dunkel_kurs")
        ui { vm.nav.openTab(Tab.STATS) }
        shot("dunkel_statistik")
        ui { vm.nav.push(Screen.Debrief(Demo.SIMULATION)) }
        shot("dunkel_auswertung")
        ui { vm.nav.openTab(Tab.TRAINING); vm.demoPrep(Demo.task(), SessionKind.PART1, focusTask = 2, minutes = 8) }
        shot("dunkel_vorbereitung")
        ui { vm.demoClear(); vm.demoLive(Demo.liveState(part = 2), part = 2, withExaminer = true) }
        shot("dunkel_gespraech")
        ui { vm.demoClear(); app.settings.palette = de.abicoach.ui.theme.Palette.WALD; vm.nav.openTab(Tab.WEG) }
        shot("dunkel_wald")
        ui { app.settings.palette = de.abicoach.ui.theme.Palette.STANDARD; app.settings.themeMode = ThemeMode.SYSTEM }
    }

    /** The evaluation runs as background work: it starts, shows its progress and ends in a clear error without keys. */
    @Test
    fun c_backgroundAnalysis() {
        ui { Demo.seed(app) }
        val vm = compose.activity.vm
        val id = "demo-background"
        ui {
            app.store.save(
                SessionRecord.create(SessionKind.PART2, FeedbackMode.FLUESTERN, 4, null).copy(
                    id = id,
                    startedAt = System.currentTimeMillis(),
                    durationSec = 300,
                    utterances = Demo.liveState(2).utterances,
                ),
            )
            vm.openAnalysis(id)
        }
        shot("hintergrund_auswertung_start")
        var seen = false
        repeat(20) {
            if (!seen) {
                Thread.sleep(500)
                val s = app.analysis.status.value
                seen = s.id == id && (s.error != null || s.text.isNotBlank())
            }
        }
        shot("hintergrund_auswertung_danach")
        Log.i(TAG, "Hintergrund-Status: ${app.analysis.status.value}")
        ui { vm.nav.openTab(Tab.WEG) }
        shot("hintergrund_weiter_in_der_app")
    }

    companion object {
        private const val TAG = "AbiCoachSmoke"
        private var counter = 0
    }
}

/** Realistic demo data for the screenshots. */
object Demo {
    const val BOOK = "demo-book"
    const val KLAUSUR = "demo-klausur"
    const val WORKSHEETS = "demo-worksheets"
    const val REAL = "demo-real"
    const val SIMULATION = "demo-simulation"

    private const val DAY = 86_400_000L

    fun task(): ExamTask =
        ExamTask(
            topicId = 4,
            title = "Lagos Rising",
            textType = "newspaper article",
            text =
                "On a humid Saturday morning in Yaba, Lagos, the queue outside a converted warehouse stretches around the block. " +
                    "Inside, twenty-three-year-old Chioma Eze is editing a short film on a borrowed laptop. \"Nobody is waiting for permission anymore,\" she says. " +
                    "Her generation, she explains, has grown up with power cuts, traffic jams and politicians who promise much and deliver little. " +
                    "Yet instead of leaving the country, many of them are building something new.\n\n" +
                    "Nigeria's creative industries – from Nollywood to Afrobeats and fashion – are now among the fastest-growing sectors of the economy. " +
                    "Streaming platforms compete for Nigerian stories, and songs recorded in tiny home studios reach millions of listeners worldwide. " +
                    "For Chioma, success is not only about money. \"When people in London or Houston watch our films, they see Lagos as we see it: loud, chaotic, but full of ideas.\"\n\n" +
                    "Critics warn that the boom hides deep problems. Most young Nigerians still struggle to find stable jobs, and talented people continue to emigrate – " +
                    "a trend locals call \"japa\". Still, the mood in the warehouse is anything but resigned. As one producer puts it: \"We cannot fix the roads, but we can tell our own stories.\"",
            sourceNote = "Neuer Text, passend zu deinem Kurs (Stil: Zeitungsartikel)",
            tasks =
                listOf(
                    TaskItem("summarise", "I", "Summarise the article.", listOf("young creatives in Lagos", "growth of creative industries", "problems: jobs, emigration (japa)")),
                    TaskItem("examine", "II", "Examine how the author presents young Nigerians.", listOf("positive, energetic portrayal", "quotes give them a voice", "contrast: problems vs. creativity")),
                    TaskItem("comment", "III", "Comment on the producer's claim that young Nigerians \"cannot fix the roads, but can tell their own stories\".", listOf("own position with reasons", "link to Adichie's \"danger of a single story\"", "limits of cultural success")),
                ),
            part2 =
                listOf(
                    Part2Topic(10, "How does migration change both the countries people leave and the countries they go to?"),
                    Part2Topic(8, "Can social media give young people a political voice?"),
                ),
            source = "course",
            courseLinks = listOf("TED Talk „The danger of a single story“ (Green Line, S. 142)", "Klausur Q1.2: Arbeitsmarkt und Jugendarbeitslosigkeit"),
            adapted = "Text: Sachtext · Aufgabe 2: „examine how …“ · Aufgabe 3: „comment on“ · Niveau B2/C1",
        )

    /** The goals the learning brain would set for the demo simulation. */
    val goals =
        listOf(
            de.abicoach.data.SessionGoal("pattern", "Fehler vermeiden: third person -s – also „the author describes“", "", "pattern", "SVA:third person s"),
            de.abicoach.data.SessionGoal("skill", "Aufgabe 2: jede Beobachtung mit Zitat oder Textverweis belegen und die Wirkung erklären", "", "skill", "analysis"),
            de.abicoach.data.SessionGoal("habit", "Daran arbeiten: antwortet im Gespräch oft nur mit einem Satz", "", "habit", "antwortet im Gespräch oft nur mit einem Satz"),
        )

    /** A realistic presentation: three tasks over about ten minutes, with some fillers and one long pause. */
    fun talk(): List<Utterance> {
        val tasks =
            listOf(
                Triple(
                    "The article Lagos Rising, published in a newspaper, deals with young creative people in Lagos and how they shape the future of their city.",
                    listOf(
                        "First the author describe how a young woman called Chioma is editing a short film on a borrowed laptop in a converted warehouse.",
                        "um He then explains that the creative industries, from Nollywood to Afrobeats, are among the fastest growing sectors of the economy.",
                        "Finally he mentions that many young Nigerians still struggle to find jobs and that a lot of talented people er emigrate.",
                    ),
                    0 to 125_000,
                ),
                Triple(
                    "Moving on to the analysis, the author perceives the young creatives in a very positive and energetic way.",
                    listOf(
                        "um He lets them speak for themselves, for example Chioma says that nobody is waiting for permission anymore, which makes her sound confident.",
                        "The tone of the article is optimistic, although er the author does mention the structural problems of the country.",
                        "Furthermore the contrast between power cuts and traffic jams on the one hand and creativity on the other hand makes the young people look even more resourceful.",
                    ),
                    125_000 to 390_000,
                ),
                Triple(
                    "Now I would like to comment on the producer's claim that young Nigerians cannot fix the roads but can tell their own stories.",
                    listOf(
                        "In my opinion this is partly true, because as we saw in class with Adichie, there is a danger of a single story about Africa.",
                        "um On the other hand cultural success alone does not create enough jobs, so the government still has to invest in infrastructure.",
                        "All in all I think that telling their own stories is an important first step, but it cannot replace political change.",
                    ),
                    390_000 to 605_000,
                ),
            )
        val out = ArrayList<Utterance>()
        tasks.forEachIndexed { t, (first, rest, window) ->
            out += Utterance(Speaker.STUDENT, first, 1, window.first + 9_000L)
            var at = window.first + 9_000L
            var i = 0
            while (true) {
                at += if (t == 1 && i == 6) 32_000L else 10_000L
                if (at > window.second) break
                out += Utterance(Speaker.STUDENT, rest[i % rest.size], 1, at)
                i++
            }
        }
        return out
    }

    fun liveState(part: Int): LiveUiState {
        val utterances =
            if (part == 2) {
                listOf(
                    Utterance(Speaker.EXAMINER, "Thank you for your presentation. You mentioned that many young Nigerians leave the country. Why do you think that is?", 2, 1_000),
                    Utterance(Speaker.STUDENT, "I think the main reason is that there are not enough jobs, especially for young people with a good education.", 2, 9_000),
                    Utterance(Speaker.STUDENT, "And in the class we talked about the brain drain, that a lot of doctors go to the UK.", 2, 18_000),
                    Utterance(Speaker.EXAMINER, "That's an interesting point. What does that mean for the UK?", 2, 26_000),
                )
            } else {
                listOf(
                    Utterance(Speaker.STUDENT, "The article Lagos Rising, published in a newspaper, deals with young creative people in Lagos.", 1, 4_000),
                    Utterance(Speaker.STUDENT, "First the author describe how a young woman is editing a film.", 1, 12_000),
                )
            }
        return LiveUiState(
            status = if (part == 2) "Ich höre zu …" else "Du hast das Wort – halte deinen Vortrag.",
            examinerReady = part == 2,
            transcriberReady = true,
            utterances = utterances,
            interim = if (part == 2) "Well, on the one hand the UK profits from" else "",
            liveErrors = listOf(LiveError("the author describe", "the author describes", "3. Person Singular: -s", "SVA", "major")),
            lastAlert = LiveError("in the class", "in class", "„in class“ ohne Artikel", "DET", "minor"),
            alertAt = if (part == 2) System.currentTimeMillis() + 600_000 else 0,
            elapsedSec = if (part == 2) 214 else 95,
            micLevel = 0.45f,
            examinerSpeaking = false,
            examinerLabel = "Gemini 3.8 Live",
            diag =
                LiveDiag(
                    micHeard = true,
                    segments = if (part == 2) 14 else 9,
                    heard = if (part == 2) 13 else 9,
                    checks = if (part == 2) 13 else 9,
                    checkMs = 2_300,
                    checkMode = "Audio",
                    hints = 3,
                    transcript = "läuft",
                    transcriptTexts = 11,
                    examiner = if (part == 2) "bereit" else "",
                    coachSpoken = 1,
                    repeatsOk = 1,
                ),
        )
    }

    private fun skills(vararg values: Pair<Skill, Double>): Map<Skill, SkillState> =
        values.associate { (s, v) -> s to SkillState(v, 0.55, 4, System.currentTimeMillis()) }

    fun seed(app: AbiCoachApp) {
        val now = System.currentTimeMillis()
        val s = app.settings
        s.examDate = LocalDate.now().plusDays(118).toString()
        s.geminiKey = "demo-gemini-key"
        s.courseTopics = "The Hate U Give, Brexit, Purple Hibiscus, Black Mirror"
        s.themeMode = ThemeMode.LIGHT

        app.profiles.update {
            Profile(
                skills =
                    skills(
                        Skill.AUFGABENBEZUG to 10.8,
                        Skill.ZUSAMMENFASSEN to 11.6,
                        Skill.ANALYSE to 8.1,
                        Skill.KOMMENTAR to 9.4,
                        Skill.SACHWISSEN to 9.9,
                        Skill.STRUKTUR to 10.6,
                        Skill.INTERAKTION to 10.2,
                        Skill.FLUESSIGKEIT to 9.6,
                        Skill.AUSSPRACHE to 10.9,
                        Skill.WORTSCHATZ to 9.1,
                        Skill.GRAMMATIK to 8.6,
                    ),
                placementDone = true,
                placementAt = now - 12 * DAY,
                history = listOf(8.3, 8.6, 8.9, 9.1, 9.0, 9.5, 9.8, 10.1).mapIndexed { i, v -> ForecastPoint(now - (12 - i) * DAY, v, "Demo") },
                klausurErrors = mapOf("VERB_TENSE" to 5, "PREP" to 3, "WORD_CHOICE" to 4),
                teacherNotes = listOf("Gute Argumentation, aber mehr Textbelege!", "Achte auf die Zeiten (present perfect)."),
                topics =
                    mapOf(
                        4 to SkillState(10.6, 0.6, 4, now - 2 * DAY),
                        0 to SkillState(8.2, 0.45, 2, now - 6 * DAY),
                        3 to SkillState(9.4, 0.4, 2, now - 9 * DAY),
                        10 to SkillState(7.1, 0.3, 1, now - 12 * DAY),
                    ),
                skillLog =
                    mapOf(
                        Skill.ANALYSE to listOf(6, 7, 7, 8, 9),
                        Skill.GRAMMATIK to listOf(10, 10, 9, 8),
                        Skill.FLUESSIGKEIT to listOf(9, 10, 10, 11),
                    ),
            )
        }

        val debrief =
            Debrief(
                inhaltPoints = 9,
                inhaltReason = "Die Zusammenfassung ist präzise. In der Analyse fehlen Textbelege, die Stellungnahme bleibt allgemein und nutzt kaum Unterrichtswissen.",
                darstellungPoints = 11,
                darstellungReason = "Weitgehend freier, gut gegliederter Vortrag mit klaren Überleitungen. Einige Fehler bei Zeiten und Präpositionen, Wortschatz angemessen.",
                totalPoints = 10,
                strengths = listOf("Klare Gliederung mit Überleitungen", "Im Gespräch gehst du gut auf Fragen ein", "Gute Aussprache, natürliches Tempo"),
                levers = listOf("Belege jede Analysebehauptung mit einem Zitat und Zeilenangabe", "Nutze in Aufgabe 3 ein Beispiel aus dem Unterricht (z. B. Adichie)", "Present perfect bei Entwicklungen bis heute"),
                errors =
                    listOf(
                        LiveError("the author describe", "the author describes", "3. Person Singular braucht -s", "SVA", "major"),
                        LiveError("since many years", "for many years", "„for“ bei Zeitdauer", "PREP", "minor"),
                        LiveError("in the class we talked", "in class we talked", "„in class“ ohne Artikel", "DET", "minor"),
                        LiveError("Nigeria has became", "Nigeria has become", "Partizip von become", "VERB_FORM", "major"),
                    ),
                upgrades =
                    listOf(
                        Upgrade("I think the young people are very creative and they do many things.", "The young people portrayed in the article come across as remarkably resourceful and entrepreneurial.", "präziser Wortschatz, analytische Formulierung"),
                        Upgrade("The author is positive about it.", "The author's tone is largely optimistic, although he does acknowledge the structural problems the country faces.", "differenziert, zeigt AFB II"),
                    ),
                pronunciation = listOf(PronTip("thoroughly", "„th“ wie „s“ gesprochen", "Zungenspitze leicht zwischen die Zähne"), PronTip("development", "Betonung auf der ersten Silbe", "de-VE-lop-ment")),
                contentFeedback = listOf("In Aufgabe 2 fehlte die Wirkung der Zitate.", "Im Gespräch hättest du Brexit und Migration verbinden können."),
                phrases = listOf(Phrase("to come across as", "wirken wie", "The narrator comes across as rather naive."), Phrase("to shed light on", "beleuchten", "The article sheds light on youth unemployment.")),
                droppedUnverified = 1,
                audioAnalysed = true,
                skills = mapOf(Skill.ZUSAMMENFASSEN to 12, Skill.ANALYSE to 8, Skill.KOMMENTAR to 9, Skill.STRUKTUR to 11, Skill.INTERAKTION to 11, Skill.GRAMMATIK to 9, Skill.AUSSPRACHE to 11),
                taskReviews =
                    listOf(
                        TaskReview(1, 12, listOf("young creatives in Lagos", "growth of creative industries"), listOf("problems: jobs, emigration"), "Auch die Gegenseite kurz nennen."),
                        TaskReview(2, 8, listOf("positive portrayal"), listOf("quotes give them a voice", "contrast problems vs. creativity"), "Behauptung – Zitat – Wirkung."),
                        TaskReview(3, 9, listOf("own position"), listOf("link to Adichie"), "Ein Beispiel aus dem Unterricht macht die Stellungnahme stark."),
                    ),
                courseLinks = listOf("Adichie: The danger of a single story (Green Line, S. 142)", "Brain drain im NHS (Klausur Q1.2)"),
                gradedBy = "Gemini 4 Argon",
                listenedBy = "Gemini 4 Argon",
                progress =
                    Progress(
                        comparedTo = "demo-1",
                        comparedLabel = "Aufgabe 2 vom 28.10.",
                        comparedPoints = 8,
                        fixed = listOf("Zeitform: diesmal kein Fehler dieser Art.", "Präposition: diesmal kein Fehler dieser Art."),
                        still = listOf("Subjekt-Verb-Übereinstimmung: kam wieder vor (2×) – schon in 3 Sitzungen in Folge."),
                    ),
                goalResults =
                    listOf(
                        de.abicoach.data.GoalResult("pattern", "partly", "Einmal „the author describe“, danach richtig „he explains“ und „he mentions“."),
                        de.abicoach.data.GoalResult("skill", "no", "Nur ein Zitat („Nobody is waiting for permission“), die Wirkung der Kontraste blieb unbelegt."),
                        de.abicoach.data.GoalResult("habit", "yes", "Im Gespräch zwei ausführliche Antworten mit Begründung und dem Beispiel brain drain."),
                    ),
                observations =
                    listOf(
                        de.abicoach.data.Observation("beschreibt in Aufgabe 2 den Inhalt, statt die Wirkung zu analysieren", "inhalt", "schwaeche"),
                        de.abicoach.data.Observation("verknüpft Themen gut mit Beispielen aus dem Unterricht", "gespraech", "staerke"),
                    ),
                secondOpinion = de.abicoach.data.SecondOpinion(9, 10, 10, "Gemini 4 Argon", 11, "Inhaltlich solide, die Analyse bleibt an der Oberfläche. Sprachlich sicher mit wenigen Fehlern."),
            )
        app.store.deleteAll()
        app.store.save(
            SessionRecord.create(SessionKind.SIMULATION, FeedbackMode.PRUEFUNG, 4, task()).copy(
                id = SIMULATION,
                startedAt = now - DAY,
                durationSec = 1260,
                part1Sec = 610,
                debrief = debrief.copy(segments = listOf(1 to "The article Lagos Rising", 2 to "Moving on to the analysis", 3 to "Now I would like to comment")),
                examiner = "Gemini 3.8 Live",
                goals = goals,
                liveErrors = debrief.errors,
                tech =
                    "== Teil 1 (Vortrag) · 10:10 Min. · Coach ==\nMikrofon: hört dich (höchster Pegel 0.71)\nLive-Mitschrift: läuft, 31 Sätze\n" +
                        "Ohr: 34 Äußerungen, 33 verstanden, 33 geprüft (Audio, Ø 2.4 s), 0 Störungen, 6 Hinweise\n" +
                        "Coach: 3 wichtige Fehler, 3 gesagt (3 mit Handy-Stimme), 2 richtig wiederholt\n0:01 Start: Teil 1 (Vortrag), Rückmeldung Coach, Lautsprecher\n" +
                        "0:02 Mitschrift verbunden\n1:12 Hinweis: „the author describe“ → „the author describes“ (third person -s)\n" +
                        "1:13 Coach spricht: Careful: not \"the author describe\", but \"the author describes\". Say it again, please.\n1:19 Richtig wiederholt: „the author describes“",
                utterances = talk() + liveState(2).utterances,
            ),
        )
        listOf(
            Triple(SessionKind.PLACEMENT, 9, 12),
            Triple(SessionKind.PART1, 8, 6),
            Triple(SessionKind.PART2, 10, 4),
            Triple(SessionKind.TRAINING, 11, 2),
        ).forEachIndexed { i, (kind, pts, daysAgo) ->
            app.store.save(
                SessionRecord.create(kind, FeedbackMode.FLUESTERN, listOf(4, 0, 10, 8)[i], null, if (kind == SessionKind.PART1) 2 else 0).copy(
                    id = "demo-$i",
                    startedAt = now - daysAgo * DAY,
                    durationSec = 600 + i * 120,
                    debrief = debrief.copy(totalPoints = pts, inhaltPoints = pts - 1, darstellungPoints = pts + 1),
                ),
            )
        }

        app.briefings.save(
            de.abicoach.coach.Briefing(
                date = LocalDate.now().toString(),
                signature = de.abicoach.coach.BriefingMaker.signature(app),
                headline = "Heute holst du bei der Analyse die meisten Punkte.",
                focus = "Deine Analyse liegt bei 8 Punkten, Zusammenfassen schon bei 12. Mit Textbelegen (die dir auch deine Lehrkraft anstreicht) sind schnell 2–3 Punkte drin.",
                steps =
                    listOf(
                        de.abicoach.coach.BriefingStep("Analysieren mit Belegen", "Aufgabe 2 ist dein größter Hebel: Behauptung – Zitat – Wirkung.", "aufgabe2"),
                        de.abicoach.coach.BriefingStep("3-Minuten-Sprint", "Längere, flüssigere Antworten – dein Tempo ist gut, die Sätze noch kurz.", "sprint"),
                        de.abicoach.coach.BriefingStep("Wortschatz wiederholen", "6 Karten sind fällig, darunter „come across as“.", "wortschatz"),
                    ),
                by = "Gemini 4 Argon",
            ),
        )
        app.vocab.reset()
        app.vocab.addAll(de.abicoach.practice.PracticeFeed.fromDebrief(debrief, 4))
        app.vocab.addAll(
            listOf(
                Vocab("brain drain", "Abwanderung von Fachkräften", 10),
                Vocab("upward mobility", "sozialer Aufstieg", 3),
                Vocab("colonial legacy", "koloniales Erbe", 1),
            ).map { v -> de.abicoach.practice.VocabCard(java.util.UUID.randomUUID().toString(), v.de, v.en, "", de.abicoach.practice.CardSource.COURSE, v.topic) },
        )
        app.vocab.cards.value.take(3).forEach { c -> app.vocab.update(c.copy(reps = 4, intervalDays = 12.0, dueAt = now + 6 * DAY)) }
        app.pron.record("thoroughly", 72)
        app.pron.record("development", 91)
        app.pron.record("hierarchy", 58)

        val hour = 3_600_000L
        app.path.update {
            de.abicoach.path.PathProgress(
                mapOf(
                    "w1.ablauf" to de.abicoach.path.LevelProgress(passedAt = now - 14 * DAY, attempts = 1),
                    "w1.aufgaben" to de.abicoach.path.LevelProgress(passedAt = now - 14 * DAY, attempts = 1),
                    "w1.vorbereitung" to de.abicoach.path.LevelProgress(passedAt = now - 13 * DAY, attempts = 1),
                    "w1.frei" to de.abicoach.path.LevelProgress(stars = 2, best = 11, attempts = 2, passedAt = now - 12 * DAY, reviewStage = 2, nextReviewAt = now + 2 * DAY),
                    "w2.einleitung" to de.abicoach.path.LevelProgress(stars = 3, best = 13, attempts = 2, passedAt = now - 9 * DAY, reviewStage = 3, nextReviewAt = now + 5 * DAY),
                    "w2.kern" to de.abicoach.path.LevelProgress(stars = 1, best = 10, attempts = 1, passedAt = now - 4 * DAY, reviewStage = 1, nextReviewAt = now - hour),
                    "w2.outline" to de.abicoach.path.LevelProgress(stars = 2, best = 12, attempts = 1, passedAt = now - 3 * DAY, reviewStage = 1, nextReviewAt = now + DAY),
                    "w3.haltung" to de.abicoach.path.LevelProgress(stars = 1, best = 9, attempts = 3, passedAt = now - 6 * DAY, reviewStage = 1, nextReviewAt = now + DAY, rusty = true),
                ),
            )
        }

        val memory = app.memory
        memory.docs.value.forEach { memory.delete(it.id) }
        memory.add(CourseDoc(BOOK, "Green Line Oberstufe NRW", DocKind.SCHULBUCH, "Q1.1", now - 20 * DAY, "green_line.pdf", "pdf", 312, 48_000_000, 312, true, 120, 120, false, true, DocStatus.DONE, summary = "", topics = listOf(4, 0, 3)))
        memory.add(CourseDoc(KLAUSUR, "Klausur Q1.2 – Brexit", DocKind.KLAUSUR, "Q1.2", now - 10 * DAY, "4 Fotos", "images", 4, 3_000_000, 4, true, 10, 10, false, true, DocStatus.DONE, topics = listOf(0, 1)))
        memory.add(CourseDoc(WORKSHEETS, "Arbeitsblätter Nigeria", DocKind.UNTERRICHT, "Q2.1", now - 2 * DAY, "nigeria.pdf", "pdf", 12, 2_000_000, 7, status = DocStatus.PAUSED))
        val topicsCycle = listOf(4, 4, 0, 3, 10, 1, 2, 5, 8, 4, 0, 13)
        memory.saveChunks(BOOK, (0 until 120).map { i -> Chunk(BOOK, i, 10 + i * 2, "Demo passage $i about ${de.abicoach.data.Knowledge.TOPICS[topicsCycle[i % topicsCycle.size]].english}.", listOf(topicsCycle[i % topicsCycle.size]), "text") })
        memory.saveChunks(KLAUSUR, (0 until 10).map { i -> Chunk(KLAUSUR, i, 1 + i / 3, "Brexit essay passage $i", listOf(0, 1), "exam") })
        memory.appendPages(BOOK, (1..6).map { PageText(it, "# Unit 4: Nigeria – a nation of many voices\n\nNigeria is Africa's most populous country …", "text", listOf(4)) })
        memory.saveDigest(
            BOOK,
            Digest(
                summary = "Das Schulbuch für die Oberstufe mit Units zu Nigeria, Großbritannien, den USA und Globalisierung. Besonders nützlich: die Texte zu Nigeria und zum American Dream.",
                topics = listOf(4, 0, 3, 10),
                halbjahr = "Q1.1",
                facts = listOf(Fact(4, "Nigeria has more than 250 ethnic groups; Hausa, Yoruba and Igbo are the largest."), Fact(4, "Nollywood is the second-largest film industry in the world by output."), Fact(3, "The American Dream promises upward mobility through hard work.")),
                vocabulary = listOf(Vocab("brain drain", "Abwanderung von Fachkräften", 10), Vocab("upward mobility", "sozialer Aufstieg", 3), Vocab("colonial legacy", "koloniales Erbe", 1)),
                examTexts = listOf(ExamTextRef("The danger of a single story (excerpt)", 142, 143, 4, false, "Rede, gut für Analyse"), ExamTextRef("Americanah (excerpt)", 156, 157, 4, true, "Romanauszug zu Identität")),
                klausur = null,
            ),
        )
        memory.update(BOOK) { it.copy(summary = "Das Schulbuch für die Oberstufe mit Units zu Nigeria, Großbritannien, den USA und Globalisierung.") }
        memory.add(CourseDoc(REAL, "Abitur NRW – mündliche Prüfungsaufgaben", DocKind.PRUEFUNG, "unbekannt", now - DAY, "abi_muendlich.pdf", "pdf", 24, 6_000_000, 24, true, 30, 30, false, true, DocStatus.DONE, topics = listOf(4, 8)))
        memory.saveChunks(REAL, (0 until 30).map { i -> Chunk(REAL, i, 1 + i / 2, "Exam material passage $i", listOf(if (i % 2 == 0) 4 else 8), "text") })
        memory.appendPages(REAL, (1..4).map { PageText(it, "Nollywood: Stories for a continent\n\nLagos, a city of twenty million, is also the capital of a film industry that tells Africa's stories to Africa. …", "text", listOf(4)) })
        memory.saveDigest(
            REAL,
            Digest(
                summary = "Original-Aufgaben für die mündliche Prüfung mit Erwartungshorizont.",
                topics = listOf(4, 8),
                halbjahr = "unbekannt",
                facts = emptyList(),
                vocabulary = emptyList(),
                examTexts = emptyList(),
                klausur = null,
                examUnits =
                    listOf(
                        ExamUnit(
                            id = "$REAL#0",
                            docId = REAL,
                            title = "Nollywood: Stories for a continent",
                            source = "Abitur NRW, Grundkurs, mündliche Prüfung (Beispielaufgabe)",
                            startPage = 1,
                            endPage = 2,
                            topic = 4,
                            literary = false,
                            textStart = "Lagos, a city of twenty million",
                            textEnd = "to Africa",
                            tasks =
                                listOf(
                                    TaskItem("outline", "I", "Outline the author's view of Nollywood.", listOf("film industry as voice of Africa", "economic importance")),
                                    TaskItem("examine", "II", "Examine how the author presents the city of Lagos.", listOf("contrasts", "imagery of energy")),
                                    TaskItem("comment", "III", "Comment on the claim that films can change how a country sees itself.", emptyList()),
                                ),
                            part2 = listOf("How do media shape the image of a country?"),
                            solution = "Summary: Nollywood as Africa's own voice … Analysis: contrasts, enumeration … Comment: identity, single story (Adichie).",
                            format = "oral",
                        ),
                        ExamUnit(
                            id = "$REAL#1",
                            docId = REAL,
                            title = "Likes and lies",
                            source = "Abitur NRW, Grundkurs, mündliche Prüfung (Beispielaufgabe)",
                            startPage = 3,
                            endPage = 4,
                            topic = 8,
                            literary = false,
                            textStart = "",
                            textEnd = "",
                            tasks =
                                listOf(
                                    TaskItem("summarise", "I", "Summarise the text.", emptyList()),
                                    TaskItem("analyse", "II", "Analyse the means the author uses to warn the reader.", emptyList()),
                                    TaskItem("discuss", "III", "Discuss whether social media make young people more political.", emptyList()),
                                ),
                            part2 = emptyList(),
                            solution = "",
                            format = "oral",
                        ),
                    ),
            ),
        )
        app.errorMemory.replaceAll(
            listOf(
                ErrorPattern("SVA:third person s", "third person -s", "SVA", listOf(PatternExample("the author describe", "the author describes", now - DAY, "Sitzung"), PatternExample("she live in Lagos", "she lives in Lagos", now - 4 * DAY, "Sitzung")), 7, 4, now - 20 * DAY, now - DAY, 0, "demo-1", dueAt = now - hour),
                ErrorPattern("VERB_TENSE:present perfect for up to now", "present perfect for up to now", "VERB_TENSE", listOf(PatternExample("since 2016 the UK left", "since 2016 the UK has left", now - 10 * DAY, "Klausur")), 5, 3, now - 25 * DAY, now - 3 * DAY, 0, "demo-2", fromKlausur = 1, dueAt = now - hour),
                ErrorPattern("PREP:since vs for", "since vs for", "PREP", listOf(PatternExample("since many years", "for many years", now - 6 * DAY, "Sitzung")), 3, 2, now - 14 * DAY, now - 6 * DAY, 1, "demo-3", dueAt = now + 2 * DAY, intervalDays = 3.0),
                ErrorPattern("FALSE_FRIEND:false friend become", "false friend: become", "FALSE_FRIEND", listOf(PatternExample("I become a lot of likes", "I get a lot of likes", now - 2 * DAY, "Sitzung")), 1, 1, now - 2 * DAY, now - 2 * DAY, 0, "demo-4", dueAt = now - hour),
                ErrorPattern("DET:article with general nouns", "article with general nouns", "DET", listOf(PatternExample("the society is changing", "society is changing", now - 30 * DAY, "Sitzung")), 4, 3, now - 40 * DAY, now - 30 * DAY, 4, "demo-5", dueAt = now + 20 * DAY, intervalDays = 14.0, drills = 3, drillsOk = 3, drillStreak = 3),
            ),
        )
        memory.saveDigest(
            KLAUSUR,
            Digest(
                summary = "Klausur zum Brexit mit Textanalyse und Kommentar; Note 2-, Kommentar der Lehrkraft zu fehlenden Textbelegen.",
                topics = listOf(0, 1),
                halbjahr = "Q1.2",
                facts = listOf(Fact(0, "In 2016, 52 % voted to leave the EU.")),
                vocabulary = emptyList(),
                examTexts = emptyList(),
                klausur =
                    KlausurInfo(
                        grade = "10 Punkte (2-)",
                        teacherComments = listOf("Gute Argumentation, aber mehr Textbelege!", "Achte auf die Zeiten (present perfect)."),
                        errors = listOf(LiveError("since 2016 the UK left", "since 2016 the UK has left", "present perfect mit since", "VERB_TENSE", "major")),
                        strengths = listOf("klarer Aufbau"),
                        weaknesses = listOf("wenig Textbelege"),
                    ),
            ),
        )
        app.insights.replaceAll(
            listOf(
                de.abicoach.learn.Insight("antwortet im Gespräch oft nur mit einem Satz", "gespraech", "schwaeche", 3, now - 12 * DAY, now - 2 * DAY),
                de.abicoach.learn.Insight("beschreibt in Aufgabe 2 den Inhalt, statt die Wirkung zu analysieren", "inhalt", "schwaeche", 2, now - 9 * DAY, now - DAY),
                de.abicoach.learn.Insight("verknüpft Themen gut mit Beispielen aus dem Unterricht", "gespraech", "staerke", 2, now - 6 * DAY, now - DAY),
                de.abicoach.learn.Insight("liest im Vortrag ganze Sätze von den Notizen ab", "vortrag", "schwaeche", 2, now - 20 * DAY, now - 8 * DAY, overcome = 2),
            ),
        )
        app.dossiers.clear()
        app.dossiers.save(
            TopicDossier(
                topic = 4,
                madeAt = now - DAY,
                signature = DossierMaker.signature(app, 4),
                by = "Gemini 4 Argon",
                overview = "In your course, Nigeria is studied as a young, diverse nation between tradition and rapid change: colonial legacy, ethnic and religious diversity, a booming creative industry and the emigration of the educated young.",
                keyPoints =
                    listOf(
                        "Nigeria has more than 250 ethnic groups; Hausa, Yoruba and Igbo are the largest.",
                        "Independence from Britain in 1960; the borders were drawn by the colonial power.",
                        "Nollywood is the second-largest film industry in the world by output.",
                        "Many young graduates emigrate (\"japa\") – a brain drain, e.g. doctors to the UK.",
                        "Adichie warns against \"a single story\" about Africa.",
                    ),
                examples = listOf(DossierItem("Chimamanda Ngozi Adichie: The danger of a single story", "Green Line Oberstufe NRW, S. 142"), DossierItem("Americanah: Ifemelu's experience of race in the USA", "Green Line Oberstufe NRW, S. 156")),
                quotes = listOf(DossierItem("The single story creates stereotypes, and the problem with stereotypes is not that they are untrue, but that they are incomplete.", "Green Line Oberstufe NRW, S. 142")),
                terms = listOf(Vocab("brain drain", "Abwanderung von Fachkräften", 4), Vocab("colonial legacy", "koloniales Erbe", 4), Vocab("ethnic diversity", "ethnische Vielfalt", 4)),
                arguments =
                    listOf(
                        DossierArgument("Emigration weakens Nigeria's health system.", "contra", "Thousands of doctors have left for the UK since 2015."),
                        DossierArgument("The diaspora sends money and ideas back home.", "pro", "Remittances are larger than foreign aid."),
                        DossierArgument("Whether emigration helps depends on whether people return.", "nuance", "Return migration of tech founders to Lagos."),
                    ),
                links = listOf(DossierLink(10, "Brain drain connects Nigeria with migration and the global job market."), DossierLink(1, "The colonial legacy links Nigeria to Britain's view of its empire.")),
                questions = listOf("Why do so many young Nigerians leave the country?", "Is Nollywood more than entertainment?", "What does Adichie mean by a 'single story'?"),
                ownIdeas = listOf("Compared Nollywood with Hollywood's image of Africa."),
            ),
        )
    }

    fun drills(): List<Drill> =
        listOf(
            Drill("d1", DrillType.SAY_RIGHT, "SVA:third person s", "Sag es richtig – achte auf die 3. Person -s.", "The author describe how young people in Lagos work.", "The author describes how young people in Lagos work.", "describes", 4),
            Drill("d2", DrillType.COMPLETE, "VERB_TENSE:present perfect for up to now", "Vollende den Satz – es geht um eine Entwicklung bis heute.", "Since independence in 1960, Nigeria's population …", "Since independence in 1960, Nigeria's population has grown enormously.", "has grown", 4),
            Drill("d3", DrillType.ANSWER, "PREP:since vs for", "Antworte in einem Satz – mit „for“ oder „since“.", "How long has Nollywood been so successful?", "Nollywood has been successful for about thirty years.", "for about thirty years", 4),
        )
}
