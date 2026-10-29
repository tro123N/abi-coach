package de.abicoach

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.abicoach.data.Debrief
import de.abicoach.data.ExamTask
import de.abicoach.data.FeedbackMode
import de.abicoach.data.LiveError
import de.abicoach.data.Part2Topic
import de.abicoach.data.Phrase
import de.abicoach.data.PronTip
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
            app.memory.docs.value.forEach { app.memory.delete(it.id) }
            app.settings.themeMode = ThemeMode.LIGHT
            app.settings.geminiKey = ""
            app.settings.openAiKey = ""
            app.settings.anthropicKey = ""
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
        swipeShot("list_weg", "abiweg_unten", 2)

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
        ui { vm.nav.pop(); vm.nav.push(Screen.Ask) }
        shot("frag_dein_material")

        ui { vm.nav.openTab(Tab.STATS) }
        shot("statistik")
        swipeShot("list_stats", "statistik_mitte", 1)
        swipeShot("list_stats", "statistik_unten", 2)

        ui { vm.nav.push(Screen.Debrief(Demo.SIMULATION)) }
        shot("auswertung")
        swipeShot("debrief", "auswertung_2", 1)
        swipeShot("debrief", "auswertung_3", 1)
        swipeShot("debrief", "auswertung_4", 2)

        ui { vm.nav.openTab(Tab.TRAINING); vm.nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.SIMULATION, 0, FeedbackMode.FLUESTERN))) }
        shot("einrichten_simulation")
        swipeShot("setup", "einrichten_simulation_unten", 2)
        ui { vm.nav.pop(); vm.nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.PART1, 2, FeedbackMode.COACH))) }
        shot("einrichten_aufgabe2")

        ui { vm.nav.openTab(Tab.TRAINING); vm.demoPrep(Demo.task(), SessionKind.SIMULATION) }
        shot("vorbereitung")
        swipeShot("prep", "vorbereitung_unten", 2)

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
        )

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
            elapsedSec = if (part == 2) 214 else 95,
            micLevel = 0.45f,
            examinerSpeaking = false,
            examinerLabel = "GPT-Live-1",
        )
    }

    private fun skills(vararg values: Pair<Skill, Double>): Map<Skill, SkillState> =
        values.associate { (s, v) -> s to SkillState(v, 0.55, 4, System.currentTimeMillis()) }

    fun seed(app: AbiCoachApp) {
        val now = System.currentTimeMillis()
        val s = app.settings
        s.examDate = LocalDate.now().plusDays(118).toString()
        s.geminiKey = "demo-gemini-key"
        s.openAiKey = "demo-openai-key"
        s.anthropicKey = "demo-anthropic-key"
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
                gradedBy = "Claude Opus 5.5",
                listenedBy = "Gemini 4 Argon",
            )
        app.store.deleteAll()
        app.store.save(
            SessionRecord.create(SessionKind.SIMULATION, FeedbackMode.PRUEFUNG, 4, task()).copy(
                id = SIMULATION,
                startedAt = now - DAY,
                durationSec = 1260,
                part1Sec = 610,
                debrief = debrief,
                examiner = "GPT-Live-1",
                utterances = liveState(1).utterances + liveState(2).utterances,
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
    }
}
