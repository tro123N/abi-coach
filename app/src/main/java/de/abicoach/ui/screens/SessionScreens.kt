package de.abicoach.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.audio.FeedbackType
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.Phrases
import de.abicoach.data.SessionKind
import de.abicoach.data.Speaker
import de.abicoach.data.Utterance
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.SessionSetup
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.AbiTextField
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.AreaIntro
import de.abicoach.ui.components.Banner
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.BulletText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.ChoiceChips
import de.abicoach.ui.components.ExpandableCard
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.LevelBadge
import de.abicoach.ui.components.LoadingCard
import de.abicoach.ui.components.Pill
import de.abicoach.ui.components.PillRow
import de.abicoach.ui.components.PopUp
import de.abicoach.ui.components.ProgressLine
import de.abicoach.ui.components.StartButton
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow
import kotlinx.coroutines.delay

private fun kindDescription(s: SessionSetup): String =
    when (s.kind) {
        SessionKind.SIMULATION -> "Wie im Abi: neues Material, Vorbereitungszeit, ca. 10 Minuten Vortrag und 10 Minuten Prüfungsgespräch mit Themenwechsel."
        SessionKind.PLACEMENT -> "Kurzer Text, kurzer Vortrag, kurzes Gespräch – danach kennt die App deinen Stand."
        SessionKind.PART1 ->
            when (s.focusTask) {
                1 -> "Nur Aufgabe 1: Du fasst den Text zusammen (ca. 2–3 Minuten). Simple present, nur das Wichtigste, keine Meinung."
                2 -> "Nur Aufgabe 2: Du analysierst den Text, z. B. wie der Autor etwas sieht und mit welchen Mitteln er wirkt (ca. 3–4 Minuten)."
                3 -> "Nur Aufgabe 3: Du nimmst Stellung – mit Argumenten und Beispielen aus dem Unterricht (ca. 3 Minuten)."
                else -> "Der ganze erste Prüfungsteil: ein neuer Text mit drei Aufgaben, ca. 10 Minuten freier Vortrag."
            }
        SessionKind.PART2 -> "Der zweite Prüfungsteil: 10 Minuten Gespräch über größere Zusammenhänge, mit Themenwechsel wie im Abi."
        SessionKind.TRAINING -> "Ein freies Gespräch zu einem Themenfeld – ideal, um mit Flüstern oder Coach an deinen Fehlern zu arbeiten."
    }

private fun setupTitle(s: SessionSetup): String =
    when {
        s.kind == SessionKind.PART1 && s.focusTask in 1..3 -> "Aufgabe ${s.focusTask}: " + Knowledge.TASK_PATTERNS[s.focusTask - 1].germanName
        else -> s.kind.label
    }

