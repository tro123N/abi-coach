package de.abicoach.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.learn.ActivityKind
import de.abicoach.learn.DayStep
import de.abicoach.learn.LearningBrain
import de.abicoach.learn.Phase
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.Planner
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.Banner
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.Pill
import de.abicoach.ui.components.ProgressLine
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.red

/**
 * Today's plan – the one place that says what to do. The app makes it once a day from everything it knows: first
 * what is due for repetition, then the mistake that costs the most, then the main exercise, then something short to
 * finish with. Every step is ticked off when it is done and shows what it brought, so it is visible that it counted.
 */
@Composable
fun DayPlanCard(vm: AppViewModel) {
    val app = vm.app
    val stored by app.daily.day.collectAsState()
    val sessions by app.store.sessions.collectAsState()
    val version by app.settings.version.collectAsState()
    val briefing by app.briefings.current.collectAsState()
    val date = remember(stored, sessions.size) { java.time.LocalDate.now().toString() }
    // The plan is there as soon as the page is shown – also after midnight, and after the placement.
    LaunchedEffect(date, sessions.size, version) {
        vm.ensureDay()
        vm.refreshBriefing(false)
    }
    val day = stored?.takeIf { it.date == date }
    val phase = remember(sessions.size, version) { LearningBrain.phase(app) }
    val setup = remember(version, sessions.size) { Planner.setup(app) }
    AbiCard(modifier = Modifier.testTag("today")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Dein Plan für heute",
                fontSize = AbiTheme.dimens.fonts.label * 0.9f,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f),
            )
            if (day != null && day.steps.isNotEmpty()) {
                Pill("${day.doneCount} von ${day.steps.size} erledigt", if (day.allDone) green else accent, Modifier.testTag("plan_progress"))
            }
            if (day != null && !day.allDone && phase != Phase.START) {
                IconButton(onClick = { vm.tap(); vm.ensureDay(force = true) }, modifier = Modifier.size(36.dp).testTag("plan_refresh")) {
                    Icon(painterResource(R.drawable.refresh_24dp), contentDescription = "Offene Schritte neu berechnen", tint = accent, modifier = Modifier.size(20.dp))
                }
            }
        }
        Gap(6.dp)
        if (day != null && day.steps.isNotEmpty()) {
            ProgressLine(day.doneCount.toFloat() / day.steps.size, color = if (day.allDone) green else accent)
            Gap(8.dp)
        }
        val b = briefing
        when {
            day?.allDone == true -> Unit
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
                    CaptionText("Der KI-Coach erklärt dir gleich, warum der Plan so aussieht …")
                }
            else ->
                CaptionText(
                    if (phase == Phase.START) {
                        "Erst die Einstufung – dann rechnet die App jeden Tag aus, was dir am meisten bringt."
                    } else {
                        "Jeden Tag neu berechnet aus deinem ganzen Lernstand. Mach die Schritte von oben nach unten – mehr musst du nicht überlegen."
                    },
                )
        }
        vm.briefingError?.let { CaptionText(it, color = red, modifier = Modifier.padding(top = 4.dp)) }
        // What has to be set up first (key, material, exam date).
        setup.forEachIndexed { i, r ->
            Gap(8.dp)
            ActionButton(r.title, r.icon, tintColor(r.tint), { vm.open(r.target) }, subtitle = r.subtitle, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("setup_$i"))
        }
        if (day != null) {
            val next = day.next
            day.steps.forEachIndexed { i, step ->
                Gap(8.dp)
                StepRow(i + 1, step, isNext = step.id == next?.id, onClick = { vm.openStep(step) }, tag = "rec_$i")
            }
            if (day.allDone) {
                Gap(10.dp)
                val streak = remember(day) { app.daily.finishedStreak(day.date) }
                Banner(
                    "Tagesplan geschafft!" + (if (streak > 1) " Das ist der $streak. Tag in Folge." else "") + " Alles, was du heute geübt hast, ist in deinen Lernstand eingegangen.",
                    green,
                    icon = R.drawable.task_alt_24dp,
                    modifier = Modifier.testTag("plan_done"),
                )
                Gap(8.dp)
                ActionButton("Noch etwas üben", R.drawable.add_24dp, accent, { vm.addBonusStep() }, subtitle = "Die App sucht den nächsten sinnvollen Schritt", container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("plan_more"))
            } else if (day.steps.isNotEmpty()) {
                Gap(8.dp)
                CaptionText("Noch etwa ${day.minutesLeft} Min." + if (phase != Phase.START) " · Phase ${phase.label}" else "")
            }
            // Practice outside the plan counts as well – and is shown, so it is visible that it counted.
            val extra = day.ledger.filter { !it.planned }.takeLast(3)
            if (extra.isNotEmpty()) {
                Gap(10.dp)
                CaptionText("Zusätzlich geübt – zählt auch:")
                extra.forEach { e -> CaptionText("✓ ${e.title}" + if (e.effect.isNotBlank()) " – ${e.effect}" else "", color = green, modifier = Modifier.testTag("ledger_extra")) }
            }
        }
    }
}

