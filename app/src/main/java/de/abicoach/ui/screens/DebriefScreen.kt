package de.abicoach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.data.Debrief
import de.abicoach.data.Knowledge
import de.abicoach.data.Progress
import de.abicoach.data.SessionRecord
import de.abicoach.data.Speaker
import de.abicoach.profile.Skill
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.Planner
import de.abicoach.ui.Screen
import de.abicoach.ui.SessionSetup
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.BarRow
import de.abicoach.ui.components.BigNumber
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.BulletText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.ExpandableCard
import de.abicoach.ui.components.FoldGroup
import de.abicoach.ui.components.FoldRow
import de.abicoach.ui.components.HeroCard
import de.abicoach.ui.components.HeroChip
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.GoalRow
import de.abicoach.ui.components.LevelBadge
import de.abicoach.ui.components.PopUp
import de.abicoach.ui.components.ProgressLine
import de.abicoach.ui.components.StarRow
import de.abicoach.ui.components.Tab
import de.abicoach.ui.components.TopBarAction
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.pointsColor
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow

/** The feedback after a session, built like the result screen of Mental Math Plus. */
@Composable
fun DebriefScreen(vm: AppViewModel, id: String) {
    val sessions by vm.app.store.sessions.collectAsState()
    val record = sessions.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        PopUp(
            title = "Sitzung löschen?",
            description = "Sitzung, Aufnahme und Auswertung werden ganz gelöscht. Dein Lernprofil bleibt, wie es ist.",
            confirmText = "Löschen",
            onConfirm = { confirmDelete = false; vm.deleteSession(id) },
            onDismiss = { confirmDelete = false },
            confirmColor = red,
        )
    }
    SayItDialog(vm)
    DetailScaffold(
        title = "Auswertung",
        onBack = { vm.tap(); vm.stopSpeaking(); vm.nav.pop() },
        tag = "debrief",
        actions = { TopBarAction(R.drawable.delete_24dp, "Löschen", { confirmDelete = true }) },
    ) {
        if (record == null) {
            BodyText("Diese Sitzung gibt es nicht mehr.")
            return@DetailScaffold
        }
        MessageBanners(vm)
        val d = record.debrief
        if (d == null) {
            AbiCard {
                CardTitle("Noch nicht ausgewertet", icon = R.drawable.hourglass_bottom_24dp, iconTint = yellow)
                BodyText("Du entscheidest: auswerten lassen – oder die Sitzung löschen, wenn du sie nicht brauchst.")
                Gap(10.dp)
                ActionButton("Jetzt auswerten", R.drawable.auto_awesome_24dp, accent, { vm.tap(); vm.openAnalysis(id) }, Modifier.testTag("debrief_analyze"), container = MaterialTheme.colorScheme.background)
                Gap(8.dp)
                ActionButton("Sitzung löschen", R.drawable.delete_24dp, red, { vm.tap(); confirmDelete = true }, Modifier.testTag("debrief_delete"), container = MaterialTheme.colorScheme.background)
            }
            TranscriptSection(record)
            return@DetailScaffold
        }
        SummaryCard(record, d)
        GoalResultsCard(record, d)
        NextStepCard(vm)
        d.progress?.let { ProgressCard(it, d) }
        if (record.levelId.isNotBlank()) {
            de.abicoach.path.Curriculum.level(record.levelId)?.let { level ->
                LevelResultCard(vm, record.levelId, de.abicoach.path.PathEngine.pointsFor(level, d))
            }
        }
        if (d.levers.isNotEmpty()) {
            AbiCard(modifier = Modifier.testTag("debrief_levers")) {
                CardTitle("Das bringt dir die meisten Punkte", icon = R.drawable.rocket_launch_24dp)
                d.levers.forEach { BulletText(it) }
            }
        }
        // The most important open mistake of this session: straight into the Fehler-Werkstatt.
        val workshopPattern =
            remember(d) {
                val open = vm.app.errorMemory.patterns.value.filter { it.state != de.abicoach.learn.PatternState.BEHOBEN }
                // The pattern of the mistake itself, or else the open pattern of the same kind (e.g. a grammar slip without a named rule).
                d.errors.asSequence().mapNotNull { e -> vm.app.errorMemory.matching(e)?.takeIf { it in open } ?: open.firstOrNull { it.category == e.category } }.firstOrNull()
            }
        workshopPattern?.let { p ->
            ActionButton(
                "Fehler-Werkstatt: ${p.title}",
                R.drawable.school_24dp,
                yellow,
                { vm.openWorkshop(p.key) },
                subtitle = "Diesen Fehler jetzt Schritt für Schritt beheben",
                modifier = Modifier.testTag("debrief_workshop"),
            )
        }
        if (d.strengths.isNotEmpty()) {
            AbiCard {
                CardTitle("Das war stark", icon = R.drawable.thumb_up_24dp, iconTint = green)
                d.strengths.forEach { BulletText(it, bulletColor = green) }
            }
        }
        ObservationsCard(d)
        if (record.task != null && record.kind.hasPart1 && d.taskReviews.isNotEmpty()) {
            val weakest = d.taskReviews.minByOrNull { it.points }
            if (weakest != null && weakest.points < 15) {
                ActionButton(
                    "Zweiter Versuch: Aufgabe ${weakest.number}",
                    R.drawable.refresh_24dp,
                    accent,
                    { vm.stopSpeaking(); vm.startRetry(record.id, weakest.number) },
                    subtitle = "Derselbe Text, nur diese Aufgabe (${weakest.points} P.) – 2 Minuten Vorbereitung. Danach siehst du Vorher und Nachher.",
                    modifier = Modifier.testTag("debrief_retry"),
                )
            }
        }
        if (record.kind.hasPart1) TalkCoachCard(record, d)
        if (record.utterances.any { it.speaker == Speaker.STUDENT }) ModelCard(vm, record, d)
        DetailsGroup(vm, record, d)
        TechCard(record)
        ActionButton(
            "Mit dem KI-Coach besprechen",
            R.drawable.psychology_24dp,
            accent,
            { vm.stopSpeaking(); vm.askCoach("Besprich mit mir meine letzte Auswertung (${record.kind.label}, ${d.totalPoints} Punkte): Was waren die wichtigsten Gründe für die Punkte und was soll ich als Nächstes konkret üben?") },
            subtitle = "Er kennt die Auswertung, deine Fehler und dein Kursmaterial",
            modifier = Modifier.testTag("debrief_coach"),
        )
        ActionButton(
            "Noch einmal üben",
            R.drawable.refresh_24dp,
            accent,
            {
                vm.tap()
                vm.nav.push(
                    Screen.Setup(
                        SessionSetup.defaults(record.kind, record.focusTask, vm.app.settings.defaultFeedback).copy(
                            topicChoice = if (record.topicId >= 0) record.topicId else Knowledge.RANDOM,
                            focusCategories = record.focusCategories,
                        ),
                    ),
                )
            },
            subtitle = "Gleiche Übung, neues Material",
        )
        ActionButton("Zur Startseite", R.drawable.light_mode_24dp, green, { vm.tap(); vm.stopSpeaking(); vm.nav.openTab(Tab.WEG) })
    }
}