/** Choose topic field, material, text type, preparation time and feedback, then start. */
@Composable
fun SetupScreen(vm: AppViewModel, initial: SessionSetup) {
    val app = vm.app
    val hasMaterial = remember { app.course.hasMaterial() }
    var s by remember(initial) {
        mutableStateOf(if (initial.topicChoice == Knowledge.FROM_COURSE && !hasMaterial) initial.copy(topicChoice = Knowledge.RANDOM) else initial)
    }
    val start = rememberMicGate { vm.start(s) }
    val topicOptions = listOfNotNull(if (hasMaterial) Knowledge.FROM_COURSE else null, Knowledge.RANDOM) + Knowledge.TOPICS.map { it.id }

    DetailScaffold(
        title = setupTitle(s),
        onBack = { vm.tap(); vm.nav.pop() },
        tag = "setup",
        bottom = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    StartButton("Los geht's", onClick = start, modifier = Modifier.testTag("setup_start"))
                }
            }
        },
    ) {
        AreaIntro(de.abicoach.ui.screens.kindIcon(s.kind), s.kind.label, kindDescription(s))
        MessageBanners(vm)
        AbiCard {
            CardTitle("Themenfeld", icon = R.drawable.hub_24dp)
            ChoiceChips(
                options = topicOptions,
                selected = s.topicChoice,
                label = { id ->
                    when (id) {
                        Knowledge.FROM_COURSE -> "Aus meinem Kurs"
                        Knowledge.RANDOM -> "Zufällig"
                        else -> Knowledge.topicShort(id)
                    }
                },
                onSelect = { vm.tap(); s = s.copy(topicChoice = it) },
                tagPrefix = "topic",
            )
            if (!hasMaterial) {
                Gap(8.dp)
                CaptionText("Tipp: Lade in „Mein Kurs“ dein Material hoch – dann passen Texte und Fragen genau zu eurem Unterricht.")
            }
        }
        if (s.kind.hasPart1) {
            AbiCard {
                CardTitle("Material für Teil 1", icon = R.drawable.article_24dp)
                if (s.kind != SessionKind.PLACEMENT) {
                    ChoiceChips(
                        options = Knowledge.Source.entries,
                        selected = s.source,
                        label = { it.label },
                        onSelect = { vm.tap(); s = s.copy(source = it) },
                        tagPrefix = "source",
                    )
                    Gap(6.dp)
                    CaptionText(s.source.description + if (s.source != Knowledge.Source.NEW && !hasMaterial) " (Noch kein Material da – dann wird es ein neuer Text.)" else "")
                    Gap(12.dp)
                }
                if (s.source != Knowledge.Source.MATERIAL || s.kind == SessionKind.PLACEMENT) {
                    BodyText("Textart")
                    Gap(6.dp)
                    ChoiceChips(
                        options = Knowledge.TextType.entries,
                        selected = s.textType,
                        label = { it.label },
                        onSelect = { vm.tap(); s = s.copy(textType = it) },
                        tagPrefix = "texttype",
                    )
                    Gap(12.dp)
                }
                BodyText("Vorbereitungszeit")
                Gap(6.dp)
                val times =
                    when {
                        s.kind == SessionKind.SIMULATION -> listOf(30, 20, 10)
                        s.kind == SessionKind.PLACEMENT -> listOf(8, 5)
                        s.focusTask in 1..3 -> listOf(8, 5, 3)
                        else -> listOf(30, 20, 10)
                    }
                ChoiceChips(
                    options = times,
                    selected = if (s.prepMinutes in times) s.prepMinutes else times.first(),
                    label = { "$it Min." },
                    onSelect = { vm.tap(); s = s.copy(prepMinutes = it) },
                    tagPrefix = "prep",
                )
            }
        }
        AbiCard {
            CardTitle("Rückmeldung beim Sprechen", icon = R.drawable.hearing_24dp)
            ChoiceChips(
                options = FeedbackMode.entries,
                selected = s.feedback,
                label = { it.label },
                onSelect = { vm.tap(); s = s.copy(feedback = it) },
                tagPrefix = "feedback",
            )
            Gap(6.dp)
            CaptionText(s.feedback.description)
        }
        AbiCard {
            CardTitle("Deine KI für diese Übung", icon = R.drawable.auto_awesome_24dp)
            if (s.kind.hasPart2 || !s.kind.hasPart1) CaptionText("Prüfer: ${app.ai.examinerLabel()}" + if (app.ai.useOpenAi) " – hört und spricht gleichzeitig" else "")
            CaptionText("Mitschrift: ${de.abicoach.ai.AiHub.pretty(app.settings.transcribeModel)} (wortgetreu, mit deinen Fehlern)")
            if (s.feedback != FeedbackMode.PRUEFUNG) CaptionText("Fehlerprüfung beim Sprechen: ${app.ai.fastLabel()}")
            if (s.kind.hasPart1) CaptionText("Text und Aufgaben: ${app.ai.brainLabel()}")
            CaptionText("Bewertung: ${app.ai.brainLabel()}" + if (app.ai.useClaude) " · Aufnahme hört ${app.ai.listenerLabel()}" else "")
        }
    }
}

