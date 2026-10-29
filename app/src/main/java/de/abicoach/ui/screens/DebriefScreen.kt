package de.abicoach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.GoalRow
import de.abicoach.ui.components.LevelBadge
import de.abicoach.ui.components.PopUp
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
            description = "Auswertung und Aufnahme werden gelöscht. Dein Lernprofil bleibt, wie es ist.",
            confirmText = "Löschen",
            onConfirm = { confirmDelete = false; vm.deleteSession(id) },
            onDismiss = { confirmDelete = false },
            confirmColor = red,
        )
    }
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
                BodyText("Die Auswertung hat nicht geklappt oder wurde abgebrochen.")
                Gap(10.dp)
                ActionButton("Jetzt auswerten", R.drawable.auto_awesome_24dp, accent, { vm.tap(); vm.openAnalysis(id) }, container = MaterialTheme.colorScheme.background)
            }
            TranscriptSection(record)
            return@DetailScaffold
        }
        SummaryCard(record, d)
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
        if (d.strengths.isNotEmpty()) {
            AbiCard {
                CardTitle("Das war stark", icon = R.drawable.thumb_up_24dp, iconTint = green)
                d.strengths.forEach { BulletText(it, bulletColor = green) }
            }
        }
        if (d.taskReviews.isNotEmpty()) TaskReviewCard(record, d)
        record.task?.let { task ->
            if (task.tasks.any { it.expectations.isNotEmpty() }) {
                ExpandableCard("Erwartungshorizont", subtitle = "Was ein 15-Punkte-Vortrag enthalten hätte", icon = R.drawable.checklist_24dp) {
                    task.tasks.forEachIndexed { i, t ->
                        if (record.focusTask == 0 || record.focusTask == i + 1) {
                            Text("Aufgabe ${i + 1} (AFB ${t.afb})", fontSize = AbiTheme.dimens.fonts.body, color = accent)
                            CaptionText(t.instruction)
                            t.expectations.forEach { BulletText(it) }
                            Gap(8.dp)
                        }
                    }
                }
            }
        }
        val rated = Skill.entries.mapNotNull { s -> d.skills[s]?.let { s to it } }
        if (rated.isNotEmpty()) {
            ExpandableCard("Kompetenzen", subtitle = "So hat die KI diese Sitzung eingeschätzt", icon = R.drawable.insights_24dp) {
                rated.forEach { (s, v) ->
                    BarRow(s.label, v / 15f, "$v P.", pointsColor(v.toDouble()))
                    Gap(6.dp)
                }
            }
        }
        if (d.errors.isNotEmpty()) ErrorsCard(d)
        if (d.upgrades.isNotEmpty()) {
            AbiCard(modifier = Modifier.testTag("debrief_upgrades")) {
                CardTitle("So klingt es nach 15 Punkten", icon = R.drawable.auto_awesome_24dp)
                d.upgrades.forEachIndexed { i, u ->
                    if (i > 0) Gap(12.dp)
                    CaptionText("Du: „${u.original}“")
                    Row(verticalAlignment = Alignment.Top) {
                        BodyText("„${u.upgraded}“", color = green, modifier = Modifier.weight(1f))
                        SpeakButton(vm, u.upgraded)
                    }
                    CaptionText(u.why)
                }
            }
        }
        if (d.pronunciation.isNotEmpty()) {
            AbiCard {
                CardTitle("Aussprache", icon = R.drawable.graphic_eq_24dp)
                d.pronunciation.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.word, fontSize = AbiTheme.dimens.fonts.label, color = MaterialTheme.colorScheme.onPrimary)
                            CaptionText(p.issue)
                            BodyText(p.tip)
                        }
                        SpeakButton(vm, p.word)
                    }
                    Gap(8.dp)
                }
            }
        }
        if (d.contentFeedback.isNotEmpty()) {
            AbiCard {
                CardTitle("Inhalt", icon = R.drawable.psychology_24dp)
                d.contentFeedback.forEach { BulletText(it) }
            }
        }
        if (d.courseLinks.isNotEmpty()) {
            AbiCard {
                CardTitle("Aus deinem Kurs hätte gepasst", icon = R.drawable.school_24dp)
                d.courseLinks.forEach { BulletText(it) }
            }
        }
        if (d.phrases.isNotEmpty()) {
            ExpandableCard("Redemittel für dich", subtitle = "${d.phrases.size} Formulierungen zum Thema", icon = R.drawable.translate_24dp) {
                d.phrases.forEach { p ->
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            BodyText(p.phrase, color = accent)
                            CaptionText(p.meaningDe)
                            Text(p.example, fontSize = AbiTheme.dimens.fonts.caption, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                        }
                        SpeakButton(vm, p.example.ifBlank { p.phrase })
                    }
                    Gap(10.dp)
                }
            }
        }
        TranscriptSection(record)
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
        ActionButton("Zum Abi-Weg", R.drawable.route_24dp, green, { vm.tap(); vm.stopSpeaking(); vm.nav.openTab(Tab.WEG) })
    }
}