/** One section of the folding details under an evaluation. */
private class Fold(
    val title: String,
    val icon: Int,
    val tint: Color,
    val tag: String,
    val count: String? = null,
    val countColor: Color? = null,
    val subtitle: String? = null,
    val open: Boolean = false,
    val content: @Composable ColumnScope.() -> Unit,
)

/** Everything below the most important part folds away into one card: open what you want to look at. */
@Composable
private fun DetailsGroup(vm: AppViewModel, record: SessionRecord, d: Debrief) {
    val dimens = AbiTheme.dimens
    val folds =
        buildList<Fold> {
            add(
                Fold("Warum diese Punkte", R.drawable.grading_24dp, accent, "debrief_reasons", subtitle = "Begründung, Zweitbewertung, Prüfer") {
                    ReasonsContent(record, d)
                },
            )
            if (d.taskReviews.isNotEmpty()) {
                add(Fold("Die Aufgaben im Einzelnen", R.drawable.assignment_24dp, accent, "debrief_tasks", count = d.taskReviews.size.toString()) { TaskReviewContent(record, d) })
            }
            if (d.errors.isNotEmpty()) {
                val grouped = d.errors.groupBy { it.category }.entries.sortedByDescending { it.value.size }
                add(
                    Fold(
                        "Deine Fehler",
                        R.drawable.spellcheck_24dp,
                        red,
                        "debrief_errors",
                        count = d.errors.size.toString(),
                        countColor = red,
                        subtitle = grouped.take(2).joinToString(" · ") { "${Knowledge.categoryLabel(it.key)} ${it.value.size}×" },
                        open = d.errors.size <= 3,
                    ) { ErrorsContent(vm, d) },
                )
            }
            if (d.upgrades.isNotEmpty()) {
                add(
                    Fold("So klingt es nach 15 Punkten", R.drawable.auto_awesome_24dp, yellow, "debrief_upgrades", count = d.upgrades.size.toString()) {
                        d.upgrades.forEachIndexed { i, u ->
                            if (i > 0) Gap(12.dp)
                            CaptionText("Du: „${u.original}“")
                            Row(verticalAlignment = Alignment.Top) {
                                BodyText("„${u.upgraded}“", color = green, modifier = Modifier.weight(1f))
                                SpeakButton(vm, u.upgraded)
                                SayItIcon(vm, u.original, u.upgraded, u.why)
                            }
                            CaptionText(u.why)
                        }
                    },
                )
            }
            if (d.pronunciation.isNotEmpty()) {
                add(
                    Fold("Aussprache", R.drawable.graphic_eq_24dp, green, "debrief_pronunciation", count = d.pronunciation.size.toString()) {
                        d.pronunciation.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.word, fontSize = dimens.fonts.label * 0.9f, color = MaterialTheme.colorScheme.onPrimary)
                                    CaptionText(p.issue)
                                    BodyText(p.tip)
                                }
                                SpeakButton(vm, p.word, prefetch = true)
                            }
                            Gap(8.dp)
                        }
                        ActionButton(
                            "Im Aussprache-Labor üben",
                            R.drawable.mic_24dp,
                            accent,
                            { vm.stopSpeaking(); vm.openPronunciation(d.pronunciation.first().word) },
                            subtitle = "Nachsprechen und sofort eine Bewertung bekommen",
                            container = MaterialTheme.colorScheme.background,
                        )
                    },
                )
            }
            if (d.contentFeedback.isNotEmpty()) {
                add(Fold("Inhalt", R.drawable.psychology_24dp, accent, "debrief_content") { d.contentFeedback.forEach { BulletText(it) } })
            }
            if (d.courseLinks.isNotEmpty()) {
                add(Fold("Aus deinem Kurs hätte gepasst", R.drawable.school_24dp, accent, "debrief_course") { d.courseLinks.forEach { BulletText(it) } })
            }
            if (d.phrases.isNotEmpty()) {
                add(
                    Fold("Redemittel für dich", R.drawable.translate_24dp, accent, "debrief_phrases", count = d.phrases.size.toString()) {
                        d.phrases.forEach { p ->
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    BodyText(p.phrase, color = accent)
                                    CaptionText(p.meaningDe)
                                    Text(p.example, fontSize = dimens.fonts.caption, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                }
                                SpeakButton(vm, p.example.ifBlank { p.phrase })
                            }
                            Gap(10.dp)
                        }
                        CaptionText("Alle Redemittel stehen jetzt auch in deinem Wortschatz zum Wiederholen.")
                    },
                )
            }
            val task = record.task
            if (task != null && task.tasks.any { it.expectations.isNotEmpty() }) {
                add(
                    Fold("Erwartungshorizont", R.drawable.checklist_24dp, accent, "debrief_expectations", subtitle = "Was ein 15-Punkte-Vortrag enthalten hätte") {
                        task.tasks.forEachIndexed { i, t ->
                            if (record.focusTask == 0 || record.focusTask == i + 1) {
                                Text("Aufgabe ${i + 1} (AFB ${t.afb})", fontSize = dimens.fonts.body, color = accent)
                                CaptionText(t.instruction)
                                t.expectations.forEach { BulletText(it) }
                                Gap(8.dp)
                            }
                        }
                    },
                )
            }
            val rated = Skill.entries.mapNotNull { s -> d.skills[s]?.let { s to it } }
            if (rated.isNotEmpty()) {
                add(
                    Fold("Kompetenzen", R.drawable.insights_24dp, accent, "debrief_skills", subtitle = "So hat die KI diese Sitzung eingeschätzt") {
                        rated.forEach { (s, v) ->
                            BarRow(s.label, v / 15f, "$v P.", pointsColor(v.toDouble()))
                            Gap(6.dp)
                        }
                    },
                )
            }
            if (record.utterances.isNotEmpty()) {
                add(
                    Fold("Transkript", R.drawable.text_snippet_24dp, MaterialTheme.colorScheme.onPrimary, "debrief_transcript", subtitle = "${record.utterances.count { it.speaker == Speaker.STUDENT }} Beiträge von dir") {
                        TranscriptContent(record)
                    },
                )
            }
        }
    FoldGroup(modifier = Modifier.testTag("debrief_details")) {
        folds.forEachIndexed { i, f ->
            FoldRow(
                title = f.title,
                icon = f.icon,
                iconTint = f.tint,
                subtitle = f.subtitle,
                count = f.count,
                countColor = f.countColor,
                initiallyOpen = f.open,
                divider = i > 0,
                modifier = Modifier.testTag(f.tag),
                content = f.content,
            )
        }
    }
}