/** Waiting while the AI writes the material. */
@Composable
fun GeneratingScreen(vm: AppViewModel) {
    BackHandler { vm.cancelGenerating() }
    DetailScaffold(title = "Material wird erstellt", onBack = { vm.cancelGenerating() }, backIcon = R.drawable.close_24dp, tag = "generating") {
        LoadingCard(vm.progressText.ifBlank { "Einen Moment …" }, detail = "Meist 20–60 Sekunden. Die Vorbereitungszeit beginnt erst danach.")
        AbiCard {
            CardTitle("Gut zu wissen", icon = R.drawable.lightbulb_24dp, iconTint = yellow)
            BulletText("Teil 1 hat immer drei Aufgaben: zusammenfassen, analysieren, kommentieren.")
            BulletText("Notizen sind erlaubt – aber nur Stichpunkte. Wer abliest, verliert Punkte in der Darstellung.")
            BulletText("Für 13 Punkte und mehr braucht es eine eigenständige, gut begründete Stellungnahme (AFB III).")
        }
    }
}

/** The preparation time: text, tasks, notes and phrases. */
@Composable
fun PrepScreen(vm: AppViewModel) {
    val task = vm.task
    val draft = vm.draft
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmLeave by remember { mutableStateOf(false) }
    var timeUpSignalled by remember { mutableStateOf(false) }
    val start = rememberMicGate { vm.startPart1() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val remaining = ((vm.prepEndsAt - now) / 1000).toInt()
    LaunchedEffect(remaining <= 0) {
        if (remaining <= 0 && !timeUpSignalled && task != null) {
            timeUpSignalled = true
            vm.app.feedback.play(FeedbackType.SKIP)
        }
    }
    BackHandler { confirmLeave = true }
    if (confirmLeave) {
        PopUp(
            title = "Vorbereitung abbrechen?",
            description = "Das Material geht verloren.",
            confirmText = "Abbrechen",
            onConfirm = { confirmLeave = false; vm.cancelPrep() },
            onDismiss = { confirmLeave = false },
            cancelText = "Weiter vorbereiten",
            confirmColor = red,
        )
    }
    if (task == null || draft == null) {
        DetailScaffold(title = "Vorbereitung", onBack = { vm.cancelPrep() }) { BodyText("Kein Material vorhanden.") }
        return
    }
    val dimens = AbiTheme.dimens
    DetailScaffold(
        title = "Vorbereitung",
        onBack = { confirmLeave = true },
        backIcon = R.drawable.close_24dp,
        tag = "prep",
        actions = {
            Text(
                if (remaining > 0) mmss(remaining) else "Zeit!",
                fontSize = dimens.fonts.label,
                color = if (remaining > 60) MaterialTheme.colorScheme.onPrimary else if (remaining > 0) yellow else red,
                modifier = Modifier.padding(end = 16.dp).testTag("prep_timer"),
            )
        },
        bottom = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    StartButton(if (draft.focusTask in 1..3) "Sprechen" else "Vortrag beginnen", onClick = start, modifier = Modifier.testTag("prep_start"), icon = R.drawable.mic_24dp)
                }
            }
        },
    ) {
        if (remaining <= 0) Banner("Die Vorbereitungszeit ist um – beginne jetzt deinen Vortrag.", yellow, icon = R.drawable.timer_24dp)
        MessageBanners(vm)
        AbiCard(modifier = Modifier.testTag("prep_text")) {
            PillRow(listOfNotNull(Knowledge.topicShort(task.topicId), task.textType.ifBlank { null }, "${task.wordCount} Wörter"))
            Gap(10.dp)
            Text(task.title, fontSize = dimens.fonts.label, lineHeight = dimens.fonts.label * 1.2f, color = MaterialTheme.colorScheme.onPrimary)
            if (task.sourceNote.isNotBlank()) CaptionText(task.sourceNote)
            Gap(10.dp)
            SelectionContainer {
                Text(task.text, fontSize = dimens.fonts.reading, lineHeight = dimens.fonts.reading * 1.5f, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        AbiCard {
            CardTitle("Aufgaben", icon = R.drawable.assignment_24dp)
            task.tasks.forEachIndexed { i, t ->
                val active = draft.focusTask == 0 || draft.focusTask == i + 1
                TaskLine(i + 1, t.afb, t.instruction, active)
            }
            if (draft.focusTask in 1..3) {
                Gap(4.dp)
                CaptionText("Bei dieser Übung bearbeitest du nur Aufgabe ${draft.focusTask}.")
            }
        }
        AbiCard {
            CardTitle("Notizen", icon = R.drawable.edit_note_24dp)
            AbiTextField(
                value = vm.notes,
                onValueChange = { vm.notes = it },
                label = "Stichpunkte",
                singleLine = false,
                minLines = 6,
                maxLines = 20,
                tag = "prep_notes",
            )
            Gap(6.dp)
            CaptionText("Nur Stichpunkte – ganze Sätze verleiten zum Ablesen, und das kostet Punkte.")
        }
        val groups =
            if (draft.focusTask in 1..3) listOf(Phrases.PART1[draft.focusTask - 1], Phrases.PART1.last()) else Phrases.PART1
        ExpandableCard("Redemittel", subtitle = "Formulierungen für deine Aufgaben", icon = R.drawable.translate_24dp) {
            groups.forEach { g ->
                Text(g.title, fontSize = dimens.fonts.body, color = accent)
                CaptionText(g.hint)
                g.phrases.forEach { BulletText(it) }
                Gap(10.dp)
            }
        }
    }
}

@Composable
private fun TaskLine(number: Int, afb: String, instruction: String, active: Boolean, done: Boolean = false, onClick: (() -> Unit)? = null) {
    val dimens = AbiTheme.dimens
    Row(
        verticalAlignment = Alignment.Top,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.components.cornerRadius))
                .let { if (onClick != null) it.clickable { onClick() } else it }
                .padding(vertical = 6.dp),
    ) {
        LevelBadge(number.toString(), locked = !active, mastered = done, attention = false, highlighted = active && !done, size = dimens.components.button * 0.5f)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Pill("AFB $afb", color = if (active) accent else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                instruction,
                fontSize = dimens.fonts.body,
                lineHeight = dimens.fonts.body * 1.35f,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (active) 1f else 0.45f),
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
        }
    }
}

