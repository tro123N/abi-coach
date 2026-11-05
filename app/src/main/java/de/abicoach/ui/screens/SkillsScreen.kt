package de.abicoach.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.data.FeedbackMode
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.data.TalkStyle
import de.abicoach.learn.PatternState
import de.abicoach.profile.Area
import de.abicoach.profile.Profile
import de.abicoach.profile.Skill
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.Screen
import de.abicoach.ui.SessionSetup
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.AreaIntro
import de.abicoach.ui.components.BarRow
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.ChartGuide
import de.abicoach.ui.components.ChartSeries
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.MultiLineChart
import de.abicoach.ui.components.Pill
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.pointsColor
import de.abicoach.ui.theme.yellow

/** One way to train a competence on its own. */
data class SkillAction(val title: String, val subtitle: String, val icon: Int, val tag: String, val run: () -> Unit)

/**
 * How each competence is trained on its own: the part of the exam where it counts, with a focus for the examiner or the
 * material, a session goal and a grading that looks at exactly this competence.
 */
object SkillTraining {
    const val AUFGABENBEZUG =
        "Use demanding operators that are easy to mix up (e.g. outline vs. summarise, examine vs. analyse, assess vs. comment), so that the student practises doing exactly what each operator asks."
    const val SACHWISSEN =
        "Focus of this exercise: subject knowledge. Ask for concrete facts, names, events, figures and examples from the course, and ask him to link the topic to other topic fields and to the texts read in class."
    const val INTERAKTION =
        "Focus of this exercise: interaction. Leave room for the student: ask short open follow-up questions, present a counter-position for him to react to, once ask him to put a question to you, and do not fill pauses yourself."
    const val FLUESSIGKEIT =
        "Focus of this exercise: fluency. Keep your own turns very short and keep him talking: ask him to go on, to give another example, to explain it in other words."
    const val AUSSPRACHE =
        "Focus of this exercise: pronunciation and intonation. Ask questions whose answers need words that German speakers often mispronounce (th, w/v, -ed endings, word stress as in 'development', 'determine', 'comfortable')."
    const val WORTSCHATZ =
        "Focus of this exercise: precise vocabulary. Ask questions that need precise topic words and phrases for analysis and discussion; when he uses vague words (thing, good, bad, a lot of), ask him to say it more precisely."
    const val KOMMENTAR =
        "Focus of this exercise: taking a position. Ask for his opinion on controversial questions of the topic, demand reasons and examples, present counter-arguments and ask him to weigh them."

    private val GRAMMAR = setOf("VERB_TENSE", "VERB_FORM", "SVA", "PREP", "DET", "NOUN_NUM", "PRON", "WORD_ORDER", "ADJ_ADV", "STRUCTURE")
    private val WORDS = setOf("WORD_CHOICE", "FALSE_FRIEND", "REGISTER")

