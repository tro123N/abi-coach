package de.abicoach.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import de.abicoach.R
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
import de.abicoach.ui.components.AreaIntro
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
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.pointsColor
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow

/** Forecast, competences, practice time, errors and topic fields. */
@Composable
fun StatsScreen(vm: AppViewModel) {
    val app = vm.app
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
    val topicPractice = remember(sessions) { app.store.topicCounts() }
    val coverage = remember(docs) { app.memory.stats().chunksPerTopic }

    TabScaffold(vm, Tab.STATS, "Statistik") {
        item { AreaIntro(R.drawable.bar_chart_24dp, "Dein Lernprofil", "Nach dem Bewertungsraster der mündlichen Prüfung: Inhalt 40 %, Darstellungsleistung 60 %.") }
        item {
            AbiCard(modifier = Modifier.testTag("stats_prognosis")) {
                CardTitle("Punktprognose", icon = R.drawable.trending_up_24dp)
                if (prognosis == null) {
                    BodyText("Noch keine Daten. Mach die Einstufung oder eine Übung – danach erscheint hier deine Prognose.")
                    Gap(8.dp)
                    ActionButton("Zur Einstufung", R.drawable.flag_24dp, yellow, { vm.tap(); vm.nav.push(Screen.PlacementIntro) }, container = MaterialTheme.colorScheme.background)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BigNumber(prognosis.points.toString(), "Punkte", pointsColor(prognosis.total))
                        Spacer(Modifier.width(16.dp))
                        Column2 {
                            Text(Planner.gradeLabel(prognosis.points), fontSize = dimens.fonts.label, color = pointsColor(prognosis.total))
                            CaptionText("Spanne ${prognosis.lowPoints}–${prognosis.highPoints} · Sicherheit ${(prognosis.confidence * 100).toInt()} %")
                        }
                    }
                    if (profile.history.size >= 2) {
                        Gap(12.dp)
                        LineChart(
                            values = profile.history.takeLast(30).map { it.total.toFloat() },
                            color = accent,
                            guides = listOf(ChartGuide(13f, "13", green), ChartGuide(10f, "10", yellow)),
                            minY = 0f,
                            maxY = 15f,
                            valueText = { points(it.toDouble()) },
                        )
                        CaptionText("Verlauf der Prognose nach jeder Auswertung")
                    }
                }
            }
        }
        if (prognosis != null) {
            item {
                AbiCard(modifier = Modifier.testTag("stats_skills")) {
                    CardTitle("Kompetenzen", icon = R.drawable.insights_24dp)
                    Area.entries.forEach { area ->
                        Text("${area.label} (${(area.weight * 100).toInt()} %)", fontSize = dimens.fonts.body, color = accent)
                        Gap(4.dp)
                        Skill.entries.filter { it.area == area }.forEach { s ->
                            val state = profile.skills[s]
                            if (state == null) {
                                CaptionText("${s.label}: noch nicht gemessen")
                            } else {
                                BarRow(s.label, (state.score / 15).toFloat(), points(state.score) + " P.", pointsColor(state.score))
                            }
                            Gap(6.dp)
                        }
                        Gap(6.dp)
                    }
                    val levers = profile.levers(3)
                    if (levers.isNotEmpty()) CaptionText("Größte Hebel: " + levers.joinToString(", ") { it.label })
                }
            }
        }
        item {
            AbiCard {
                CardTitle("Übungszeit", icon = R.drawable.timer_24dp)
                val total = sessions.sumOf { it.durationSec } / 60
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(sessions.size.toString(), "Sitzungen", Modifier.weight(1f), color = accent)
                    StatTile("$total", "Minuten gesamt", Modifier.weight(1f), color = accent)
                    StatTile(app.store.streak().toString(), "Tage in Folge", Modifier.weight(1f), icon = R.drawable.whatshot_24dp, color = red)
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
        item {
            AbiCard {
                CardTitle("Themenfelder", icon = R.drawable.hub_24dp)
                CaptionText("Geübt (Sitzungen) und Material (Abschnitte) je Themenfeld")
                Gap(8.dp)
                val maxPractice = (topicPractice.values.maxOrNull() ?: 1).coerceAtLeast(1)
                Knowledge.TOPICS.forEach { t ->
                    val n = topicPractice[t.id] ?: 0
                    val m = coverage.getOrElse(t.id) { 0 }
                    BarRow(t.short, n.toFloat() / maxPractice, "$n · $m", if (n == 0 && m > 0) yellow else accent)
                    Gap(5.dp)
                }
                CaptionText("Gelb: Material da, aber noch nicht geübt.")
            }
        }
        val recent = sessions.take(5)
        if (recent.isNotEmpty()) {
            item {
                AbiCard {
                    CardTitle("Letzte Sitzungen", icon = R.drawable.history_24dp)
                    recent.forEach { s -> SessionRow(s) { vm.tap(); if (s.debrief != null) vm.nav.push(Screen.Debrief(s.id)) else vm.openAnalysis(s.id) } }
                    Gap(6.dp)
                    ActionButton("Alle Sitzungen", R.drawable.history_24dp, MaterialTheme.colorScheme.onPrimary, { vm.tap(); vm.nav.push(Screen.Sessions) }, container = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
}

@Composable
private fun Column2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column { content() }
}

/** All sessions, newest first. */
@Composable
fun SessionsScreen(vm: AppViewModel) {
    val sessions by vm.app.store.sessions.collectAsState()
    DetailScaffold(title = "Alle Sitzungen", onBack = { vm.tap(); vm.nav.pop() }, tag = "sessions") {
        if (sessions.isEmpty()) {
            BodyText("Noch keine Sitzungen.")
        } else {
            val byKind = sessions.groupBy { it.kind }
            AbiCard {
                CardTitle("${sessions.size} Sitzungen", icon = R.drawable.history_24dp)
                CaptionText(byKind.entries.joinToString(" · ") { "${it.key.short}: ${it.value.size}" })
                Gap(8.dp)
                sessions.forEach { s -> SessionRow(s) { vm.tap(); if (s.debrief != null) vm.nav.push(Screen.Debrief(s.id)) else vm.openAnalysis(s.id) } }
            }
        }
    }
}