/** The live part: microphone, timer, tasks, corrections and transcript. */
@Composable
fun LiveScreen(vm: AppViewModel) {
    val conversation = vm.live
    var confirmCancel by remember { mutableStateOf(false) }
    BackHandler { confirmCancel = true }
    if (confirmCancel) {
        PopUp(
            title = "Sitzung abbrechen?",
            description = "Es wird nichts gespeichert und nicht ausgewertet.",
            confirmText = "Abbrechen",
            onConfirm = { confirmCancel = false; vm.cancelLive() },
            onDismiss = { confirmCancel = false },
            cancelText = "Weitermachen",
            confirmColor = red,
        )
    }
    if (conversation == null) {
        DetailScaffold(title = "Verbinde …", onBack = { vm.cancelLive() }) { LoadingCard("Starte die Sitzung …") }
        return
    }
    val state by conversation.state.collectAsState()
    val draft = vm.draft
    val task = vm.task
    val dimens = AbiTheme.dimens
    val title =
        when (state.part) {
            1 -> if ((draft?.focusTask ?: 0) in 1..3) "Aufgabe ${draft?.focusTask}" else "Teil 1 – Vortrag"
            2 -> "Teil 2 – Gespräch"
            else -> "Gesprächstraining"
        }
    val toPart2 = state.part == 1 && draft?.kind?.hasPart2 == true
    DetailScaffold(
        title = title,
        onBack = { confirmCancel = true },
        backIcon = R.drawable.close_24dp,
        tag = "live",
        bottom = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    StartButton(
                        text = vm.busy ?: if (toPart2) "Weiter zu Teil 2" else "Auswerten",
                        onClick = { if (toPart2) vm.finishPart1() else vm.finishSession() },
                        enabled = vm.busy == null,
                        icon = if (toPart2) R.drawable.arrow_forward_24dp else R.drawable.stop_24dp,
                        iconTint = if (toPart2) green else red,
                        modifier = Modifier.testTag("live_finish"),
                    )
                }
            }
        },
    ) {
        AbiCard(modifier = Modifier.testTag("live_status")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicPulse(level = state.micLevel, examinerSpeaking = state.examinerSpeaking)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.status, fontSize = dimens.fonts.label, lineHeight = dimens.fonts.label * 1.2f, color = MaterialTheme.colorScheme.onPrimary)
                    if (state.hasExaminer && state.examinerLabel.isNotBlank()) CaptionText("Prüfer: ${state.examinerLabel}")
                    if (!state.transcriberReady) CaptionText("Mitschrift startet …")
                }
            }
            Gap(12.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mmss(state.elapsedSec), fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                ProgressLine(
                    fraction = state.elapsedSec.toFloat() / state.targetSec.coerceAtLeast(1),
                    modifier = Modifier.weight(1f),
                    color = if (state.elapsedSec > state.targetSec + 60) red else if (state.elapsedSec > state.targetSec) yellow else accent,
                )
                Spacer(Modifier.width(10.dp))
                CaptionText("Ziel " + mmss(state.targetSec))
            }
        }
        state.error?.let { Banner(it, red, icon = R.drawable.error_24dp) }
        state.info?.let { Banner(it, yellow) }
        if (state.part == 1 && task != null) {
            AbiCard {
                CardTitle("Deine Aufgaben", icon = R.drawable.assignment_24dp)
                task.tasks.forEachIndexed { i, t ->
                    val number = i + 1
                    val active = draft?.focusTask == 0 || draft?.focusTask == number
                    TaskLine(number, t.afb, t.instruction, active, done = number in vm.tasksDone) {
                        vm.tap()
                        if (number in vm.tasksDone) vm.tasksDone.remove(number) else vm.tasksDone.add(number)
                    }
                }
                CaptionText("Tippe eine Aufgabe an, wenn du sie erledigt hast.")
            }
            if (vm.notes.isNotBlank()) {
                ExpandableCard("Deine Notizen", icon = R.drawable.edit_note_24dp, initiallyOpen = true) {
                    Text(vm.notes, fontSize = dimens.fonts.reading, lineHeight = dimens.fonts.reading * 1.4f, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        if (draft?.feedback != FeedbackMode.PRUEFUNG) {
            AlertCard(state.lastAlert, state.liveErrors.size, draft?.feedback ?: FeedbackMode.FLUESTERN)
        }
        if (state.part != 1 || vm.app.settings.showTranscript) {
            TranscriptCard(state.utterances, state.interim, state.examinerLive)
        }
    }
}

@Composable
private fun MicPulse(level: Float, examinerSpeaking: Boolean) {
    val dimens = AbiTheme.dimens
    val scale by animateFloatAsState(targetValue = 1f + level.coerceIn(0f, 1f) * 0.55f, animationSpec = tween(120), label = "mic")
    val color = if (examinerSpeaking) green else accent
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(dimens.components.button * 1.1f)) {
        Canvas(Modifier.size(dimens.components.button * 1.1f)) {
            val r = size.minDimension / 2.6f
            drawCircle(color.copy(alpha = 0.18f), radius = r * scale)
            drawCircle(color.copy(alpha = 0.35f), radius = r * (0.6f + 0.4f * scale))
            drawCircle(color, radius = r * 0.62f)
        }
        Icon(
            painterResource(if (examinerSpeaking) R.drawable.record_voice_over_24dp else R.drawable.mic_24dp),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(dimens.icons.menuItem * 0.9f),
        )
    }
}

@Composable
private fun AlertCard(alert: de.abicoach.data.LiveError?, count: Int, mode: FeedbackMode) {
    val dimens = AbiTheme.dimens
    AbiCard(border = if (alert != null) yellow.copy(alpha = 0.6f) else null, modifier = Modifier.testTag("live_alerts")) {
        CardTitle(if (mode == FeedbackMode.COACH) "Coach" else "Flüstern", icon = R.drawable.hearing_24dp, iconTint = yellow)
        if (alert == null) {
            CaptionText(if (mode == FeedbackMode.COACH) "Bei wichtigen Fehlern unterbricht dich der Prüfer." else "Fehler erscheinen hier, das Handy vibriert kurz.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("„${alert.quote}“", fontSize = dimens.fonts.body, color = red, textDecoration = TextDecoration.LineThrough, modifier = Modifier.weight(1f, fill = false))
            }
            Text("→ „${alert.correction}“", fontSize = dimens.fonts.label, color = green)
            CaptionText(alert.explanation + " · " + Knowledge.categoryLabel(alert.category))
            if (count > 1) CaptionText("$count Hinweise bisher – alle kommen in die Auswertung.")
        }
    }
}

@Composable
private fun TranscriptCard(utterances: List<Utterance>, interim: String, examinerLive: String) {
    AbiCard(modifier = Modifier.testTag("live_transcript")) {
        CardTitle("Mitschrift", icon = R.drawable.text_snippet_24dp)
        if (utterances.isEmpty() && interim.isBlank() && examinerLive.isBlank()) CaptionText("Sobald gesprochen wird, erscheint hier der Text.")
        utterances.takeLast(10).forEach { u -> Bubble(u.speaker == Speaker.STUDENT, u.text) }
        if (examinerLive.isNotBlank()) Bubble(false, examinerLive, live = true)
        if (interim.isNotBlank()) Bubble(true, interim, live = true)
    }
}

@Composable
private fun Bubble(student: Boolean, text: String, live: Boolean = false) {
    val dimens = AbiTheme.dimens
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (student) Arrangement.End else Arrangement.Start) {
        Text(
            text,
            fontSize = dimens.fonts.body * 0.92f,
            lineHeight = dimens.fonts.body * 1.3f,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (live) 0.7f else 1f),
            fontStyle = if (live) FontStyle.Italic else null,
            modifier =
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(if (student) RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp) else RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                    .background(if (student) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** While the AI evaluates the session. */
@Composable
fun AnalyzingScreen(vm: AppViewModel) {
    val tips =
        remember {
            listOf(
                "Inhalt zählt 40 %, Sprache und Darstellung 60 %. Flüssig und frei sprechen bringt viel.",
                "Im Gespräch sollst du auch selbst Impulse setzen – nicht nur antworten.",
                "Beispiele aus dem Unterricht machen eine Stellungnahme stark.",
                "Wer bei einer Frage hängt, darf nachfragen: „Could you rephrase the question, please?“",
            ).shuffled()
        }
    DetailScaffold(title = "Auswertung", onBack = { vm.tap(); vm.nav.pop() }, tag = "analyzing") {
        val error = vm.errorMessage
        if (error != null) {
            Banner(error, red, icon = R.drawable.error_24dp)
            vm.analyzingId?.let { id ->
                ActionButton("Erneut versuchen", R.drawable.refresh_24dp, accent, { vm.tap(); vm.analyze(id) }, modifier = Modifier.testTag("analyze_retry"))
            }
        } else {
            LoadingCard(
                vm.progressText.ifBlank { "Bitte warten …" },
                detail = "Die KI hört deine Aufnahme an und bewertet nach dem NRW-Raster. Meist 30–90 Sekunden.",
            )
        }
        AbiCard {
            CardTitle("Gut zu wissen", icon = R.drawable.lightbulb_24dp, iconTint = yellow)
            tips.take(2).forEach { BulletText(it) }
        }
    }
}