    fun actions(vm: AppViewModel, skill: Skill, patterns: List<de.abicoach.learn.ErrorPattern>): List<SkillAction> {
        val app = vm.app
        val feedback = app.settings.defaultFeedback

        fun setup(s: SessionSetup): () -> Unit = {
            vm.tap()
            vm.nav.push(Screen.Setup(s.copy(focusSkill = skill.key)))
        }

        fun talk(minutes: Int, instruction: String, mode: FeedbackMode = feedback, style: TalkStyle? = null, kind: SessionKind = SessionKind.TRAINING) =
            setup(SessionSetup.defaults(kind, 0, mode).copy(talkMinutes = minutes, instruction = instruction, talkStyle = style))

        val now = System.currentTimeMillis()
        val open = patterns.filter { it.state != PatternState.BEHOBEN }.sortedByDescending { it.weight(now) }
        return when (skill) {
            Skill.ZUSAMMENFASSEN ->
                listOf(SkillAction("Aufgabe 1 üben", "Kernaussagen knapp wiedergeben · 8 Min. Vorbereitung", R.drawable.summarize_24dp, "skill_go_0", setup(SessionSetup.defaults(SessionKind.PART1, 1, feedback))))
            Skill.ANALYSE ->
                listOf(SkillAction("Aufgabe 2 üben", "Mittel, Wirkung, Textbelege · 8 Min. Vorbereitung", R.drawable.search_24dp, "skill_go_0", setup(SessionSetup.defaults(SessionKind.PART1, 2, feedback))))
            Skill.KOMMENTAR ->
                listOf(
                    SkillAction("Aufgabe 3 üben", "Eigene, begründete Position · 8 Min. Vorbereitung", R.drawable.rate_review_24dp, "skill_go_0", setup(SessionSetup.defaults(SessionKind.PART1, 3, feedback))),
                    SkillAction("Stellung nehmen im Gespräch", "Der Prüfer fragt nach deiner Meinung und hält dagegen · 6 Min.", R.drawable.forum_24dp, "skill_go_1", talk(6, KOMMENTAR)),
                )
            Skill.AUFGABENBEZUG ->
                listOf(
                    SkillAction(
                        "Vortrag mit kniffligen Operatoren",
                        "Alle drei Aufgaben genau nach Operator · 15 Min. Vorbereitung",
                        R.drawable.assignment_24dp,
                        "skill_go_0",
                        setup(SessionSetup.defaults(SessionKind.PART1, 0, feedback).copy(prepMinutes = 15, instruction = AUFGABENBEZUG)),
                    ),
                )
            Skill.STRUKTUR ->
                listOf(
                    SkillAction("Ganzer Vortrag", "Gliederung, Überleitungen, frei sprechen · 15 Min. Vorbereitung", R.drawable.co_present_24dp, "skill_go_0", setup(SessionSetup.defaults(SessionKind.PART1, 0, feedback).copy(prepMinutes = 15))),
                )
            Skill.SACHWISSEN -> {
                val weakTopic = app.course.coveredTopics().map { it.first }.minByOrNull { app.profiles.profile.value.topics[it]?.score ?: 7.0 }
                listOfNotNull(
                    SkillAction("Gespräch über Fakten und Bezüge", "Der Prüfer fragt nach Beispielen aus dem Unterricht · 8 Min.", R.drawable.forum_24dp, "skill_go_0", talk(8, SACHWISSEN, kind = SessionKind.PART2)),
                    weakTopic?.takeIf { app.dossiers.get(it) != null }?.let { t ->
                        SkillAction("Dossier wiederholen", "Das Wichtigste zu ${de.abicoach.data.Knowledge.topicShort(t)}", R.drawable.library_books_24dp, "skill_go_1") { vm.openDossier(t) }
                    },
                )
            }
            Skill.INTERAKTION ->
                listOf(
                    SkillAction("Gesprächsführung trainieren", "Eingehen, nachfragen, selbst Impulse setzen · 8 Min.", R.drawable.forum_24dp, "skill_go_0", talk(8, INTERAKTION, style = TalkStyle.EINGEHEN)),
                )
            Skill.FLUESSIGKEIT ->
                listOf(
                    SkillAction("Sprint", "3 Minuten so viel und so frei sprechen wie möglich", R.drawable.speed_24dp, "skill_go_0", setup(SessionSetup.sprint(feedback))),
                    SkillAction("Im Fluss bleiben", "Der Prüfer hält dich mit kurzen Fragen am Reden · 6 Min.", R.drawable.chat_24dp, "skill_go_1", talk(6, FLUESSIGKEIT)),
                )
            Skill.AUSSPRACHE ->
                listOf(
                    SkillAction("Aussprache-Labor", "Wörter anhören, nachsprechen, Laut für Laut", R.drawable.graphic_eq_24dp, "skill_go_0") { vm.openPronunciation() },
                    SkillAction("Gespräch mit schwierigen Wörtern", "Die Auswertung hört genau auf deine Aussprache · 5 Min.", R.drawable.record_voice_over_24dp, "skill_go_1", talk(5, AUSSPRACHE)),
                )
            Skill.WORTSCHATZ -> {
                val wordPattern = open.firstOrNull { it.category in WORDS }
                listOfNotNull(
                    SkillAction("Präzise Wörter im Gespräch", "Der Prüfer hakt bei ungenauen Wörtern nach · 6 Min.", R.drawable.translate_24dp, "skill_go_0", talk(6, WORTSCHATZ)),
                    SkillAction("Wortschatz-Karten", "Deine Themenwörter im Wiederholplan", R.drawable.note_stack_24dp, "skill_go_1") { vm.openVocab() },
                    wordPattern?.let { p -> SkillAction("Fehler-Werkstatt: ${p.title}", "Dein häufigster Wortfehler, Schritt für Schritt", R.drawable.school_24dp, "skill_go_2") { vm.openWorkshop(p.key) } },
                )
            }
            Skill.GRAMMATIK -> {
                val grammar = open.filter { it.category in GRAMMAR }
                val categories = grammar.map { it.category }.distinct().take(2)
                listOfNotNull(
                    grammar.firstOrNull()?.let { p -> SkillAction("Fehler-Werkstatt: ${p.title}", "Dein wichtigster Grammatikfehler, Schritt für Schritt", R.drawable.school_24dp, "skill_go_0") { vm.openWorkshop(p.key) } },
                    grammar.takeIf { it.isNotEmpty() }?.let { list ->
                        SkillAction("Sprech-Training", "Kurze Übungen zu ${list.size} Grammatikfehlern", R.drawable.spellcheck_24dp, "skill_go_1") { vm.openDrills(list.take(4).map { it.key }) }
                    },
                    SkillAction(
                        "Gespräch mit Coach",
                        "Der Coach unterbricht bei Grammatikfehlern · 6 Min.",
                        R.drawable.hearing_24dp,
                        "skill_go_2",
                        setup(SessionSetup.defaults(SessionKind.TRAINING, 0, FeedbackMode.COACH).copy(talkMinutes = 6, focusCategories = categories)),
                    ),
                )
            }
        }
    }

