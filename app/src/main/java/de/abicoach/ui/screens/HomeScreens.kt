package de.abicoach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.data.SessionKind
import de.abicoach.profile.Area
import de.abicoach.profile.Profile
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.Milestone
import de.abicoach.ui.Planner
import de.abicoach.ui.Recommendation
import de.abicoach.ui.Screen
import de.abicoach.ui.SessionSetup
import de.abicoach.ui.TabScaffold
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.AreaIntro
import de.abicoach.ui.components.BarRow
import de.abicoach.ui.components.BigNumber
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.BulletText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.LevelBadge
import de.abicoach.ui.components.MiniAction
import de.abicoach.ui.components.StartButton
import de.abicoach.ui.components.StatTile
import de.abicoach.ui.components.Tab
import de.abicoach.ui.components.TopBarAction
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.pointsColor
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow

/** Start page: forecast, what to do today and the milestones on the way to 15 points. */
@Composable
fun AbiWegScreen(vm: AppViewModel) {
    val app = vm.app
    val version by app.settings.version.collectAsState()
    val profile by app.profiles.profile.collectAsState()
    val sessions by app.store.sessions.collectAsState()
    val docs by app.memory.docs.collectAsState()
    val recommendations = remember(version, profile, sessions, docs) { Planner.today(app) }
    val milestones = remember(version, profile, sessions, docs) { Planner.milestones(app) }
    TabScaffold(
        vm,
        Tab.WEG,
        "Abi-Weg",
        actions = { TopBarAction(R.drawable.settings_24dp, "Einstellungen", { vm.tap(); vm.nav.push(Screen.Settings) }, tag = "action_settings") },
    ) {
        item {
            AreaIntro(
                icon = R.drawable.route_24dp,
                title = "Dein Weg zur 15",
                text = "Die App kennt deinen Kurs, misst deinen Stand und sagt dir jeden Tag, was am meisten Punkte bringt.",
            )
        }
        if (vm.errorMessage != null || vm.notice != null) item { MessageBanners(vm) }
        item { OverviewCard(vm, profile) }
        item { TodayCard(vm, recommendations) }
        if (profile.skills.isNotEmpty()) item { AreasCard(vm, profile) }
        item { MilestonesCard(milestones) }
    }
}