/** One step of the plan: number or tick, what it is for, what to do and why – and, once done, what it brought. */
@Composable
private fun StepRow(number: Int, step: DayStep, isNext: Boolean, onClick: () -> Unit, tag: String) {
    val dimens = AbiTheme.dimens
    val shape = RoundedCornerShape(20.dp)
    val tile = MaterialTheme.colorScheme.background
    val fill by animateColorAsState(if (step.done) tile.copy(alpha = tile.alpha * 0.45f) else tile, tween(400), label = "stepFill")
    val edge by animateColorAsState(if (isNext) accent else accent.copy(alpha = 0f), tween(400), label = "stepEdge")
    val bubble by animateColorAsState(if (step.done) green.copy(alpha = 0.18f) else accent.copy(alpha = if (isNext) 1f else 0.14f), tween(400), label = "stepBubble")
    val icon = runCatching { ActivityKind.valueOf(step.kind).icon }.getOrDefault(R.drawable.target_24dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(fill)
                .border(2.dp, edge, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag(tag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(bubble),
        ) {
            // The number pops over into the tick when the step is done.
            AnimatedContent(
                targetState = step.done,
                transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f, stiffness = 380f), initialScale = 0.3f) + fadeIn(tween(160))) togetherWith (scaleOut(tween(140)) + fadeOut(tween(120))) },
                label = "stepTick",
            ) { done ->
                if (done) {
                    Icon(painterResource(R.drawable.check_24dp), contentDescription = "erledigt", tint = green, modifier = Modifier.size(20.dp))
                } else {
                    Text("$number", fontSize = dimens.fonts.body, fontWeight = FontWeight.Bold, color = if (isNext) androidx.compose.ui.graphics.Color.White else accent)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                step.role.label.uppercase() + (if (isNext) " · JETZT DRAN" else "") + (if (step.carried && !step.done) " · VON GESTERN" else ""),
                fontSize = dimens.fonts.caption * 0.85f,
                fontWeight = FontWeight.Bold,
                color = if (step.done) green else accent,
            )
            Text(
                step.title,
                fontSize = dimens.fonts.body,
                lineHeight = dimens.fonts.body * 1.25f,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (step.done) 0.6f else 1f),
            )
            if (step.done) {
                CaptionText(if (step.effect.isNotBlank()) "Zählt: ${step.effect}" else "Erledigt – ist in deinen Lernstand eingegangen", color = green, modifier = Modifier.testTag("${tag}_effect"))
            } else {
                CaptionText("${step.reason} · ca. ${step.minutes} Min.")
            }
        }
        if (!step.done) {
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(icon), contentDescription = null, tint = accent.copy(alpha = if (isNext) 1f else 0.5f), modifier = Modifier.size(22.dp))
        }
    }
}
