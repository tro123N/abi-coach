package de.abicoach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.data.SessionRecord
import de.abicoach.ui.components.LegendItem
import de.abicoach.ui.components.MultiLineChart
import de.abicoach.ui.components.ChartSeries
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionKind
import de.abicoach.profile.Area
import de.abicoach.profile.Skill
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.Planner
import de.abicoach.ui.Screen
import de.abicoach.ui.SessionSetup
import de.abicoach.ui.TabScaffold
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.Segmented
import de.abicoach.ui.components.BarRow
import de.abicoach.ui.components.BigNumber
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.ChartGuide
import de.abicoach.ui.components.ColorBarChart
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.LineChart
import de.abicoach.ui.components.StatTile
import de.abicoach.ui.components.Tab
import de.abicoach.ui.components.TopBarAction
import de.abicoach.ui.components.PopUp
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.pointsColor
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow

/** "Fortschritt" in four short sections instead of one long list: overview, competences, mistakes, topic fields. */
@Composable
fun StatsScreen(vm: AppViewModel) {
    val app = vm.app
    val version by app.settings.version.collectAsState()
    val profile by app.profiles.profile.collectAsState()
    val sessions by app.store.sessions.collectAsState()
    val docs by app.memory.docs.collectAsState()
    val prognosis = profile.prognosis()
    val dimens = AbiTheme.dimens
    val minutes = remember(sessions) { app.store.minutesPerDay(14) }
    val errorCounts = remember(sessions, profile) {
        val map = HashMap<String, Int>()
        app.store.categoryCounts().forEach { (k, v) -> map[k] = (map[k] ?: 0) + v }
        profile.klausurErrors.forEach { (k, v) -> map[k] = (map[k] ?: 0) + v }
        map.filterKeys { it != "CONTENT" }.entries.sortedByDescending { it.value }.take(6)
    }
    val milestones = remember(version, profile, sessions, docs) { Planner.milestones(app) }
    val first = sessions.minOfOrNull { it.startedAt }

    TabScaffold(
        vm,
        Tab.STATS,
        "Fortschritt",
        caption = if (first == null) "Nach dem NRW-Raster: Inhalt 40 %, Darstellung 60 %" else "Seit ${formatDay(first)} · ${sessions.size} " + (if (sessions.size == 1) "Übung" else "Übungen"),
    ) {
        item(key = "views") {
            Segmented(
                options = listOf("Überblick", "Kompetenzen", "Fehler", "Themen"),
                selected = vm.progressView,
                onSelect = { vm.tap(); vm.progressView = it },
                tags = listOf("progress_overview", "progress_skills", "progress_errors", "progress_topics"),
            )
        }
        when (vm.progressView) {
            1 -> {
                if (prognosis == null) {
                    item { EmptyProgress(vm) }
                } else {
                    item { AreasCard(vm, profile) }
                    item(key = "skill_dev") { DevelopmentCard(vm, sessions, startView = 2) }
                    item(key = "skill_train") {
                        ActionButton(
                            "Kompetenzen einzeln üben",
                            R.drawable.target_24dp,
                            yellow,
                            { vm.tap(); vm.nav.push(Screen.Skills) },
                            subtitle = "Jede der 11 Kompetenzen mit eigener Übung",
                            modifier = Modifier.testTag("stats_skills_train"),
                        )
                    }
                    item {
                        AbiCard(modifier = Modifier.testTag("stats_skills")) {
                            CardTitle("Kompetenzen", icon = R.drawable.insights_24dp)
                            Area.entries.forEach { area ->
                                Text("${area.label} (${(area.weight * 100).toInt()} %)", fontSize = dimens.fonts.body, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = accent)
                                Gap(4.dp)
                                Skill.entries.filter { it.area == area }.forEach { s ->
                                    val state = profile.skills[s]
                                    if (state == null) {
                                        CaptionText("${s.label}: noch nicht gemessen")
                                    } else {
                                        BarRow(s.label, (state.score / 15).toFloat(), points(state.score) + " P." + trendMark(profile.skillTrend(s)), pointsColor(state.score))
                                    }
                                    Gap(6.dp)
                                }
                                Gap(6.dp)
                            }
                            val levers = profile.levers(3)
                            if (levers.isNotEmpty()) CaptionText("Größte Hebel: " + levers.joinToString(", ") { it.label })
                            CaptionText("↑ / ↓: Trend der letzten Auswertungen")
                        }
                    }
                }
                item { ExaminerProfileCard(vm) }
            }
            2 -> {
                if (errorCounts.isNotEmpty()) {
                    item {
                        AbiCard(modifier = Modifier.testTag("stats_errors")) {
                            CardTitle("Häufigste Fehler", icon = R.drawable.spellcheck_24dp, iconTint = red)
                            val max = errorCounts.first().value.coerceAtLeast(1)
                            errorCounts.forEach { (k, v) ->
                                BarRow(Knowledge.categoryLabel(k), v.toFloat() / max, "$v×", red)
                                Gap(6.dp)
                            }
                            CaptionText("Aus deinen Übungen" + if (profile.klausurErrors.isNotEmpty()) " und Klausuren" else "")
                            Gap(8.dp)
                            ActionButton(
                                "Gezielt üben",
                                R.drawable.spellcheck_24dp,
                                yellow,
                                {
                                    vm.tap()
                                    vm.nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.TRAINING, 0, FeedbackMode.COACH).copy(focusCategories = errorCounts.take(2).map { it.key })))
                                },
                                subtitle = "Gesprächstraining mit Coach-Korrekturen",
                                container = MaterialTheme.colorScheme.background,
                            )
                        }
                    }
                }
                item { ErrorMemoryCard(vm) }
            }
            3 -> item { TopicCompassCard(vm) }
            else -> {
                item(key = "development") { DevelopmentCard(vm, sessions) }
                item {
                    AbiCard(modifier = Modifier.testTag("stats_prognosis")) {
                        CardTitle("So entwickelst du dich", icon = R.drawable.trending_up_24dp)
                        if (prognosis == null) {
                            BodyText("Noch keine Daten. Mach die Einstufung oder eine Übung – danach erscheint hier deine Prognose.")
                            Gap(8.dp)
                            ActionButton("Zur Einstufung", R.drawable.flag_24dp, yellow, { vm.tap(); vm.nav.push(Screen.PlacementIntro) }, container = MaterialTheme.colorScheme.background)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BigNumber(prognosis.points.toString(), "Punkte", pointsColor(prognosis.total))
                                Spacer(Modifier.width(16.dp))
                                Column2 {
                                    Text(Planner.gradeLabel(prognosis.points), fontSize = dimens.fonts.label, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = pointsColor(prognosis.total))
                                    CaptionText("Spanne ${prognosis.lowPoints}–${prognosis.highPoints} · Sicherheit ${(prognosis.confidence * 100).toInt()} %")
                                }
                            }
                            ProjectionBlock(vm)
                            if (profile.history.size >= 2) {
                                Gap(12.dp)
                                LineChart(
                                    values = profile.history.takeLast(30).map { it.total.toFloat() },
                                    color = accent,
                                    guides = listOf(ChartGuide(13f, "13", green), ChartGuide(10f, "10", yellow)),
                                    minY = maxOf(0f, (profile.history.takeLast(30).minOf { it.total }.toFloat() - 2f)),
                                    maxY = 15f,
                                    valueText = { points(it.toDouble()) },
                                )
                                CaptionText("Verlauf der Prognose nach jeder Auswertung")
                            }
                        }
                    }
                }
                if (prognosis != null) item { ReadinessCard(profile) }
                item { InsightsCard(vm) }
                item { MilestonesCard(milestones) }
                item {
                    AbiCard {
                        CardTitle("Übungszeit", icon = R.drawable.timer_24dp)
                        val total = sessions.sumOf { it.durationSec } / 60
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                            StatTile(sessions.size.toString(), "Sitzungen", Modifier.weight(1f).fillMaxHeight(), icon = R.drawable.history_24dp, color = accent)
                            StatTile("$total", "Minuten gesamt", Modifier.weight(1f).fillMaxHeight(), icon = R.drawable.timer_24dp, color = accent)
                            StatTile(app.store.streak().toString(), "Tage in Folge", Modifier.weight(1f).fillMaxHeight(), icon = R.drawable.whatshot_24dp, color = red)
                        }
                        Gap(12.dp)
                        ColorBarChart(
                            values = minutes.map { it.second.toFloat() },
                            colors = minutes.map { if (it.second >= app.settings.dailyMinutes) green else accent },
                            labels = minutes.mapIndexed { i, p -> if (i % 3 == 0 || i == minutes.lastIndex) "${p.first.dayOfMonth}." else "" },
                            guide = ChartGuide(app.settings.dailyMinutes.toFloat(), "Ziel", yellow),
                        )
                        CaptionText("Minuten pro Tag, letzte 14 Tage")
                    }
                }
                val recent = sessions.take(5)
                if (recent.isNotEmpty()) {
                    item {
                        AbiCard {
                            CardTitle("Letzte Sitzungen", icon = R.drawable.history_24dp)
                            recent.forEach { s -> SessionRow(s) { vm.openSession(s.id) } }
                            Gap(6.dp)
                            ActionButton("Alle Sitzungen", R.drawable.history_24dp, MaterialTheme.colorScheme.onPrimary, { vm.tap(); vm.nav.push(Screen.Sessions) }, container = MaterialTheme.colorScheme.background)
                        }
                    }
                }
            }
        }
    }
}

