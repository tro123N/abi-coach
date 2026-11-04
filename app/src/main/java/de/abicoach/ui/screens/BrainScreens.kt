package de.abicoach.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import de.abicoach.data.Debrief
import de.abicoach.data.SessionGoal
import de.abicoach.data.SessionRecord
import de.abicoach.learn.LearningBrain
import de.abicoach.learn.Phase
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.Planner
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.BulletText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.GoalRow
import de.abicoach.ui.components.Pill
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.pointsColor
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow

/** The goals the learning brain set for this session (preparation page and live page). */
@Composable
fun GoalsCard(goals: List<SessionGoal>, modifier: Modifier = Modifier) {
    if (goals.isEmpty()) return
    AbiCard(modifier = modifier.testTag("session_goals"), border = accent.copy(alpha = 0.35f)) {
        CardTitle("Deine Ziele für diese Übung", icon = R.drawable.target_24dp, iconTint = accent)
        goals.forEach { g -> BulletText(g.text) }
        Gap(4.dp)
        CaptionText("Aus deinem Lernstand gewählt – die Auswertung sagt dir danach, ob du sie erreicht hast.")
    }
}

/** How the goals of the session were reached. */
@Composable
fun GoalResultsCard(record: SessionRecord, d: Debrief) {
    if (record.goals.isEmpty() || d.goalResults.isEmpty()) return
    val reached = d.goalResults.count { it.reached }
    AbiCard(modifier = Modifier.testTag("debrief_goals")) {
        CardTitle("Deine Ziele: $reached von ${record.goals.size} erreicht", icon = R.drawable.target_24dp, iconTint = if (reached == record.goals.size) green else yellow)
        record.goals.forEach { g ->
            val r = d.goalResults.firstOrNull { it.id == g.id }
            GoalRow(ok = r?.result == "yes", text = g.text, neutral = r == null || r.result == "partly")
            if (r != null) {
                val label =
                    when (r.result) {
                        "yes" -> "erreicht"
                        "partly" -> "teilweise"
                        else -> "noch nicht"
                    }
                CaptionText("$label – ${r.note}".trim().trimEnd('–', ' '), color = if (r.result == "yes") green else if (r.result == "partly") yellow else red, modifier = Modifier.padding28())
            }
        }
    }
}

/** Indents a caption under a [GoalRow], in line with its text. */
private fun Modifier.padding28(): Modifier = this.padding(start = 28.dp)

/** What the grader noticed about the student's habits in this session (it goes into the learner memory). */
@Composable
fun ObservationsCard(d: Debrief) {
    if (d.observations.isEmpty()) return
    AbiCard(modifier = Modifier.testTag("debrief_observations")) {
        CardTitle("Das ist dem Coach aufgefallen", icon = R.drawable.psychology_24dp)
        d.observations.forEach { o -> GoalRow(ok = o.strength, text = o.text) }
        Gap(4.dp)
        CaptionText("Diese Beobachtungen kommen in dein Lerner-Gedächtnis: Wiederholt sich etwas, setzt die App dir ein Ziel dagegen und der Prüfer achtet darauf.")
    }
}

/** The single best next step, computed by the learning brain from the updated profile. */
@Composable
fun NextStepCard(vm: AppViewModel) {
    val app = vm.app
    val profile by app.profiles.profile.collectAsState()
    val patterns by app.errorMemory.patterns.collectAsState()
    val step = remember(profile, patterns) { runCatching { LearningBrain.next(app) }.getOrNull() } ?: return
    val r = Planner.recommendation(app, step)
    AbiCard(modifier = Modifier.testTag("debrief_next")) {
        CardTitle("Nächster bester Schritt", icon = R.drawable.route_24dp, iconTint = green)
        CaptionText("Berechnet aus deinem neuen Lernstand: Das bringt dir jetzt pro Minute die meisten Punkte.")
        Gap(6.dp)
        ActionButton(r.title, r.icon, tintColor(r.tint), { vm.stopSpeaking(); vm.open(r.target) }, subtitle = r.subtitle, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("debrief_next_button"))
    }
}

