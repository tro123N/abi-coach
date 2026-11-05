package de.abicoach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.abicoach.R
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.learn.ActivityKind
import de.abicoach.learn.LearningBrain
import de.abicoach.learn.Phase
import de.abicoach.path.PathEngine
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
import de.abicoach.ui.components.HeroCard
import de.abicoach.ui.components.HeroChip
import de.abicoach.ui.components.HeroStat
import de.abicoach.ui.components.LevelBadge
import de.abicoach.ui.components.MiniAction
import de.abicoach.ui.components.Pill
import de.abicoach.ui.components.SectionLabel
import de.abicoach.ui.components.Segmented
import de.abicoach.ui.components.StartButton
import de.abicoach.ui.components.Tab
import de.abicoach.ui.components.Tile
import de.abicoach.ui.components.TopBarAction
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.pointsColor
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val todayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/** "Mittwoch, 4. November · noch 118 Tage" above the title of the start page. */
private fun todayCaption(days: Long?): String {
    val date = LocalDate.now().format(todayFormat)
    return when (days) {
        null -> date
        0L -> "$date · heute ist Prüfung"
        1L -> "$date · noch 1 Tag"
        else -> "$date · noch $days Tage"
    }
}

/** Start page "Heute": the forecast, the plan for today and the two most important next steps – nothing else. */
@Composable
fun AbiWegScreen(vm: AppViewModel) {
    val app = vm.app
    val version by app.settings.version.collectAsState()
    val profile by app.profiles.profile.collectAsState()
    val sessions by app.store.sessions.collectAsState()
    val docs by app.memory.docs.collectAsState()
    val progress by app.path.progress.collectAsState()
    val patterns by app.errorMemory.patterns.collectAsState()
    val habits by app.insights.all.collectAsState()
    val recommendations = remember(version, profile, sessions, docs, patterns, habits) { Planner.today(app) }
    val worlds = remember(docs) { app.worlds() }
    val next = remember(progress, profile, worlds) { PathEngine.next(worlds, progress, profile) }
    val due = remember(progress, worlds) { PathEngine.due(worlds, progress) }
    TabScaffold(
        vm,
        Tab.WEG,
        "Heute",
        caption = todayCaption(Planner.daysUntilExam(app)),
        actions = { TopBarAction(R.drawable.settings_24dp, "Einstellungen", { vm.tap(); vm.nav.push(Screen.Settings) }, tag = "action_settings") },
    ) {
        if (vm.errorMessage != null || vm.notice != null || vm.readyDebrief != null || vm.readyMaterialMinutes > 0) item { MessageBanners(vm) }
        item(key = "overview") { OverviewCard(vm, profile) }
        item(key = "plan") { PlanCard(vm, recommendations) }
        item(key = "next") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                if (next != null) {
                    Tile(
                        title = next.level.title,
                        subtitle = "Weiter auf dem Abi-Weg · Welt ${next.world.number}",
                        icon = R.drawable.terrain_24dp,
                        tint = green,
                        onClick = { vm.openLevel(next.level.id) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        tag = "today_next_level",
                    )
                } else {
                    Tile(
                        title = "Alle Levels geschafft",
                        subtitle = "Wiederhole regelmäßig, damit nichts einrostet",
                        icon = R.drawable.terrain_24dp,
                        tint = green,
                        onClick = { vm.tap(); vm.practiceView = 1; vm.nav.openTab(Tab.TRAINING) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                LastResultTile(vm, sessions.firstOrNull { it.debrief != null }, Modifier.weight(1f).fillMaxHeight())
            }
        }
        due.firstOrNull()?.let { d ->
            item(key = "refresh") {
                ActionButton(
                    text = "Auffrischen: ${d.level.title}",
                    subtitle = if (due.size > 1) "Noch ${due.size - 1} weitere fällig" else "Kurz wiederholen, damit es sitzt",
                    icon = R.drawable.refresh_24dp,
                    iconTint = yellow,
                    onClick = { vm.openLevel(d.level.id) },
                    modifier = Modifier.testTag("today_refresh"),
                )
            }
        }
    }
}

/** The colourful hero: forecast ring, grade, range, the projection to the exam day and three small numbers. */
@Composable
private fun OverviewCard(vm: AppViewModel, profile: Profile) {
    val app = vm.app
    val prognosis = profile.prognosis()
    val projection = remember(profile) { runCatching { LearningBrain.projection(app) }.getOrNull() }
    val days = Planner.daysUntilExam(app)
    val streak = app.store.streak()
    val today = app.store.minutesToday()
    HeroCard(modifier = Modifier.testTag("overview"), onClick = { vm.tap(); vm.nav.openTab(Tab.STATS) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigNumber(
                value = prognosis?.points?.toString() ?: "–",
                caption = "Prognose",
                color = Color.White,
                track = Color.White.copy(alpha = 0.25f),
                textColor = Color.White,
                size = 86.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (prognosis != null) {
                    Text("${prognosis.points} Punkte – ${Planner.gradeLabel(prognosis.points)}", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    val trend = profile.trend()
                    val trendText =
                        when {
                            trend == null || trend == 0.0 -> ""
                            trend > 0 -> " · ▲ ${points(trend)} in zwei Wochen"
                            else -> " · ▼ ${points(-trend)} in zwei Wochen"
                        }
                    Text("Spanne ${prognosis.lowPoints}–${prognosis.highPoints}$trendText", fontSize = 13.sp, lineHeight = 17.sp, color = Color.White.copy(alpha = 0.9f))
                    if (projection != null) {
                        HeroChip("Am Prüfungstag etwa ${"%.0f".format(projection.points)}", Modifier.padding(top = 5.dp).testTag("projection"))
                    }
                } else {
                    Text("Noch keine Prognose", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Nach der Einstufung siehst du hier, wo du stehst und was dir bis zur 15 fehlt.", fontSize = 13.sp, lineHeight = 17.sp, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        Gap(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            HeroStat(days?.toString() ?: "?", if (days == 1L) "Tag bis zum Abi" else "Tage bis zum Abi", Modifier.weight(1f).fillMaxHeight())
            HeroStat(streak.toString(), if (streak == 1) "Tag in Folge" else "Tage in Folge", Modifier.weight(1f).fillMaxHeight())
            HeroStat("$today/${app.settings.dailyMinutes}", "Min. heute", Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * One card instead of two: the plan the learning brain calculated (what brings the most points per minute),
 * explained in two sentences by the AI coach.
 */
@Composable
private fun PlanCard(vm: AppViewModel, recommendations: List<Recommendation>) {
    val briefing by vm.app.briefings.current.collectAsState()
    val sessions by vm.app.store.sessions.collectAsState()
    LaunchedEffect(sessions.size, vm.app.ai.ready) { vm.refreshBriefing(false) }
    val phase = remember(recommendations) { LearningBrain.phase(vm.app) }
    AbiCard(modifier = Modifier.testTag("today")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Dein Plan für heute",
                fontSize = AbiTheme.dimens.fonts.label * 0.9f,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f),
            )
            if (phase != Phase.START) Pill("Phase ${phase.label}")
            if (briefing != null && !vm.briefingBusy) {
                IconButton(onClick = { vm.tap(); vm.refreshBriefing(true) }, modifier = Modifier.size(36.dp).testTag("briefing_refresh")) {
                    Icon(painterResource(R.drawable.refresh_24dp), contentDescription = "Neu erklären lassen", tint = accent, modifier = Modifier.size(20.dp))
                }
            }
        }
        Gap(6.dp)
        val b = briefing
        when {
            b != null ->
                Column(Modifier.testTag("briefing")) {
                    Text(b.headline, fontSize = AbiTheme.dimens.fonts.body, lineHeight = AbiTheme.dimens.fonts.body * 1.3f, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
                    Gap(2.dp)
                    CaptionText(b.focus)
                }
            vm.briefingBusy ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    CaptionText("Der KI-Coach schaut auf deinen Stand und erklärt dir den Plan …")
                }
            else ->
                CaptionText(
                    if (phase == Phase.START) {
                        "Erst die Einstufung – dann rechnet die App aus, was dir am meisten bringt."
                    } else {
                        "Berechnet aus deinem ganzen Lernstand: was dir pro Minute die meisten Punkte bringt."
                    },
                )
        }
        vm.briefingError?.let { CaptionText(it, color = red, modifier = Modifier.padding(top = 4.dp)) }
        Gap(10.dp)
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
        if (b != null) {
            Gap(8.dp)
            CaptionText("Plan: Lern-Gehirn der App · erklärt von ${b.by}")
        }
    }
}

/** The tile with the last evaluation: points and how many of the goals were reached. */
@Composable
private fun LastResultTile(vm: AppViewModel, last: SessionRecord?, modifier: Modifier) {
    val d = last?.debrief
    if (last == null || d == null) {
        Tile(
            title = "Noch keine Auswertung",
            subtitle = "Nach deiner ersten Übung steht sie hier",
            icon = R.drawable.grading_24dp,
            tint = accent,
            onClick = { vm.tap(); vm.nav.openTab(Tab.TRAINING) },
            modifier = modifier,
        )
        return
    }
    val goals = last.goals.size
    val reached = d.goalResults.count { it.reached }
    Tile(
        title = "${d.totalPoints} Punkte · ${last.kind.short}",
        subtitle = if (goals > 0) "Letzte Auswertung · $reached von $goals Zielen erreicht" else "Letzte Auswertung · ${formatDate(last.startedAt)}",
        icon = kindIcon(last.kind),
        tint = pointsColor(d.totalPoints.toDouble()),
        onClick = { vm.tap(); vm.nav.push(Screen.Debrief(last.id)) },
        modifier = modifier,
        tag = "today_last",
    )
}

/** "Dein Stand": content and presentation with the biggest levers (shown under Fortschritt → Kompetenzen). */
@Composable
fun AreasCard(vm: AppViewModel, profile: Profile) {
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
            MiniAction(R.drawable.spellcheck_24dp, "Sprech-Training", { vm.openDrills() }, Modifier.weight(1f), tint = yellow)
            MiniAction(R.drawable.mic_24dp, "Üben", { vm.tap(); vm.practiceView = 0; vm.nav.openTab(Tab.TRAINING) }, Modifier.weight(1f))
            MiniAction(R.drawable.school_24dp, "Kurs", { vm.tap(); vm.nav.openTab(Tab.KURS) }, Modifier.weight(1f))
        }
    }
}

/** The milestones on the way to 15 points (shown under Fortschritt). */
@Composable
fun MilestonesCard(milestones: List<Milestone>) {
    val dimens = AbiTheme.dimens
    val next = milestones.indexOfFirst { !it.done }
    AbiCard(modifier = Modifier.testTag("milestones")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Meilensteine", icon = R.drawable.trophy_24dp, iconTint = yellow, modifier = Modifier.weight(1f))
            CaptionText("${milestones.count { it.done }} von ${milestones.size}")
        }
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

/** "Üben": free practice as tiles, grouped by part of the exam – or the Abi-Weg with its worlds and levels. */
@Composable
fun TrainingScreen(vm: AppViewModel) {
    val app = vm.app
    val sessions by app.store.sessions.collectAsState()
    val profile by app.profiles.profile.collectAsState()
    val docs by app.memory.docs.collectAsState()
    val progress by app.path.progress.collectAsState()
    val cards by app.vocab.cards.collectAsState()
    val patterns by app.errorMemory.patterns.collectAsState()
    val dueCards = remember(cards) { app.vocab.due().size }
    val duePatterns = remember(patterns) { app.errorMemory.due().size }
    val worlds = remember(docs) { app.worlds() }
    val dueLevels = remember(progress, worlds) { PathEngine.due(worlds, progress).size }
    val due = dueCards.coerceAtMost(1) + duePatterns.coerceAtMost(1) + dueLevels.coerceAtMost(1)
    TabScaffold(
        vm,
        Tab.TRAINING,
        "Üben",
        caption = if (due > 0) (if (due == 1) "1 Sache ist heute fällig" else "$due Dinge sind heute fällig") else "Frei üben oder Level für Level",
        actions = { TopBarAction(R.drawable.settings_24dp, "Einstellungen", { vm.tap(); vm.nav.push(Screen.Settings) }) },
    ) {
        item(key = "views") {
            Segmented(
                options = listOf("Frei üben", if (dueLevels > 0) "Abi-Weg · $dueLevels" else "Abi-Weg"),
                selected = vm.practiceView,
                onSelect = { vm.tap(); vm.practiceView = it },
                tags = listOf("practice_free", "practice_path"),
            )
        }
        if (vm.errorMessage != null || vm.notice != null || vm.readyDebrief != null || vm.readyMaterialMinutes > 0) item { MessageBanners(vm) }
        if (vm.practiceView == 1) {
            item(key = "climb") { ClimbCard(vm, worlds, progress, profile) }
            val next = PathEngine.next(worlds, progress, profile)
            worlds.forEachIndexed { index, world ->
                item(key = world.id) {
                    WorldCard(
                        vm = vm,
                        world = world,
                        worldIndex = index,
                        worlds = worlds,
                        progress = progress,
                        profile = profile,
                        nextId = next?.level?.id,
                        openByDefault = next?.world?.id == world.id,
                    )
                }
            }
        } else {
            freePractice(vm, sessions, dueCards, duePatterns, patterns.isEmpty(), cards.isEmpty())
        }
    }
}

/** The tiles of "Frei üben". */
private fun LazyListScope.freePractice(
    vm: AppViewModel,
    sessions: List<SessionRecord>,
    dueCards: Int,
    duePatterns: Int,
    noPatterns: Boolean,
    noCards: Boolean,
) {
    val app = vm.app
    val feedback = app.settings.defaultFeedback

    fun last(kind: SessionKind, task: Int = 0): Int? =
        sessions.firstOrNull { it.kind == kind && it.focusTask == task && it.debrief != null }?.debrief?.totalPoints

    fun open(setup: SessionSetup) {
        vm.tap()
        vm.nav.push(Screen.Setup(setup))
    }

    item(key = "label_exam") { SectionLabel("Ganze Prüfung") }
    item(key = "exam") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Tile(
                title = "Simulation",
                subtitle = "Vorbereitung, Vortrag und Gespräch – wie im Abi",
                icon = R.drawable.workspace_premium_24dp,
                tint = green,
                onClick = { open(SessionSetup.defaults(SessionKind.SIMULATION, 0, feedback)) },
                modifier = Modifier.weight(1.3f).fillMaxHeight(),
                chip = last(SessionKind.SIMULATION)?.let { "$it P." to Color.White },
                hero = true,
                tag = "train_simulation",
            )
            Tile(
                title = "Einstufung",
                subtitle = "12 Min. · misst deinen Stand neu",
                icon = R.drawable.flag_24dp,
                tint = yellow,
                onClick = { vm.tap(); vm.nav.push(Screen.PlacementIntro) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                chip = last(SessionKind.PLACEMENT)?.let { "$it P." to pointsColor(it.toDouble()) },
                tag = "train_placement",
            )
        }
    }
    item(key = "label_part1") { SectionLabel("Teil 1 – Vortrag") }
    item(key = "part1") {
        val lever =
            remember(sessions) {
                runCatching {
                    LearningBrain.candidates(app)
                        .filter { it.kind == ActivityKind.TASK1 || it.kind == ActivityKind.TASK2 || it.kind == ActivityKind.TASK3 }
                        .maxByOrNull { it.value }
                        ?.kind
                }.getOrNull()
            }
        val tasks =
            listOf(
                Triple(1, "Zusammenfassen", "summarise, outline · 2–3 Min."),
                Triple(2, "Analysieren", "examine, analyse · 3–4 Min."),
                Triple(3, "Kommentieren", "comment, discuss · 3 Min."),
            )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Tile(
                    title = "Ganzer Vortrag",
                    subtitle = "Alle drei Aufgaben · ca. 10 Min.",
                    icon = R.drawable.co_present_24dp,
                    tint = accent,
                    onClick = { open(SessionSetup.defaults(SessionKind.PART1, 0, feedback)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    chip = last(SessionKind.PART1, 0)?.let { "$it P." to pointsColor(it.toDouble()) },
                    tag = "train_part1",
                )
                TaskTile(vm, tasks[0], lever, last(SessionKind.PART1, 1), Modifier.weight(1f).fillMaxHeight()) { open(SessionSetup.defaults(SessionKind.PART1, 1, feedback)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                TaskTile(vm, tasks[1], lever, last(SessionKind.PART1, 2), Modifier.weight(1f).fillMaxHeight()) { open(SessionSetup.defaults(SessionKind.PART1, 2, feedback)) }
                TaskTile(vm, tasks[2], lever, last(SessionKind.PART1, 3), Modifier.weight(1f).fillMaxHeight()) { open(SessionSetup.defaults(SessionKind.PART1, 3, feedback)) }
            }
        }
    }
    item(key = "real") { RealTasksCard(vm) }
    item(key = "label_part2") { SectionLabel("Teil 2 – Prüfungsgespräch") }
    item(key = "part2") {
        val focus = remember(sessions) { Planner.topErrorCategories(app, 2) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Tile(
                    title = "Prüfungsgespräch",
                    subtitle = "10 Min. über größere Zusammenhänge",
                    icon = R.drawable.forum_24dp,
                    tint = accent,
                    onClick = { open(SessionSetup.defaults(SessionKind.PART2, 0, feedback)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    chip = last(SessionKind.PART2)?.let { "$it P." to pointsColor(it.toDouble()) },
                    tag = "train_part2",
                )
                Tile(
                    title = "Freies Training",
                    subtitle = "Der Prüfer korrigiert dich sofort",
                    icon = R.drawable.chat_24dp,
                    tint = accent,
                    onClick = { open(SessionSetup.defaults(SessionKind.TRAINING, 0, feedback)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    chip = last(SessionKind.TRAINING)?.let { "$it P." to pointsColor(it.toDouble()) },
                    tag = "train_free",
                )
            }
            if (focus.isNotEmpty()) {
                ActionButton(
                    "Schwachstellen üben",
                    R.drawable.spellcheck_24dp,
                    yellow,
                    { open(SessionSetup.defaults(SessionKind.TRAINING, 0, de.abicoach.data.FeedbackMode.COACH).copy(focusCategories = focus)) },
                    subtitle = "Gespräch mit Fokus: " + focus.joinToString(", ") { de.abicoach.data.Knowledge.categoryLabel(it) },
                    modifier = Modifier.testTag("train_focus"),
                )
            }
        }
    }
    item(key = "label_target") { SectionLabel("Gezielt trainieren") }
    item(key = "target") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("train_coach")) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Tile(
                    title = "Fehler-Werkstatt",
                    subtitle = if (noPatterns) "Füllt sich mit jeder Übung" else "Deine Fehler Schritt für Schritt beheben",
                    icon = R.drawable.school_24dp,
                    tint = if (duePatterns > 0) yellow else accent,
                    onClick = { vm.openWorkshop() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    badge = duePatterns,
                    tag = "train_workshop",
                )
                Tile(
                    title = "Kompetenzen",
                    subtitle = "Alle 11 einzeln üben",
                    icon = R.drawable.target_24dp,
                    tint = green,
                    onClick = { vm.tap(); vm.nav.push(Screen.Skills) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    tag = "train_skills",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Tile(
                    title = "Sprech-Training",
                    subtitle = if (noPatterns) "Füllt sich mit jeder Übung" else "Kurze Übungen zu deinen Fehlern",
                    icon = R.drawable.spellcheck_24dp,
                    tint = if (duePatterns > 0) yellow else accent,
                    onClick = { vm.openDrills() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    badge = duePatterns,
                    tag = "train_drills",
                )
                Tile(
                    title = "Wortschatz",
                    subtitle = if (noCards) "Füllt sich mit jeder Auswertung" else "Karten nach 1, 3, 7 … Tagen",
                    icon = R.drawable.note_stack_24dp,
                    tint = if (dueCards > 0) yellow else green,
                    onClick = { vm.openVocab() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    badge = dueCards,
                    tag = "train_vocab",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniAction(R.drawable.speed_24dp, "Sprint", { open(SessionSetup.sprint(feedback)) }, Modifier.weight(1f), tag = "train_sprint")
                MiniAction(R.drawable.graphic_eq_24dp, "Aussprache", { vm.openPronunciation() }, Modifier.weight(1f), tag = "train_pron")
                MiniAction(R.drawable.psychology_24dp, "KI-Coach", { vm.tap(); vm.nav.push(Screen.Ask) }, Modifier.weight(1f), tag = "train_ask")
            }
        }
    }
    val recent = sessions.take(3)
    if (recent.isNotEmpty()) {
        item(key = "recent") {
            AbiCard {
                CardTitle("Zuletzt", icon = R.drawable.history_24dp)
                recent.forEach { s -> SessionRow(s) { vm.openSession(s.id) } }
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

/** One task of part 1 as a tile; the task with the biggest lever gets an accent frame. */
@Composable
private fun TaskTile(vm: AppViewModel, task: Triple<Int, String, String>, lever: ActivityKind?, lastPoints: Int?, modifier: Modifier, onClick: () -> Unit) {
    val (number, title, hint) = task
    val isLever = lever?.name == "TASK$number"
    Tile(
        title = "Aufgabe $number: $title",
        subtitle = if (isLever) "Dein größter Hebel gerade" else hint,
        icon =
            when (number) {
                1 -> R.drawable.summarize_24dp
                2 -> R.drawable.insights_24dp
                else -> R.drawable.rate_review_24dp
            },
        tint = accent,
        onClick = onClick,
        modifier = modifier,
        chip = lastPoints?.let { "$it P." to pointsColor(it.toDouble()) },
        highlight = isLever,
        subtitleColor = if (isLever) accent else null,
        tag = "train_task$number",
    )
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