/** Shown in a section that needs measurements first. */
@Composable
private fun EmptyProgress(vm: AppViewModel) {
    AbiCard {
        CardTitle("Noch nichts gemessen", icon = R.drawable.flag_24dp, iconTint = yellow)
        BodyText("Mach die Einstufung oder eine Übung – danach siehst du hier jede Kompetenz des Bewertungsrasters.")
        Gap(8.dp)
        ActionButton("Zur Einstufung", R.drawable.flag_24dp, yellow, { vm.tap(); vm.nav.push(Screen.PlacementIntro) }, container = MaterialTheme.colorScheme.background)
    }
}

@Composable
private fun Column2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column { content() }
}

/** All sessions, newest first; select several and delete them completely. */
@Composable
fun SessionsScreen(vm: AppViewModel) {
    val sessions by vm.app.store.sessions.collectAsState()
    var selecting by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    // Sessions deleted elsewhere leave the selection.
    val picked = chosen.intersect(sessions.map { it.id }.toSet())
    fun toggle(id: String) {
        chosen = if (id in chosen) chosen - id else chosen + id
    }
    fun leave() {
        selecting = false
        chosen = emptySet()
    }
    if (confirm) {
        PopUp(
            title = if (picked.size == 1) "Sitzung löschen?" else "${picked.size} Sitzungen löschen?",
            description = "Sitzung, Aufnahme und Auswertung werden ganz gelöscht – auch eine Auswertung, die noch wartet. Dein Lernprofil bleibt, wie es ist.",
            confirmText = "Löschen",
            onConfirm = {
                confirm = false
                vm.deleteSessions(picked)
                leave()
            },
            onDismiss = { confirm = false },
            confirmColor = red,
        )
    }
    DetailScaffold(
        title = if (selecting) "${picked.size} ausgewählt" else "Alle Sitzungen",
        onBack = {
            vm.tap()
            if (selecting) leave() else vm.nav.pop()
        },
        backIcon = if (selecting) R.drawable.close_24dp else R.drawable.arrow_back_24dp,
        tag = "sessions",
        actions = {
            if (sessions.isNotEmpty()) {
                if (selecting) {
                    TopBarAction(R.drawable.delete_24dp, "Ausgewählte löschen", { if (picked.isNotEmpty()) confirm = true }, tint = red, tag = "sessions_delete")
                } else {
                    TopBarAction(R.drawable.checklist_24dp, "Auswählen", {
                        vm.tap()
                        selecting = true
                    }, tag = "sessions_select")
                }
            }
        },
    ) {
        if (sessions.isEmpty()) {
            BodyText("Noch keine Sitzungen.")
            return@DetailScaffold
        }
        if (selecting) {
            val open = sessions.filter { it.debrief == null }.map { it.id }.toSet()
            AbiCard(modifier = Modifier.testTag("sessions_selection")) {
                CardTitle("Sitzungen auswählen", icon = R.drawable.checklist_24dp)
                CaptionText("Tippe die Sitzungen an, die weg sollen. Sie werden ganz gelöscht – samt Aufnahme und einer Auswertung, die noch wartet.")
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Alle", R.drawable.check_24dp, accent, {
                        vm.tap()
                        chosen = sessions.map { it.id }.toSet()
                    }, Modifier.weight(1f), container = MaterialTheme.colorScheme.background)
                    ActionButton("Keine", R.drawable.close_24dp, accent, {
                        vm.tap()
                        chosen = emptySet()
                    }, Modifier.weight(1f), container = MaterialTheme.colorScheme.background)
                }
                if (open.isNotEmpty()) {
                    Gap(8.dp)
                    ActionButton(
                        "Nicht ausgewertete (${open.size})",
                        R.drawable.hourglass_bottom_24dp,
                        yellow,
                        {
                            vm.tap()
                            chosen = open
                        },
                        Modifier.testTag("sessions_select_open"),
                        container = MaterialTheme.colorScheme.background,
                    )
                }
                Gap(8.dp)
                ActionButton(
                    if (picked.isEmpty()) "Löschen" else "Löschen (${picked.size})",
                    R.drawable.delete_24dp,
                    red,
                    {
                        vm.tap()
                        confirm = true
                    },
                    Modifier.testTag("sessions_delete_button"),
                    enabled = picked.isNotEmpty(),
                    container = MaterialTheme.colorScheme.background,
                )
            }
        }
        val byKind = sessions.groupBy { it.kind }
        AbiCard {
            CardTitle("${sessions.size} Sitzungen", icon = R.drawable.history_24dp)
            CaptionText(byKind.entries.joinToString(" · ") { "${it.key.short}: ${it.value.size}" })
            if (!selecting) CaptionText("Zum Löschen: oben „Auswählen“ tippen oder eine Sitzung lange drücken.")
            Gap(8.dp)
            sessions.forEach { s ->
                SessionRow(
                    s,
                    selected = if (selecting) s.id in picked else null,
                    onLongClick = {
                        vm.tap()
                        selecting = true
                        toggle(s.id)
                    },
                ) {
                    if (selecting) {
                        vm.tap()
                        toggle(s.id)
                    } else {
                        vm.openSession(s.id)
                    }
                }
            }
        }
    }
}

