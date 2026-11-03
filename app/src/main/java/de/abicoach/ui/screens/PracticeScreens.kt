package de.abicoach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.data.Debrief
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionRecord
import de.abicoach.practice.CardSource
import de.abicoach.practice.Grade
import de.abicoach.practice.Scheduler
import de.abicoach.practice.TalkMetrics
import de.abicoach.practice.VocabCard
import de.abicoach.session.CoachAction
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.Screen
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.AbiTextField
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.AreaIntro
import de.abicoach.ui.components.Banner
import de.abicoach.ui.components.BigNumber
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.BulletText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.ExpandableCard
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.GoalRow
import de.abicoach.ui.components.LoadingCard
import de.abicoach.ui.components.Pill
import de.abicoach.ui.components.StartButton
import de.abicoach.ui.components.StatTile
import de.abicoach.ui.components.Tab
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow
import kotlin.math.abs

// ---------- Vortrags-Coach (in the debrief) ----------

/** How the presentation was delivered: time per task against the targets, speed, fillers, pauses, reading aloud. */
@Composable
fun TalkCoachCard(record: SessionRecord, d: Debrief) {
    val m = remember(record.id, d) { TalkMetrics.compute(record, d) } ?: return
    val taskColors = listOf(accent, green, yellow)
    AbiCard(modifier = Modifier.testTag("debrief_talk")) {
        CardTitle("Vortrags-Coach", icon = R.drawable.speed_24dp)
        if (m.readingAloud) {
            Banner("Das klang abgelesen. In der Prüfung zählt freies Sprechen – schreib dir nur Stichworte auf, keine ganzen Sätze.", red, icon = R.drawable.warning_24dp)
            Gap(10.dp)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(mmss(m.seconds), "Dauer", Modifier.weight(1f).fillMaxHeight())
            StatTile("${m.wordsPerMinute}", "Wörter/Min", Modifier.weight(1f).fillMaxHeight(), color = if (m.wordsPerMinute in 100..170) green else yellow)
            StatTile("${m.fillers}", "Füller", Modifier.weight(1f).fillMaxHeight(), color = if (m.fillers * 60 <= m.seconds * 3) green else yellow)
            StatTile("${m.longPauses}", "Pausen", Modifier.weight(1f).fillMaxHeight(), color = if (m.longPauses == 0) green else yellow)
        }
        val targets = if (record.focusTask in 1..3) mapOf(record.focusTask to (TalkMetrics.TARGET[record.focusTask] ?: 180)) else TalkMetrics.TARGET
        if (record.focusTask == 0 && m.perTask.size >= 2) {
            Gap(14.dp)
            CaptionText("Deine Zeit pro Aufgabe")
            Gap(4.dp)
            SegmentBar(m.perTask.map { (n, s) -> s.toFloat() to taskColors[(n - 1).coerceIn(0, 2)] })
            Gap(6.dp)
            CaptionText("Empfohlen")
            Gap(4.dp)
            SegmentBar(TalkMetrics.TARGET.entries.map { (n, s) -> s.toFloat() to taskColors[n - 1].copy(alpha = 0.45f) })
            Gap(8.dp)
            m.perTask.sortedBy { it.first }.forEach { (n, s) ->
                val target = targets[n] ?: 180
                val ok = abs(s - target) <= target * 0.4
                GoalRow(ok, "Aufgabe $n: ${mmss(s)} (Ziel ca. ${mmss(target)})" + if (!ok) (if (s < target) " – ausführlicher" else " – knapper") else "")
            }
        } else if (record.focusTask in 1..3) {
            Gap(10.dp)
            val target = targets[record.focusTask] ?: 180
            val ok = abs(m.seconds - target) <= target * 0.4
            GoalRow(ok, "Länge: ${mmss(m.seconds)} (Ziel ca. ${mmss(target)})")
        }
        Gap(8.dp)
        when {
            m.wordsPerMinute in 1..99 -> BulletText("Etwas langsam. Übe die Übergänge (\"Moving on to the second task …\"), damit du ohne Stocken weiterkommst.")
            m.wordsPerMinute > 170 -> BulletText("Sehr schnell. Mach nach jedem Hauptgedanken eine kurze Pause – das wirkt souverän.")
            else -> BulletText("Gutes Tempo – so kann die Prüferin dir gut folgen.", bulletColor = green)
        }
        if (m.fillers * 60 > m.seconds * 3) BulletText("Viele Füllwörter (um, er). Ersetze sie durch kurze Pausen oder \"Let me put it this way …\".")
        if (m.longPauses > 0) BulletText("${m.longPauses}× über 12 Sekunden still. Halte dich an deine Stichworte und sprich weiter, auch wenn ein Wort fehlt.")
    }
}