@Composable
private fun SummaryCard(record: SessionRecord, d: Debrief) {
    val dimens = AbiTheme.dimens
    AbiCard(modifier = Modifier.testTag("debrief_summary")) {
        CardTitle("${record.title} · ${Knowledge.topicShort(record.topicId)}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigNumber(d.totalPoints.toString(), "Punkte", pointsColor(d.totalPoints.toDouble()))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                StarRow(stars = d.stars, size = dimens.icons.scoreHeader * 0.5f)
                Gap(4.dp)
                Text(Planner.gradeLabel(d.totalPoints), fontSize = dimens.fonts.label, color = pointsColor(d.totalPoints.toDouble()))
                CaptionText(formatDate(record.startedAt) + " · " + mmss(record.durationSec) + " Min.")
            }
        }
        Gap(12.dp)
        BarRow("Inhalt (40 %)", d.inhaltPoints / 15f, "${d.inhaltPoints} P.", pointsColor(d.inhaltPoints.toDouble()))
        if (d.inhaltReason.isNotBlank()) CaptionText(d.inhaltReason, modifier = Modifier.padding(top = 4.dp))
        Gap(10.dp)
        BarRow("Darstellungsleistung (60 %)", d.darstellungPoints / 15f, "${d.darstellungPoints} P.", pointsColor(d.darstellungPoints.toDouble()))
        if (d.darstellungReason.isNotBlank()) CaptionText(d.darstellungReason, modifier = Modifier.padding(top = 4.dp))
        Gap(10.dp)
        val by =
            listOfNotNull(
                d.gradedBy.ifBlank { null }?.let { "Bewertet von $it" },
                d.listenedBy.ifBlank { null }?.takeIf { it != d.gradedBy }?.let { "Aufnahme gehört von $it" },
                record.examiner.ifBlank { null }?.let { "Prüfer: $it" },
            )
        if (by.isNotEmpty()) CaptionText(by.joinToString(" · "))
        if (!d.audioAnalysed) CaptionText("Ohne Aufnahme bewertet – Aussprache und Flüssigkeit sind nur geschätzt.")
    }
}

@Composable
private fun TaskReviewCard(record: SessionRecord, d: Debrief) {
    val dimens = AbiTheme.dimens
    AbiCard(modifier = Modifier.testTag("debrief_tasks")) {
        CardTitle("Die Aufgaben", icon = R.drawable.assignment_24dp)
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
}

@Composable
private fun ErrorsCard(d: Debrief) {
    val dimens = AbiTheme.dimens
    val grouped = d.errors.groupBy { it.category }.entries.sortedByDescending { it.value.size }
    ExpandableCard(
        "Fehler (${d.errors.size})",
        subtitle = grouped.take(3).joinToString(" · ") { "${Knowledge.categoryLabel(it.key)} ${it.value.size}×" },
        icon = R.drawable.spellcheck_24dp,
        iconTint = red,
        initiallyOpen = d.errors.size <= 6,
    ) {
        grouped.forEach { (category, errors) ->
            Text(Knowledge.categoryLabel(category), fontSize = dimens.fonts.body, color = accent)
            errors.forEach { e ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("„${e.quote}“", fontSize = dimens.fonts.body, color = red, textDecoration = TextDecoration.LineThrough)
                    Text("→ „${e.correction}“", fontSize = dimens.fonts.body, color = green)
                    if (e.explanation.isNotBlank()) CaptionText(e.explanation)
                }
            }
            Gap(8.dp)
        }
        if (d.droppedUnverified > 0) CaptionText("${d.droppedUnverified} Korrekturen weggelassen, weil das Zitat so nicht in deiner Mitschrift stand.")
    }
}

@Composable
private fun TranscriptSection(record: SessionRecord) {
    if (record.utterances.isEmpty()) return
    val dimens = AbiTheme.dimens
    ExpandableCard("Transkript", subtitle = "${record.utterances.count { it.speaker == Speaker.STUDENT }} Beiträge von dir", icon = R.drawable.text_snippet_24dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            record.utterances.forEach { u ->
                val who = if (u.speaker == Speaker.STUDENT) "Du" else "Prüfer"
                val part = when (u.part) { 1 -> "Teil 1 · "; 2 -> "Teil 2 · "; else -> "" }
                CaptionText(part + who, color = if (u.speaker == Speaker.STUDENT) accent else null)
                Text(u.text, fontSize = dimens.fonts.body * 0.95f, lineHeight = dimens.fonts.body * 1.3f, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