    /** The score of [skill] in every graded session, oldest first (with the day). */
    fun history(sessions: List<SessionRecord>, skill: Skill): List<Pair<Long, Int>> =
        sessions.filter { it.debrief?.skills?.get(skill) != null }.sortedBy { it.startedAt }.map { it.startedAt to (it.debrief?.skills?.get(skill) ?: 0) }.takeLast(20)
}

/** Every competence on its own: where it stands, how it developed, and how to train exactly this one. */
@Composable
fun SkillsScreen(vm: AppViewModel) {
    val app = vm.app
    val profile by app.profiles.profile.collectAsState()
    val sessions by app.store.sessions.collectAsState()
    val patterns by app.errorMemory.patterns.collectAsState()
    val levers = remember(profile) { profile.levers(3) }
    DetailScaffold(title = "Kompetenzen üben", onBack = { vm.tap(); vm.nav.pop() }, tag = "skills") {
        AreaIntro(
            R.drawable.target_24dp,
            "Jede Kompetenz einzeln",
            "Das NRW-Raster bewertet 11 Kompetenzen. Hier trainierst du jede für sich – mit einer passenden Übung, einem Ziel und einer Auswertung, die genau darauf schaut.",
        )
        MessageBanners(vm)
        if (levers.isNotEmpty()) {
            AbiCard(modifier = Modifier.testTag("skills_levers")) {
                CardTitle("Hier holst du die meisten Punkte", icon = R.drawable.rocket_launch_24dp, iconTint = yellow)
                CaptionText("Deine größten Hebel – gemessen an Gewicht im Raster und deinem Stand:")
                Gap(6.dp)
                levers.forEach { s -> CaptionText("• ${s.label}: ${s.description}") }
            }
        }
        Area.entries.forEach { area ->
            Text(
                "${area.label} (${(area.weight * 100).toInt()} %)",
                fontSize = AbiTheme.dimens.fonts.label,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Skill.entries.filter { it.area == area }.forEach { skill ->
                SkillCard(vm, skill, profile, sessions, patterns, skill in levers)
            }
        }
    }
}

@Composable
private fun SkillCard(vm: AppViewModel, skill: Skill, profile: Profile, sessions: List<SessionRecord>, patterns: List<de.abicoach.learn.ErrorPattern>, lever: Boolean) {
    val state = profile.skills[skill]
    val history = remember(sessions, skill) { SkillTraining.history(sessions, skill) }
    val actions = SkillTraining.actions(vm, skill, patterns)
    AbiCard(modifier = Modifier.testTag("skill_${skill.key}"), border = if (lever) yellow.copy(alpha = 0.6f) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(skill.label, fontSize = AbiTheme.dimens.fonts.body, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                CaptionText(skill.description)
            }
            if (lever) {
                Spacer(Modifier.width(8.dp))
                Pill("Großer Hebel", yellow)
            }
        }
        Gap(8.dp)
        if (state == null) {
            CaptionText("Noch nicht gemessen – nach der ersten Übung steht hier dein Stand.")
        } else {
            BarRow("Dein Stand", (state.score / 15).toFloat(), "%.1f P.".format(state.score) + trendMark(profile.skillTrend(skill)), pointsColor(state.score))
        }
        if (history.size >= 2) {
            Gap(6.dp)
            MultiLineChart(
                series = listOf(ChartSeries(history.map { it.second.toFloat() }, accent, skill.label)),
                labels = history.mapIndexed { i, (at, _) -> if (i == 0 || i == history.lastIndex || history.size <= 6) dayLabel(at) else "" },
                guides = listOf(ChartGuide(13f, "13", green)),
                height = 96.dp,
                maxY = 15f,
            )
            CaptionText("Deine Punkte in dieser Kompetenz bei jeder Auswertung")
        }
        actions.forEach { a ->
            Gap(8.dp)
            ActionButton(a.title, a.icon, accent, a.run, subtitle = a.subtitle, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("${a.tag}_${skill.key}"))
        }
    }
}

internal fun dayLabel(at: Long): String {
    val d = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return "${d.dayOfMonth}.${d.monthValue}."
}