/** Why the session got its points: the reasons for both areas, the second grading and who graded. */
@Composable
private fun ReasonsContent(record: SessionRecord, d: Debrief) {
    BarRow("Inhalt (40 %)", d.inhaltPoints / 15f, "${d.inhaltPoints} P.", pointsColor(d.inhaltPoints.toDouble()))
    if (d.inhaltReason.isNotBlank()) CaptionText(d.inhaltReason, modifier = Modifier.padding(top = 4.dp))
    Gap(10.dp)
    BarRow("Darstellungsleistung (60 %)", d.darstellungPoints / 15f, "${d.darstellungPoints} P.", pointsColor(d.darstellungPoints.toDouble()))
    if (d.darstellungReason.isNotBlank()) CaptionText(d.darstellungReason, modifier = Modifier.padding(top = 4.dp))
    SecondOpinionLine(d)
    Gap(10.dp)
    val by =
        listOfNotNull(
            d.gradedBy.ifBlank { null }?.let { "Bewertet von $it" },
            d.listenedBy.ifBlank { null }?.takeIf { it != d.gradedBy }?.let { "Aufnahme gehört von $it" },
            record.examiner.ifBlank { null }?.let { "Prüfer: $it" },
        )
    if (by.isNotEmpty()) CaptionText(by.joinToString(" · "))
}