/** The independent second grading, shown under the points. */
@Composable
fun SecondOpinionLine(d: Debrief) {
    val s = d.secondOpinion ?: return
    Gap(10.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pill("Zweitbewertung", if (s.agrees) green else yellow)
        Spacer(Modifier.width(8.dp))
        Text(
            "${s.firstTotal} P. und ${s.total} P. → ${d.totalPoints} P.",
            fontSize = AbiTheme.dimens.fonts.body,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
    CaptionText(
        if (s.agrees) {
            "Zwei unabhängige Bewertungen, wie im Prüfungsausschuss – die Note ist ihr Mittelwert. Sie liegen nah beieinander, das Ergebnis ist verlässlich."
        } else {
            "Die beiden unabhängigen Bewertungen liegen auseinander – die Note ist ihr Mittelwert. Eine weitere Übung dieser Art macht dein Profil sicherer."
        },
    )
    if (s.reason.isNotBlank()) CaptionText("Zweite Prüferin: ${s.reason}")
}

/** The phase and the projection to the exam day, for the forecast cards. */
@Composable
fun ProjectionBlock(vm: AppViewModel, showPhase: Boolean = true) {
    val app = vm.app
    val profile by app.profiles.profile.collectAsState()
    val projection = remember(profile) { LearningBrain.projection(app) }
    val phase = remember(profile) { LearningBrain.phase(app) }
    if (projection == null && (phase == Phase.START || !showPhase)) return
    Gap(10.dp)
    if (projection != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("projection")) {
            Pill("Hochrechnung", pointsColor(projection.points))
            Spacer(Modifier.width(8.dp))
            Text(
                "Am Prüfungstag etwa ${"%.0f".format(projection.points)} Punkte",
                fontSize = AbiTheme.dimens.fonts.body,
                color = pointsColor(projection.points),
            )
        }
        val pace =
            when {
                projection.perWeek >= 0.1 -> "Dein Tempo: +${points(projection.perWeek)} P. pro Woche."
                projection.perWeek <= -0.1 -> "Zuletzt ging es ${points(-projection.perWeek)} P. pro Woche nach unten – gezielt gegensteuern."
                else -> "Dein Stand ist gerade stabil."
            }
        CaptionText("$pace Spanne ${"%.0f".format(projection.low)}–${"%.0f".format(projection.high)} Punkte in ${projection.days} Tagen, wenn du so weitermachst.")
    }
    if (showPhase && phase != Phase.START) CaptionText("Phase: ${phase.label} – ${phase.text}", color = accent)
}

/** What the app has learned about the student (statistics): habits, strengths and what he has overcome. */
@Composable
fun InsightsCard(vm: AppViewModel) {
    val all by vm.app.insights.all.collectAsState()
    if (all.isEmpty()) return
    val weak = all.filter { !it.strength && !it.solved }.take(5)
    val strong = all.filter { it.strength }.take(3)
    val solved = all.filter { it.solved }.take(3)
    AbiCard(modifier = Modifier.testTag("stats_insights")) {
        CardTitle("Das weiß die App über dich", icon = R.drawable.psychology_24dp, iconTint = accent)
        CaptionText("Gewohnheiten, die die KI in deinen Sitzungen beobachtet hat. Daraus setzt die App deine Ziele, und der Prüfer achtet darauf.")
        if (weak.isNotEmpty()) {
            Gap(8.dp)
            BodyText("Daran arbeitest du", color = yellow)
            weak.forEach { i -> InsightRow(i.text, "${i.areaLabel} · ${i.count}× beobachtet", ok = false) }
        }
        if (strong.isNotEmpty()) {
            Gap(8.dp)
            BodyText("Deine Stärken", color = green)
            strong.forEach { i -> InsightRow(i.text, "${i.areaLabel} · ${i.count}×", ok = true) }
        }
        if (solved.isNotEmpty()) {
            Gap(8.dp)
            BodyText("Überwunden", color = green)
            solved.forEach { i -> InsightRow(i.text, "zweimal das Ziel dagegen erreicht", ok = true) }
        }
    }
}

@Composable
private fun InsightRow(text: String, detail: String, ok: Boolean) {
    Column {
        GoalRow(ok = ok, text = text)
        CaptionText(detail, modifier = Modifier.padding28())
    }
}
