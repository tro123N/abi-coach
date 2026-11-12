package de.abicoach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.abicoach.R
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.learn.ActivityKind
import de.abicoach.learn.Goal
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
import de.abicoach.ui.components.ChoiceChips
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.HeroCard
import de.abicoach.ui.components.HeroChip
import de.abicoach.ui.components.HeroStat
import de.abicoach.ui.components.LevelBadge
import de.abicoach.ui.components.MiniAction
import de.abicoach.ui.components.Pill
import de.abicoach.ui.components.PopUp
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
import de.abicoach.ui.tourTarget
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
    val update by de.abicoach.update.AppUpdater.state.collectAsState()
    val profile by app.profiles.profile.collectAsState()
    val sessions by app.store.sessions.collectAsState()
    val docs by app.memory.docs.collectAsState()
    val progress by app.path.progress.collectAsState()
    val patterns by app.errorMemory.patterns.collectAsState()
    val habits by app.insights.all.collectAsState()
    TabScaffold(
        vm,
        Tab.WEG,
        "Heute",
        caption = todayCaption(Planner.daysUntilExam(app)),
    ) {
        if (vm.errorMessage != null || vm.notice != null || vm.readyDebrief != null || vm.readyMaterialMinutes > 0) item { MessageBanners(vm) }
        if (vm.crashReport != null || vm.recoveredSession != null || vm.backupDue) item(key = "system_banners") { SystemBanners(vm) }
        // Without a key nothing works: say so first and lead to the setup.
        if (version >= 0 && !app.ai.ready) {
            item(key = "setup_missing") {
                ActionButton(
                    "Einrichtung abschließen",
                    R.drawable.key_24dp,
                    red,
                    { vm.openOnboarding() },
                    subtitle = "Der KI-Schlüssel fehlt noch – ohne ihn kann die App nicht prüfen und bewerten.",
                    modifier = Modifier.testTag("today_setup"),
                )
            }
        }
        (update as? de.abicoach.update.AppUpdater.State.Available)?.let { u ->
            item(key = "update") {
                ActionButton(
                    "Update verfügbar: Build ${u.release.build}",
                    R.drawable.download_24dp,
                    green,
                    { vm.tap(); vm.settingsSection = de.abicoach.ui.screens.SettingsSection.UEBER.name; vm.nav.push(Screen.Settings) },
                    subtitle = "Tippe, um es direkt in der App zu installieren",
                    modifier = Modifier.testTag("today_update"),
                )
            }
        }
        item(key = "overview") { Box(Modifier.tourTarget("today_overview")) { OverviewCard(vm, profile) } }
        item(key = "plan") { Box(Modifier.tourTarget("today_plan")) { DayPlanCard(vm) } }
        // The last evaluation, one tap away – everything else lives under "Üben" and "Fortschritt".
        sessions.firstOrNull { it.debrief != null }?.let { last ->
            val d = last.debrief ?: return@let
            val reached = d.goalResults.count { it.reached }
            item(key = "last") {
                ActionButton(
                    text = "Letzte Auswertung: ${d.totalPoints} Punkte · ${last.kind.short}",
                    subtitle = if (last.goals.isNotEmpty()) "$reached von ${last.goals.size} Zielen erreicht · ${formatDate(last.startedAt)}" else formatDate(last.startedAt),
                    icon = kindIcon(last.kind),
                    iconTint = pointsColor(d.totalPoints.toDouble()),
                    onClick = { vm.tap(); vm.nav.push(Screen.Debrief(last.id)) },
                    modifier = Modifier.testTag("today_last"),
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
                    Text("Nach der Einstufung siehst du hier, wo du stehst und was dir bis zu deinem Ziel fehlt.", fontSize = 13.sp, lineHeight = 17.sp, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
        Gap(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            HeroStat(days?.toString() ?: "?", if (days == 1L) "Tag bis zum Abi" else "Tage bis zum Abi", Modifier.weight(1f).fillMaxHeight())
            HeroStat(streak.toString(), if (streak == 1) "Tag in Folge" else "Tage in Folge", Modifier.weight(1f).fillMaxHeight())
            HeroStat("$today/${app.settings.dailyMinutes}", "Min. heute", Modifier.weight(1f).fillMaxHeight())
        }
        Gap(8.dp)
        // The goal the whole app steers by – a tap changes it.
        val settingsTick by app.settings.version.collectAsState()
        val goal = remember(profile, settingsTick) { Goal.state(app.settings.targetPoints, prognosis?.total, projection?.points) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = if (goal.status == Goal.Status.UNSET) 0.26f else 0.16f))
                    .clickable { vm.tap(); vm.showGoal = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("goal_row"),
        ) {
            Icon(painterResource(if (goal.status == Goal.Status.REACHED) R.drawable.task_alt_24dp else R.drawable.target_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.headline, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(goal.detail, fontSize = 12.sp, lineHeight = 16.sp, color = Color.White.copy(alpha = 0.9f))
            }
            Icon(painterResource(R.drawable.edit_note_24dp), contentDescription = "Ziel ändern", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        }
    }
    if (vm.showGoal) GoalDialog(vm)
}

/** Choosing the goal: how many points the oral exam should bring. */
@Composable
fun GoalDialog(vm: AppViewModel) {
    val settings = vm.app.settings
    var pick by remember { mutableStateOf(settings.targetPoints.takeIf { it > 0 } ?: 11) }
    PopUp(
        title = "Dein Ziel",
        description = "Wie viele Punkte willst du in der mündlichen Prüfung erreichen?",
        confirmText = "Übernehmen",
        onConfirm = {
            vm.showGoal = false
            if (settings.targetPoints != pick) {
                settings.targetPoints = pick
                // The plan follows the new goal at once.
                vm.ensureDay(force = true)
                vm.notice = "Ziel: $pick Punkte. Dein Plan richtet sich jetzt danach."
            }
        },
        onDismiss = { vm.showGoal = false },
    ) {
        ChoiceChips(Goal.CHOICES, pick, { "$it P." }, { vm.tap(); pick = it }, tagPrefix = "goal")
        Gap(10.dp)
        BodyText("$pick Punkte = ${Planner.gradeLabel(pick)}")
        Gap(6.dp)
        CaptionText("Danach richtet sich die App: Der Tagesplan übt zuerst, was noch unter deinem Ziel liegt. Die Übersicht zeigt, wie viel noch fehlt und ob dein Tempo reicht. Die Bewertung selbst bleibt gleich streng.")
    }
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
    ) {
        item(key = "views") {
            Segmented(
                options = listOf("Frei üben", if (dueLevels > 0) "Abi-Weg · $dueLevels" else "Abi-Weg"),
                selected = vm.practiceView,
                onSelect = { vm.tap(); vm.practiceView = it },
                tags = listOf("practice_free", "practice_path"),
                modifier = Modifier.tourTarget("train_views"),
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

    // During the guided tour everything is unfolded, so every stop of the tour can be shown.
    fun isOpen(group: String): Boolean = group in vm.practiceOpen || vm.tour.step != null

    // What the plan says comes first: one tap, no searching.
    item(key = "plan_next") { PlanNextCard(vm) }
    item(key = "label_exam") { GroupHeader(vm, "exam", "Ganze Prüfung", "Simulation und Einstufung", isOpen("exam")) }
    if (isOpen("exam")) item(key = "exam") {
        Box(Modifier.tourTarget("train_exam")) {
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
}
    item(key = "label_part1") { GroupHeader(vm, "part1", "Teil 1 – Vortrag", "Ganzer Vortrag, einzelne Aufgaben, echte Prüfungsaufgaben", isOpen("part1")) }
    if (isOpen("part1")) item(key = "part1") {
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
    if (isOpen("part1")) item(key = "real") { RealTasksCard(vm) }
    item(key = "label_part2") { GroupHeader(vm, "part2", "Teil 2 – Prüfungsgespräch", "Gespräch, freies Training, Schwachstellen", isOpen("part2")) }
    if (isOpen("part2")) item(key = "part2") {
        Box(Modifier.tourTarget("train_part2")) {
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
}
    item(key = "label_target") {
        val dueNow = duePatterns + dueCards
        GroupHeader(vm, "target", "Gezielt trainieren", "Fehler, Wortschatz, Aussprache, Kompetenzen" + if (dueNow > 0) " · $dueNow fällig" else "", isOpen("target"))
    }
    if (isOpen("target")) item(key = "target") {
        Box(Modifier.tourTarget("train_target")) {
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

/**
 * The heading of a group of "Frei üben". A tap unfolds the group; folded, it says in one line what is inside – so
 * the page shows five clear headings instead of fifteen tiles.
 */
@Composable
private fun GroupHeader(vm: AppViewModel, group: String, title: String, inside: String, open: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { vm.togglePractice(group) }
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .testTag("group_$group"),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = AbiTheme.dimens.fonts.body, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            androidx.compose.animation.AnimatedVisibility(!open) { CaptionText(inside) }
        }
        val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 300f), label = "groupTurn")
        Icon(
            painterResource(R.drawable.keyboard_arrow_down_24dp),
            contentDescription = if (open) "Zuklappen" else "Aufklappen",
            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = turn },
        )
    }
}

/** On "Üben": the step of today's plan that is on now – so nobody has to choose among the tiles. */
@Composable
private fun PlanNextCard(vm: AppViewModel) {
    val stored by vm.app.daily.day.collectAsState()
    val day = stored?.takeIf { it.date == LocalDate.now().toString() } ?: return
    val next = day.next
    if (next != null) {
        ActionButton(
            text = "Jetzt dran: ${next.title}",
            subtitle = "Schritt ${day.doneCount + 1} von ${day.steps.size} deines Tagesplans · ca. ${next.minutes} Min.",
            icon = runCatching { ActivityKind.valueOf(next.kind).icon }.getOrDefault(R.drawable.target_24dp),
            iconTint = accent,
            onClick = { vm.openStep(next) },
            modifier = Modifier.testTag("train_plan_next"),
        )
    } else if (day.steps.isNotEmpty()) {
        ActionButton(
            text = "Tagesplan geschafft",
            subtitle = "Alles Weitere ist Zugabe – such dir unten aus, worauf du Lust hast.",
            icon = R.drawable.task_alt_24dp,
            iconTint = green,
            onClick = { vm.tap(); vm.nav.openTab(Tab.WEG) },
            modifier = Modifier.testTag("train_plan_done"),
        )
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