/** A thin white bar on the hero card. */
@Composable
private fun HeroBar(label: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f))
            Text("$value", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth((value / 15f).coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        }
    }
}

@Composable
private fun SummaryCard(record: SessionRecord, d: Debrief) {
    HeroCard(modifier = Modifier.testTag("debrief_summary")) {
        Text("${record.title} · ${Knowledge.topicShort(record.topicId)}", fontSize = 13.sp, lineHeight = 17.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 2)
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigNumber(d.totalPoints.toString(), "Punkte", Color.White, track = Color.White.copy(alpha = 0.25f), textColor = Color.White, size = 86.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Planner.gradeLabel(d.totalPoints), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    StarRow(stars = d.stars, size = 16.dp)
                }
                HeroBar("Inhalt", d.inhaltPoints)
                HeroBar("Darstellung", d.darstellungPoints)
            }
        }
        d.secondOpinion?.let { second ->
            Gap(12.dp)
            HeroChip("Zwei Prüfer: ${second.firstTotal} und ${second.total} → ${d.totalPoints} · " + if (second.agrees) "verlässlich" else "weichen ab")
        }
        Gap(10.dp)
        Text(formatDate(record.startedAt) + " · " + ((record.durationSec + 30) / 60).coerceAtLeast(1) + " Min.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
        if (!d.audioAnalysed) Text("Ohne Aufnahme bewertet – Aussprache und Flüssigkeit sind nur geschätzt.", fontSize = 12.sp, lineHeight = 16.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

/** What changed since the last session (or since the first attempt): points, mistake types that are gone and those that came back. */
@Composable
private fun ProgressCard(p: Progress, d: Debrief) {
    val dimens = AbiTheme.dimens
    val delta = d.totalPoints - p.comparedPoints
    AbiCard(modifier = Modifier.testTag("debrief_progress")) {
        CardTitle(if (p.taskNumber in 1..3) "Vorher und nachher" else "Dein Fortschritt", icon = R.drawable.insights_24dp, iconTint = green)
        CaptionText("Verglichen mit: ${p.comparedLabel}")
        Gap(4.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${p.comparedPoints} P.", fontSize = dimens.fonts.label, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
            Text("  →  ", fontSize = dimens.fonts.label, color = MaterialTheme.colorScheme.onPrimary)
            Text("${d.totalPoints} P.", fontSize = dimens.fonts.label, color = pointsColor(d.totalPoints.toDouble()))
            Spacer(Modifier.width(10.dp))
            Text(
                if (delta > 0) "+$delta" else if (delta < 0) "$delta" else "±0",
                fontSize = dimens.fonts.label,
                color = if (delta > 0) green else if (delta < 0) red else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            )
        }
        if (p.taskNumber in 1..3 && p.taskBefore >= 0 && p.taskAfter >= 0) {
            Gap(6.dp)
            BodyText("Aufgabe ${p.taskNumber}: ${p.taskBefore} → ${p.taskAfter} Punkte")
        }
        if (p.fixed.isNotEmpty() || p.still.isNotEmpty()) Gap(6.dp)
        p.fixed.forEach { GoalRow(ok = true, text = it) }
        p.still.forEach { GoalRow(ok = false, text = it) }
        Gap(4.dp)
        CaptionText("Diese Sitzung wurde für sich bewertet – den Vergleich zeigt die App erst danach.")
    }
}

@Composable
private fun TaskReviewContent(record: SessionRecord, d: Debrief) {
    val dimens = AbiTheme.dimens
    d.taskReviews.sortedBy { it.number }.forEachIndexed { i, r ->
        if (i > 0) Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(r.number.toString(), locked = false, mastered = r.points >= 13, attention = false, highlighted = r.points < 13, size = dimens.components.button * 0.5f)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val instruction = record.task?.tasks?.getOrNull(r.number - 1)?.instruction.orEmpty()
                Text("Aufgabe ${r.number} · ${r.points} P.", fontSize = dimens.fonts.body, color = pointsColor(r.points.toDouble()))
                if (instruction.isNotBlank()) CaptionText(instruction)
            }
        }
        r.covered.forEach { GoalRow(ok = true, text = it) }
        r.missing.forEach { GoalRow(ok = false, text = it) }
        if (r.tip.isNotBlank()) CaptionText("Tipp: " + r.tip, color = accent)
    }
}