/**
 * How the student got better over time: the points of every graded session with a trend line, content and language
 * apart, or one competence at a time.
 */
@Composable
fun DevelopmentCard(vm: AppViewModel, sessions: List<SessionRecord>, startView: Int = 0) {
    val graded = remember(sessions) { sessions.filter { it.debrief != null }.sortedBy { it.startedAt }.takeLast(30) }
    var view by androidx.compose.runtime.saveable.rememberSaveable(startView) { androidx.compose.runtime.mutableIntStateOf(startView) }
    val measured = remember(graded) { Skill.entries.filter { s -> graded.count { it.debrief?.skills?.get(s) != null } >= 2 } }
    var skillKey by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    AbiCard(modifier = Modifier.testTag("stats_development")) {
        CardTitle("Deine Entwicklung", icon = R.drawable.trending_up_24dp, iconTint = green)
        if (graded.size < 2) {
            CaptionText(
                if (graded.isEmpty()) "Nach deiner zweiten ausgewerteten Übung siehst du hier im Graphen, wie du über die Zeit besser wirst."
                else "Eine Auswertung ist da (${graded.first().debrief?.totalPoints} P.). Nach der nächsten zeigt der Graph deine Entwicklung.",
            )
            return@AbiCard
        }
        Segmented(
            options = listOf("Gesamt", "Inhalt & Sprache", "Kompetenz"),
            selected = view,
            onSelect = { vm.tap(); view = it },
            tags = listOf("dev_total", "dev_areas", "dev_skill"),
        )
        Gap(10.dp)
        val labels = graded.mapIndexed { i, r -> if (i == 0 || i == graded.lastIndex || graded.size <= 6 || i % ((graded.size / 4).coerceAtLeast(2)) == 0) dayLabel(r.startedAt) else "" }
        when (view) {
            1 -> {
                MultiLineChart(
                    series =
                        listOf(
                            ChartSeries(graded.map { it.debrief?.inhaltPoints?.toFloat() }, accent, "Inhalt"),
                            ChartSeries(graded.map { it.debrief?.darstellungPoints?.toFloat() }, yellow, "Sprache"),
                        ),
                    labels = labels,
                    guides = listOf(ChartGuide(13f, "13", green)),
                    maxY = 15f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendItem(accent, "Inhalt (40 %)")
                    LegendItem(yellow, "Darstellung/Sprache (60 %)")
                }
            }
            2 -> {
                if (measured.isEmpty()) {
                    CaptionText("Sobald eine Kompetenz in zwei Auswertungen gemessen wurde, erscheint sie hier.")
                } else {
                    val skill = measured.firstOrNull { it.key == skillKey } ?: measured.first()
                    de.abicoach.ui.components.ChoiceChips(measured, skill, { it.label }, { vm.tap(); skillKey = it.key }, tagPrefix = "dev_skill")
                    Gap(8.dp)
                    MultiLineChart(
                        series = listOf(ChartSeries(graded.map { it.debrief?.skills?.get(skill)?.toFloat() }, accent, skill.label)),
                        labels = labels,
                        guides = listOf(ChartGuide(13f, "13", green)),
                        maxY = 15f,
                    )
                    val values = graded.mapNotNull { it.debrief?.skills?.get(skill) }
                    CaptionText("${skill.label}: von ${values.first()} auf ${values.last()} Punkte" + deltaText(values.last() - values.first()))
                }
            }
            else -> {
                val totals = graded.map { it.debrief!!.totalPoints.toFloat() }
                val trend = totals.indices.map { i -> totals.subList(maxOf(0, i - 2), i + 1).average().toFloat() }
                MultiLineChart(
                    series = listOf(ChartSeries(totals, accent, "Punkte"), ChartSeries(trend, green, "Trend", dashed = true, dots = false)),
                    labels = labels,
                    guides = listOf(ChartGuide(13f, "13", green), ChartGuide(10f, "10", yellow)),
                    minY = maxOf(0f, (totals.minOrNull() ?: 0f) - 2f),
                    maxY = 15f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendItem(accent, "Punkte je Auswertung")
                    LegendItem(green, "Trend (Schnitt der letzten 3)")
                }
                val first = totals.take(3).average()
                val last = totals.takeLast(3).average()
                CaptionText(
                    "Deine ersten Übungen: Ø ${points(first)} P. · zuletzt: Ø ${points(last)} P." + deltaText((last - first).let { Math.round(it * 10) / 10.0 }),
                )
            }
        }
    }
}

private fun deltaText(delta: Number): String {
    val d = delta.toDouble()
    return when {
        d >= 0.5 -> " – du bist ${points(d)} P. besser geworden ↑"
        d <= -0.5 -> " – zuletzt ${points(-d)} P. schwächer ↓"
        else -> " – stabil"
    }
}