@Composable
private fun SegmentBar(parts: List<Pair<Float, Color>>) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.background)) {
        parts.forEachIndexed { i, (value, color) ->
            if (value > 0f) {
                Box(Modifier.weight(value / total).fillMaxHeight().background(color))
                if (i < parts.lastIndex) Spacer(Modifier.width(2.dp))
            }
        }
    }
}

// ---------- Wortschatz ----------

private fun promptFor(source: CardSource): String =
    when (source) {
        CardSource.ERROR -> "Wie sagt man das richtig?"
        CardSource.UPGRADE -> "Sag es auf 15-Punkte-Niveau:"
        CardSource.PHRASE, CardSource.COURSE -> "Wie heißt das auf Englisch?"
    }

@Composable
private fun sourceColor(source: CardSource): Color =
    when (source) {
        CardSource.ERROR -> red
        CardSource.UPGRADE -> green
        CardSource.COURSE -> yellow
        CardSource.PHRASE -> accent
    }

/** Flashcards with spaced repetition: phrases, better sentences and own errors from the debriefs, words from the course. */
@Composable
fun VocabScreen(vm: AppViewModel) {
    val cards by vm.app.vocab.cards.collectAsState()
    val card = vm.vocabQueue.firstOrNull()
    DetailScaffold(title = "Wortschatz", onBack = { vm.tap(); vm.stopSpeaking(); vm.nav.pop() }, tag = "vocab") {
        val secure = cards.count { it.intervalDays >= 7 }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${cards.size}", "Karten", Modifier.weight(1f).fillMaxHeight(), icon = R.drawable.note_stack_24dp)
            StatTile("${vm.vocabQueue.size}", "Noch heute", Modifier.weight(1f).fillMaxHeight(), color = if (vm.vocabQueue.isEmpty()) green else yellow, icon = R.drawable.timer_24dp)
            StatTile("$secure", "Sicher", Modifier.weight(1f).fillMaxHeight(), color = green, icon = R.drawable.verified_24dp)
        }
        if (card == null) {
            AbiCard {
                if (cards.isEmpty()) {
                    CardTitle("Noch keine Karten", icon = R.drawable.note_stack_24dp, iconTint = yellow)
                    BodyText("Nach jeder Auswertung landen deine Redemittel, die 15-Punkte-Sätze und deine größten Fehler hier – und aus deinem Kursmaterial der wichtigste Wortschatz.")
                } else {
                    CardTitle(if (vm.vocabDone > 0) "Geschafft – ${vm.vocabDone} Karten wiederholt" else "Alles wiederholt", icon = R.drawable.task_alt_24dp, iconTint = green)
                    BodyText("Für heute ist nichts mehr fällig. Die Karten kommen wieder, kurz bevor du sie vergessen würdest (nach 1, 3, 7 … Tagen).")
                }
                Gap(10.dp)
                ActionButton("Gesprächs-Sprint", R.drawable.speed_24dp, accent, { vm.runAction(CoachAction("sprint")) }, subtitle = "Wörter gleich im Gespräch benutzen", container = MaterialTheme.colorScheme.background)
            }
        } else {
            VocabCardView(vm, card)
        }
        if (cards.isNotEmpty()) {
            ExpandableCard("Alle Karten", subtitle = "${cards.size} Karten · neueste zuerst", icon = R.drawable.format_list_bulleted_24dp) {
                cards.sortedByDescending { it.addedAt }.take(60).forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Column(Modifier.weight(1f)) {
                            BodyText(c.back, color = sourceColor(c.source))
                            CaptionText(c.front)
                        }
                        CaptionText(if (c.isNew) "neu" else if (c.intervalDays >= 7) "sicher" else "lernt")
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabCardView(vm: AppViewModel, card: VocabCard) {
    val dimens = AbiTheme.dimens
    AbiCard(modifier = Modifier.testTag("vocab_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(card.source.label, sourceColor(card.source))
            if (card.topic >= 0) {
                Spacer(Modifier.width(6.dp))
                Pill(Knowledge.topicShort(card.topic), MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
            }
            Spacer(Modifier.weight(1f))
            CaptionText(if (card.isNew) "neu" else "Wiederholung")
        }
        Gap(14.dp)
        CaptionText(promptFor(card.source))
        Gap(4.dp)
        Text(card.front, fontSize = dimens.fonts.label * 1.15f, lineHeight = dimens.fonts.label * 1.45f, color = MaterialTheme.colorScheme.onPrimary)
        Gap(16.dp)
        if (!vm.vocabRevealed) {
            ActionButton("Antwort zeigen", R.drawable.visibility_24dp, accent, { vm.tap(); vm.vocabRevealed = true }, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("vocab_reveal"))
        } else {
            Row(verticalAlignment = Alignment.Top) {
                Text(card.back, fontSize = dimens.fonts.label * 1.15f, lineHeight = dimens.fonts.label * 1.45f, color = green, modifier = Modifier.weight(1f))
                SpeakButton(vm, card.back)
            }
            if (card.example.isNotBlank()) {
                Gap(4.dp)
                Text(card.example, fontSize = dimens.fonts.caption, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            }
            Gap(8.dp)
            CaptionText("Sag die Antwort einmal laut – dann bewerte dich ehrlich:")
            Gap(8.dp)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Grade.entries.forEach { g ->
                    val color =
                        when (g) {
                            Grade.AGAIN -> red
                            Grade.HARD -> yellow
                            Grade.GOOD -> green
                            Grade.EASY -> accent
                        }
                    Button(
                        onClick = { vm.gradeCard(g) },
                        shape = RoundedCornerShape(dimens.components.cornerRadius),
                        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.18f), contentColor = MaterialTheme.colorScheme.onPrimary),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("grade_${g.name.lowercase()}"),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(g.label, fontSize = dimens.fonts.caption, color = color, maxLines = 1, textAlign = TextAlign.Center)
                            Text(Scheduler.preview(card, g), fontSize = dimens.fonts.caption * 0.85f, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), maxLines = 1)
                        }
                    }
                }
            }
        }
        Gap(10.dp)
        Text(
            "Karte löschen",
            fontSize = dimens.fonts.caption,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.tap(); vm.deleteCard(card.id) }.padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}

// ---------- Aussprache-Labor ----------

/** Words Germans often stumble over in oral exams. */
private val STUMBLING_WORDS =
    listOf(
        "thought", "throughout", "although", "the theme", "comfortable", "development", "determine", "particularly",
        "hierarchy", "statistics", "colonialism", "vegetable", "Wednesday", "analyse", "focus", "society", "debt", "island",
        "environment", "would", "world", "squirrel", "issue", "the author's attitude",
    )

/** Listen, say it, get a score: one word or sentence at a time, rated by Gemini. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PronunciationScreen(vm: AppViewModel) {
    val scores by vm.app.pron.scores.collectAsState()
    val own = remember { vm.pronWords() }
    val record = rememberMicGate { vm.pronStart() }
    DetailScaffold(title = "Aussprache-Labor", onBack = { vm.tap(); vm.pronStop(); vm.stopSpeaking(); vm.nav.pop() }, tag = "pron") {
        AreaIntro(R.drawable.graphic_eq_24dp, "Hören, nachsprechen, besser werden", "Hör dir das Wort an, sprich es nach – Gemini hört genau hin und sagt dir, welcher Laut noch nicht stimmt.")
        MessageBanners(vm)
        AbiCard {
            CardTitle("Wort oder Satz", icon = R.drawable.record_voice_over_24dp)
            AbiTextField(vm.pronTarget, { vm.pronTarget = it.take(160) }, "Was möchtest du üben?", placeholder = "z. B. thoroughly", tag = "pron_input")
            Gap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (vm.pronRecording) {
                        ActionButton("Fertig", R.drawable.stop_24dp, red, { vm.pronStop() }, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("pron_stop"))
                    } else {
                        ActionButton("Aufnehmen", R.drawable.mic_24dp, green, record, enabled = vm.pronTarget.isNotBlank() && !vm.pronBusy, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("pron_record"))
                    }
                }
                if (vm.pronTarget.isNotBlank()) SpeakButton(vm, vm.pronTarget)
            }
            if (vm.pronRecording) {
                Gap(6.dp)
                CaptionText("Ich höre zu … sag es einmal deutlich und tippe dann auf „Fertig“.")
            }
        }
        if (vm.pronBusy) LoadingCard("Gemini hört sich deine Aufnahme an …")
        vm.pronResult?.let { r ->
            val color = if (r.score >= 85) green else if (r.score >= 65) yellow else red
            AbiCard(modifier = Modifier.testTag("pron_result")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BigNumber("${r.score}", "von 100", color)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                r.score >= 85 -> "Klingt sicher!"
                                r.score >= 65 -> "Gut verständlich"
                                else -> "Noch einmal üben"
                            },
                            fontSize = AbiTheme.dimens.fonts.label,
                            color = color,
                        )
                        if (r.heard.isNotBlank()) CaptionText("Verstanden: „${r.heard}“")
                        scores[vm.pronTarget.lowercase().trim()]?.let { CaptionText("Dein bester Wert: $it") }
                    }
                }
                if (r.issues.isNotEmpty()) {
                    Gap(10.dp)
                    r.issues.forEach { BulletText(it, bulletColor = yellow) }
                }
                if (r.tip.isNotBlank()) {
                    Gap(8.dp)
                    BodyText(r.tip)
                }
                CaptionText(r.by)
            }
        }
        if (own.isNotEmpty()) {
            AbiCard {
                CardTitle("Aus deinen Auswertungen", icon = R.drawable.history_edu_24dp)
                own.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.pronSelect(p.word) }.padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.word, fontSize = AbiTheme.dimens.fonts.label, color = MaterialTheme.colorScheme.onPrimary)
                            CaptionText(p.issue)
                        }
                        val best = scores[p.word.lowercase().trim()]
                        if (best != null) Pill("$best", if (best >= 85) green else if (best >= 65) yellow else red)
                    }
                }
            }
        }
        AbiCard {
            CardTitle("Typische Stolperwörter", icon = R.drawable.lightbulb_24dp, iconTint = yellow)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                STUMBLING_WORDS.forEach { w ->
                    val best = scores[w.lowercase()]
                    val color = when {
                        best == null -> accent
                        best >= 85 -> green
                        best >= 65 -> yellow
                        else -> red
                    }
                    Text(
                        if (best == null) w else "$w · $best",
                        fontSize = AbiTheme.dimens.fonts.caption,
                        color = color,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)).clickable { vm.pronSelect(w) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// ---------- Prüferin-Profil (statistics) ----------

/** What the student's teacher values and criticises, learned from the Klausuren. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExaminerProfileCard(vm: AppViewModel) {
    val profile by vm.app.profiles.profile.collectAsState()
    AbiCard(modifier = Modifier.testTag("stats_examiner")) {
        CardTitle("So bewertet deine Lehrkraft", icon = R.drawable.school_24dp)
        if (profile.teacherNotes.isEmpty() && profile.klausurErrors.isEmpty()) {
            BodyText("Lade deine korrigierten Klausuren hoch – dann lernt die App, worauf deine Lehrkraft achtet, und die KI-Prüferin fragt und bewertet genauso.")
            Gap(10.dp)
            ActionButton("Klausuren hochladen", R.drawable.cloud_upload_24dp, yellow, { vm.tap(); vm.nav.openTab(Tab.KURS) }, container = MaterialTheme.colorScheme.background)
            return@AbiCard
        }
        if (profile.teacherNotes.isNotEmpty()) {
            CaptionText("Aus ihren Kommentaren")
            profile.teacherNotes.take(6).forEach { BulletText(it) }
        }
        val errors = profile.klausurErrors.entries.sortedByDescending { it.value }.take(4)
        if (errors.isNotEmpty()) {
            Gap(8.dp)
            CaptionText("Das hat sie in deinen Klausuren am häufigsten angestrichen")
            Gap(4.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                errors.forEach { (k, v) -> Pill("${Knowledge.categoryLabel(k)} · $v×", red) }
            }
        }
        Gap(8.dp)
        CaptionText("Die KI-Prüferin achtet in jeder Übung genau darauf.")
    }
}

// ---------- coach chat buttons ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachActionButtons(vm: AppViewModel, actions: List<CoachAction>) {
    if (actions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
        actions.forEach { a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .clickable { vm.runAction(a) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("coach_action_${a.kind}"),
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.play_arrow_24dp),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(end = 6.dp).height(18.dp).width(18.dp),
                )
                Text(a.label, fontSize = AbiTheme.dimens.fonts.caption, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

// ---------- AI daily plan (Abi-Weg) ----------

private fun actionIcon(kind: String): Int =
    when (kind) {
        "aufgabe1" -> R.drawable.summarize_24dp
        "aufgabe2" -> R.drawable.insights_24dp
        "aufgabe3" -> R.drawable.rate_review_24dp
        "vortrag" -> R.drawable.co_present_24dp
        "gespraech" -> R.drawable.forum_24dp
        "sprint" -> R.drawable.speed_24dp
        "simulation" -> R.drawable.workspace_premium_24dp
        "wortschatz" -> R.drawable.note_stack_24dp
        "aussprache" -> R.drawable.graphic_eq_24dp
        else -> R.drawable.route_24dp
    }

/** The plan the AI coach made for today from the student's profile, last sessions, errors and exam date. */
@Composable
fun BriefingCard(vm: AppViewModel) {
    val briefing by vm.app.briefings.current.collectAsState()
    val sessions by vm.app.store.sessions.collectAsState()
    androidx.compose.runtime.LaunchedEffect(sessions.size, vm.app.settings.geminiKey) { vm.refreshBriefing(false) }
    val b = briefing
    if (b == null && !vm.briefingBusy && vm.briefingError == null) return
    AbiCard(modifier = Modifier.testTag("briefing")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("Dein Tagesplan vom KI-Coach", icon = R.drawable.psychology_24dp, modifier = Modifier.weight(1f))
            if (!vm.briefingBusy && b != null) {
                androidx.compose.material3.IconButton(onClick = { vm.tap(); vm.refreshBriefing(true) }) {
                    androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.refresh_24dp), contentDescription = "Neu planen", tint = accent)
                }
            }
        }
        if (vm.briefingBusy && b == null) {
            LoadingCard("Der Coach plant deinen Tag …", detail = "Er schaut auf deine Kompetenzen, Fehler und den Prüfungstermin.")
        }
        vm.briefingError?.let { Banner(it, red, icon = R.drawable.error_24dp) }
        if (b != null) {
            Text(b.headline, fontSize = AbiTheme.dimens.fonts.label * 1.05f, lineHeight = AbiTheme.dimens.fonts.label * 1.3f, color = MaterialTheme.colorScheme.onPrimary)
            Gap(4.dp)
            BodyText(b.focus)
            Gap(10.dp)
            b.steps.forEachIndexed { i, step ->
                val action = step.parsed ?: return@forEachIndexed
                if (i > 0) Gap(8.dp)
                ActionButton(
                    "${i + 1}. ${step.title}",
                    actionIcon(action.kind),
                    accent,
                    { vm.runAction(action) },
                    subtitle = step.why,
                    container = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("briefing_step_${i + 1}"),
                )
            }
            Gap(6.dp)
            CaptionText("Geplant von ${b.by} · erneuert sich täglich und nach jeder Auswertung")
        }
    }
}