/** The student's own performance rewritten at 15-point level (made on request). */
@Composable
private fun ModelCard(vm: AppViewModel, record: SessionRecord, d: Debrief) {
    val presentation = record.kind.hasPart1 && record.utterances.any { it.part == 1 }
    AbiCard(modifier = Modifier.testTag("debrief_model")) {
        CardTitle(if (presentation) "Dein Vortrag auf 15-Punkte-Niveau" else "Deine Antworten auf 15-Punkte-Niveau", icon = R.drawable.workspace_premium_24dp, iconTint = green)
        if (d.model.isEmpty()) {
            CaptionText("Die KI schreibt deine eigene Leistung so um, wie sie für 15 Punkte klingen würde: deine Ideen und dein Aufbau, aber mit Top-Sprache und allem, was gefehlt hat.")
            Gap(8.dp)
            if (vm.modelBusy == record.id) {
                ProgressLine(null)
                CaptionText("Wird geschrieben … (ca. 20–40 Sekunden)")
            } else {
                ActionButton(
                    "Musterfassung erstellen",
                    R.drawable.auto_awesome_24dp,
                    accent,
                    { vm.tap(); vm.makeModel(record.id) },
                    container = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("debrief_model_make"),
                )
            }
        } else {
            d.model.forEachIndexed { i, part ->
                if (i > 0) Gap(14.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(part.title, fontSize = AbiTheme.dimens.fonts.body, color = accent, modifier = Modifier.weight(1f))
                    SpeakButton(vm, part.text)
                }
                BodyText(part.text)
                if (part.notes.isNotEmpty()) {
                    Gap(4.dp)
                    part.notes.forEach { GoalRow(ok = true, text = it) }
                }
            }
            Gap(8.dp)
            CaptionText("Tipp: Einmal laut lesen, dann nur mit Stichpunkten frei nachsprechen – so wird es deins.")
        }
    }
}