@Composable
private fun OverviewCard(vm: AppViewModel, profile: Profile) {
    val app = vm.app
    val dimens = AbiTheme.dimens
    val prognosis = profile.prognosis()
    val days = Planner.daysUntilExam(app)
    val streak = app.store.streak()
    val today = app.store.minutesToday()
    AbiCard(modifier = Modifier.testTag("overview")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigNumber(
                value = prognosis?.points?.toString() ?: "–",
                caption = "Prognose",
                color = prognosis?.let { pointsColor(it.total) } ?: MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (prognosis != null) {
                    Text(
                        "${prognosis.points} Punkte – ${Planner.gradeLabel(prognosis.points)}",
                        fontSize = dimens.fonts.label,
                        lineHeight = dimens.fonts.label * 1.2f,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    CaptionText("Spanne ${prognosis.lowPoints}–${prognosis.highPoints} Punkte · wird genauer, je mehr du übst")
                    profile.trend()?.let { t ->
                        if (t != 0.0) CaptionText((if (t > 0) "▲ " else "▼ ") + points(kotlin.math.abs(t)) + " seit zwei Wochen", color = if (t > 0) green else red)
                    }
                } else {
                    Text("Noch keine Prognose", fontSize = dimens.fonts.label, color = MaterialTheme.colorScheme.onPrimary)
                    CaptionText("Nach der Einstufung siehst du hier, wo du stehst und was dir bis zur 15 fehlt.")
                }
            }
        }
        Gap(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(days?.toString() ?: "?", if (days == 1L) "Tag bis zum Abi" else "Tage bis zum Abi", Modifier.weight(1f), icon = R.drawable.event_24dp, color = accent)
            StatTile(streak.toString(), if (streak == 1) "Tag in Folge" else "Tage in Folge", Modifier.weight(1f), icon = R.drawable.whatshot_24dp, color = if (streak > 0) red else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
            StatTile("$today/${app.settings.dailyMinutes}", "Min. heute", Modifier.weight(1f), icon = R.drawable.timer_24dp, color = if (today >= app.settings.dailyMinutes) green else yellow)
        }
    }
}

@Composable
private fun TodayCard(vm: AppViewModel, recommendations: List<Recommendation>) {
    AbiCard(modifier = Modifier.testTag("today")) {
        CardTitle("Heute dran", icon = R.drawable.task_alt_24dp, iconTint = green)
        if (recommendations.isEmpty()) {
            BodyText("Alles erledigt. Wenn du magst: eine Simulation oder ein Gespräch zu einem Themenfeld, das du lange nicht hattest.")
        }
        recommendations.forEachIndexed { i, r ->
            if (i > 0) Gap(8.dp)
            ActionButton(
                text = r.title,
                subtitle = r.subtitle,
                icon = r.icon,
                iconTint = tintColor(r.tint),
                onClick = { vm.open(r.target) },
                container = MaterialTheme.colorScheme.background,
                modifier = Modifier.testTag("rec_$i"),
            )
        }
    }
}

@Composable
private fun AreasCard(vm: AppViewModel, profile: Profile) {
    val prognosis = profile.prognosis() ?: return
    AbiCard {
        CardTitle("Dein Stand", icon = R.drawable.insights_24dp)
        prognosis.inhalt?.let { BarRow("${Area.INHALT.label} (40 %)", (it / 15).toFloat(), points(it) + " P.", pointsColor(it)) }
        Gap(8.dp)
        prognosis.darstellung?.let { BarRow("${Area.DARSTELLUNG.label} (60 %)", (it / 15).toFloat(), points(it) + " P.", pointsColor(it)) }
        val levers = profile.levers(2)
        if (levers.isNotEmpty()) {
            Gap(10.dp)
            CaptionText("Größte Hebel: " + levers.joinToString(", ") { it.label })
        }
        Gap(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction(R.drawable.bar_chart_24dp, "Statistik", { vm.tap(); vm.nav.openTab(Tab.STATS) }, Modifier.weight(1f))
            MiniAction(R.drawable.record_voice_over_24dp, "Training", { vm.tap(); vm.nav.openTab(Tab.TRAINING) }, Modifier.weight(1f))
            MiniAction(R.drawable.school_24dp, "Mein Kurs", { vm.tap(); vm.nav.openTab(Tab.KURS) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MilestonesCard(milestones: List<Milestone>) {
    val dimens = AbiTheme.dimens
    val next = milestones.indexOfFirst { !it.done }
    AbiCard {
        CardTitle("Meilensteine", icon = R.drawable.trophy_24dp, iconTint = yellow)
        milestones.forEachIndexed { i, m ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                LevelBadge(
                    label = (i + 1).toString(),
                    locked = !m.done && i > next,
                    mastered = m.done,
                    attention = false,
                    highlighted = i == next,
                    size = dimens.components.button * 0.55f,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (m.done || i == next) 1f else 0.6f))
                    m.detail?.let { CaptionText(it) }
                }
            }
        }
    }
}

/** All ways to practise: the whole exam, single parts and single tasks. */
@Composable
fun TrainingScreen(vm: AppViewModel) {
    val app = vm.app
    val sessions by app.store.sessions.collectAsState()
    val feedback = app.settings.defaultFeedback
    val focus = remember(sessions) { Planner.topErrorCategories(app, 2) }

    fun lastPoints(kind: SessionKind, task: Int = 0): String? =
        sessions.firstOrNull { it.kind == kind && it.focusTask == task && it.debrief != null }?.debrief?.let { "${it.totalPoints} P." }

    fun open(setup: SessionSetup) {
        vm.tap()
        vm.nav.push(Screen.Setup(setup))
    }

    TabScaffold(
        vm,
        Tab.TRAINING,
        "Training",
        actions = { TopBarAction(R.drawable.settings_24dp, "Einstellungen", { vm.tap(); vm.nav.push(Screen.Settings) }) },
    ) {
        item {
            AreaIntro(
                icon = R.drawable.record_voice_over_24dp,
                title = "Üben nach Wahl",
                text = "Die ganze Prüfung, ein Teil oder eine einzelne Aufgabe – jedes Mal mit Auswertung nach dem NRW-Raster.",
            )
        }
        if (vm.errorMessage != null || vm.notice != null) item { MessageBanners(vm) }
        item {
            AbiCard {
                CardTitle("Ganze Prüfung", icon = R.drawable.workspace_premium_24dp, iconTint = green)
                ActionButton(
                    "Prüfungssimulation",
                    R.drawable.workspace_premium_24dp,
                    green,
                    { open(SessionSetup.defaults(SessionKind.SIMULATION, 0, feedback)) },
                    subtitle = "30 Min. Vorbereitung · Vortrag · Prüfungsgespräch",
                    trailing = lastPoints(SessionKind.SIMULATION),
                    container = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("train_simulation"),
                )
                Gap(8.dp)
                ActionButton(
                    "Einstufung",
                    R.drawable.flag_24dp,
                    yellow,
                    { vm.tap(); vm.nav.push(Screen.PlacementIntro) },
                    subtitle = "12 Min. – misst deinen Stand neu",
                    trailing = lastPoints(SessionKind.PLACEMENT),
                    container = MaterialTheme.colorScheme.background,
                )
            }
        }
        item {
            AbiCard {
                CardTitle("Teil 1 – Vortrag", icon = R.drawable.co_present_24dp)
                ActionButton(
                    "Ganzer Vortrag",
                    R.drawable.co_present_24dp,
                    accent,
                    { open(SessionSetup.defaults(SessionKind.PART1, 0, feedback)) },
                    subtitle = "Alle drei Aufgaben · ca. 10 Minuten",
                    trailing = lastPoints(SessionKind.PART1, 0),
                    container = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("train_part1"),
                )
                Gap(8.dp)
                ActionButton(
                    "Aufgabe 1: Zusammenfassen",
                    R.drawable.summarize_24dp,
                    accent,
                    { open(SessionSetup.defaults(SessionKind.PART1, 1, feedback)) },
                    subtitle = "summarise, outline · ca. 2–3 Minuten",
                    trailing = lastPoints(SessionKind.PART1, 1),
                    container = MaterialTheme.colorScheme.background,
                )
                Gap(8.dp)
                ActionButton(
                    "Aufgabe 2: Analysieren",
                    R.drawable.insights_24dp,
                    accent,
                    { open(SessionSetup.defaults(SessionKind.PART1, 2, feedback)) },
                    subtitle = "examine how the author …, analyse · ca. 3–4 Minuten",
                    trailing = lastPoints(SessionKind.PART1, 2),
                    container = MaterialTheme.colorScheme.background,
                )
                Gap(8.dp)
                ActionButton(
                    "Aufgabe 3: Kommentieren",
                    R.drawable.rate_review_24dp,
                    accent,
                    { open(SessionSetup.defaults(SessionKind.PART1, 3, feedback)) },
                    subtitle = "comment, discuss · ca. 3 Minuten",
                    trailing = lastPoints(SessionKind.PART1, 3),
                    container = MaterialTheme.colorScheme.background,
                )
            }
        }
        item {
            AbiCard {
                CardTitle("Teil 2 – Prüfungsgespräch", icon = R.drawable.forum_24dp)
                ActionButton(
                    "Prüfungsgespräch",
                    R.drawable.forum_24dp,
                    accent,
                    { open(SessionSetup.defaults(SessionKind.PART2, 0, feedback)) },
                    subtitle = "10 Min. über größere Zusammenhänge, mit Themenwechsel",
                    trailing = lastPoints(SessionKind.PART2),
                    container = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("train_part2"),
                )
                Gap(8.dp)
                ActionButton(
                    "Freies Gesprächstraining",
                    R.drawable.chat_24dp,
                    accent,
                    { open(SessionSetup.defaults(SessionKind.TRAINING, 0, feedback)) },
                    subtitle = "Mit Flüstern oder Coach – der Prüfer korrigiert dich sofort",
                    trailing = lastPoints(SessionKind.TRAINING),
                    container = MaterialTheme.colorScheme.background,
                )
                if (focus.isNotEmpty()) {
                    Gap(8.dp)
                    ActionButton(
                        "Schwachstellen üben",
                        R.drawable.spellcheck_24dp,
                        yellow,
                        {
                            open(SessionSetup.defaults(SessionKind.TRAINING, 0, de.abicoach.data.FeedbackMode.COACH).copy(focusCategories = focus))
                        },
                        subtitle = "Fokus: " + focus.joinToString(", ") { de.abicoach.data.Knowledge.categoryLabel(it) },
                        container = MaterialTheme.colorScheme.background,
                    )
                }
            }
        }
        val recent = sessions.take(3)
        if (recent.isNotEmpty()) {
            item {
                AbiCard {
                    CardTitle("Zuletzt", icon = R.drawable.history_24dp)
                    recent.forEach { s -> SessionRow(s) { vm.tap(); if (s.debrief != null) vm.nav.push(Screen.Debrief(s.id)) else vm.openAnalysis(s.id) } }
                    Gap(6.dp)
                    ActionButton(
                        "Alle Sitzungen",
                        R.drawable.history_24dp,
                        MaterialTheme.colorScheme.onPrimary,
                        { vm.tap(); vm.nav.push(Screen.Sessions) },
                        container = MaterialTheme.colorScheme.background,
                    )
                }
            }
        }
    }
}

/** What the placement test does, then start. */
@Composable
fun PlacementIntroScreen(vm: AppViewModel) {
    val start = rememberMicGate { vm.start(SessionSetup.defaults(SessionKind.PLACEMENT, 0, de.abicoach.data.FeedbackMode.PRUEFUNG)) }
    DetailScaffold(title = "Einstufung", onBack = { vm.tap(); vm.nav.pop() }, tag = "placement") {
        AreaIntro(R.drawable.flag_24dp, "Wo stehst du gerade?", "In etwa 15 Minuten misst die App alle Kompetenzen der Prüfung – danach plant sie dein Training.", color = yellow)
        MessageBanners(vm)
        AbiCard {
            CardTitle("So läuft es ab", icon = R.drawable.checklist_24dp)
            BulletText("8 Minuten Vorbereitung: ein kurzer Text mit den drei typischen Aufgaben (zusammenfassen, analysieren, kommentieren).")
            BulletText("4 Minuten Vortrag – frei sprechen, Notizen sind erlaubt.")
            BulletText("4 Minuten Prüfungsgespräch mit dem KI-Prüfer.")
            BulletText("Auswertung nach dem NRW-Raster: Lernprofil, Punktprognose und deine größten Hebel.")
        }
        AbiCard {
            CardTitle("Tipps", icon = R.drawable.lightbulb_24dp, iconTint = yellow)
            BulletText("Ein ruhiger Raum hilft der Mitschrift.")
            BulletText("Mit Kopfhörern kannst du den Prüfer auch unterbrechen – wie im echten Gespräch.")
            BulletText("Sprich so, wie du es in der Prüfung tun würdest. Die Einstufung zählt nur für dein Profil.")
        }
        StartButton("Einstufung starten", onClick = start, modifier = Modifier.testTag("placement_start"))
        Spacer(Modifier.height(4.dp))
    }
}