@Composable
private fun ErrorsContent(vm: AppViewModel, d: Debrief) {
    val dimens = AbiTheme.dimens
    val grouped = d.errors.groupBy { it.category }.entries.sortedByDescending { it.value.size }
    grouped.forEach { (category, errors) ->
        Text(Knowledge.categoryLabel(category), fontSize = dimens.fonts.body, color = accent)
        errors.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("„${e.quote}“", fontSize = dimens.fonts.body, color = red, textDecoration = TextDecoration.LineThrough)
                    Text("→ „${e.correction}“", fontSize = dimens.fonts.body, color = green)
                    if (e.explanation.isNotBlank()) CaptionText(e.explanation)
                    vm.app.errorMemory.matching(e)?.takeIf { it.total >= 2 }?.let { p ->
                        CaptionText("${p.title}: schon ${p.total}× · ${p.state.label}", color = if (p.total >= 3) red else yellow)
                    }
                }
                SayItIcon(vm, e.quote, e.correction, e.explanation)
            }
        }
        Gap(8.dp)
    }
    CaptionText("Tippe auf das Mikrofon, um den Satz richtig nachzusprechen – die KI hört zu und sagt dir sofort, ob es stimmt.")
    if (d.droppedUnverified > 0) CaptionText("${d.droppedUnverified} Korrekturen weggelassen, weil das Zitat so nicht in deiner Mitschrift stand.")
    val keys = d.errors.mapNotNull { vm.app.errorMemory.matching(it)?.key }.distinct().take(4)
    if (keys.isNotEmpty()) {
        Gap(8.dp)
        ActionButton(
            "Diese Fehler im Sprech-Training üben",
            R.drawable.spellcheck_24dp,
            yellow,
            { vm.stopSpeaking(); vm.openDrills(keys) },
            subtitle = "Kurze Sprech-Übungen mit Sätzen zu deinen Themen · ca. 5 Min.",
            container = MaterialTheme.colorScheme.background,
            modifier = Modifier.testTag("debrief_drills"),
        )
    }
}

@Composable
private fun TranscriptContent(record: SessionRecord) {
    val dimens = AbiTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        record.utterances.forEach { u ->
            val who = if (u.speaker == Speaker.STUDENT) "Du" else "Prüfer"
            val part = when (u.part) { 1 -> "Teil 1 · "; 2 -> "Teil 2 · "; else -> "" }
            CaptionText(part + who, color = if (u.speaker == Speaker.STUDENT) accent else null)
            Text(u.text, fontSize = dimens.fonts.body * 0.95f, lineHeight = dimens.fonts.body * 1.3f, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** The transcript as its own folding card (for sessions without an evaluation). */
@Composable
private fun TranscriptSection(record: SessionRecord) {
    if (record.utterances.isEmpty()) return
    ExpandableCard("Transkript", subtitle = "${record.utterances.count { it.speaker == Speaker.STUDENT }} Beiträge von dir", icon = R.drawable.text_snippet_24dp) {
        TranscriptContent(record)
    }
}
