package de.abicoach.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.abicoach.R
import de.abicoach.audio.FeedbackType
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.Phrases
import de.abicoach.data.SessionKind
import de.abicoach.data.Speaker
import de.abicoach.data.Utterance
import de.abicoach.session.LiveUiState
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
import de.abicoach.ui.components.MultiChoiceChips
import de.abicoach.ui.components.ExpandableCard
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.Tab
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
        SessionKind.TRAINING ->
            if (s.isSprint) "3 Minuten so viel wie möglich sprechen: Der Prüfer stellt kurze, fordernde Fragen und hakt nach – das trainiert flüssiges, längeres Antworten." else "Ein freies Gespräch zu einem Themenfeld – ideal, um mit Flüstern oder Coach an deinen Fehlern zu arbeiten."
    }

private fun setupTitle(s: SessionSetup): String =
    when {
        s.kind == SessionKind.PART1 && s.focusTask in 1..3 -> "Aufgabe ${s.focusTask}: " + Knowledge.TASK_PATTERNS[s.focusTask - 1].germanName
        s.isSprint -> "Gesprächs-Sprint"
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
    val hasRealTasks = remember { de.abicoach.memory.ExamUnits.all(app).isNotEmpty() }
    val sources = Knowledge.Source.entries.filter { it != Knowledge.Source.REAL || hasRealTasks || s.source == Knowledge.Source.REAL }

    DetailScaffold(
        title = setupTitle(s),
        onBack = { vm.tap(); vm.nav.pop() },
        tag = "setup",
        bottom = {
            Surface(color = androidx.compose.ui.graphics.Color.Transparent) {
                Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    StartButton("Los geht's", onClick = start, modifier = Modifier.testTag("setup_start"))
                }
            }
        },
    ) {
        AreaIntro(if (s.isSprint) R.drawable.speed_24dp else de.abicoach.ui.screens.kindIcon(s.kind), if (s.isSprint) "Gesprächs-Sprint" else s.kind.label, kindDescription(s))
        MessageBanners(vm)
        de.abicoach.profile.Skill.byKey(s.focusSkill)?.let { skill ->
            Banner("Kompetenz-Training: ${skill.label} – Ziel und Auswertung schauen genau darauf.", accent, icon = R.drawable.target_24dp)
        }
        AbiCard {
            CardTitle("Topic field", icon = R.drawable.hub_24dp)
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
            Gap(6.dp)
            CaptionText(
                when (s.topicChoice) {
                    Knowledge.FROM_COURSE -> "Die App wählt ein Themenfeld aus deinem Kurs, das du am nötigsten hast – schwach, unsicher oder noch nie geübt."
                    Knowledge.RANDOM -> "Irgendein Themenfeld der Abiturvorgaben."
                    else -> Knowledge.topic(s.topicChoice)?.english.orEmpty()
                },
            )
            if (!hasMaterial) {
                Gap(8.dp)
                CaptionText("Tipp: Lade unter „Kurs“ dein Material hoch – dann passen Texte und Fragen genau zu eurem Unterricht.")
            }
        }
        if ((s.kind.hasPart2 || s.kind == SessionKind.TRAINING) && s.kind != SessionKind.PLACEMENT && !s.isSprint) {
            val forExam = s.kind.hasPart2
            val main = s.topicChoice.takeIf { it in 0..13 }
            val others = Knowledge.TOPICS.map { it.id }.filter { it != main }
            val picked = s.extraTopics.filter { it != main }.toSet()
            val terms = remember { de.abicoach.memory.HalfYears.mapping(app) }
            AbiCard(modifier = Modifier.testTag("setup_more_topics")) {
                CardTitle(if (forExam) "Teil 2 – Themenfelder" else "Weitere Themenfelder", icon = R.drawable.hub_24dp)
                CaptionText(
                    if (forExam) {
                        "Im Abitur darf Teil 2 nicht bei einem Kurshalbjahr bleiben – meist verbinden die Prüfer zwei Themenfelder und fragen nach Zusammenhängen. " +
                            "Wähle bis zu ${AppViewModel.MAX_EXTRA_TOPICS}, die zusammen geprüft werden. Ohne Auswahl nimmt die App die, die du am nötigsten hast."
                    } else {
                        "Optional: bis zu ${AppViewModel.MAX_EXTRA_TOPICS} weitere Themenfelder – das Gespräch verbindet sie, wie im Abitur."
                    },
                )
                Gap(8.dp)
                MultiChoiceChips(
                    options = others,
                    selected = picked,
                    label = { t -> Knowledge.topicShort(t) + (terms[t]?.let { " · $it" } ?: "") },
                    onToggle = { id ->
                        vm.tap()
                        s = s.copy(extraTopics = if (id in picked) (picked - id).toList() else (picked + id).toList())
                    },
                    canAdd = picked.size < AppViewModel.MAX_EXTRA_TOPICS,
                    tagPrefix = "moretopic",
                )
                Gap(6.dp)
                val mainTerm = main?.let { terms[it] }
                when {
                    terms.isEmpty() ->
                        CaptionText(
                            "Tipp: Stell unter „Halbjahre“ ein, welches Thema wann dran war – dann mischt die App die Halbjahre wie im Abitur.",
                            modifier = Modifier.clickable { vm.tap(); vm.nav.push(de.abicoach.ui.Screen.HalfYears) },
                            color = accent,
                        )
                    picked.isNotEmpty() && mainTerm != null && picked.all { terms[it] == mainTerm } ->
                        CaptionText("Achtung: Alle gewählten Themen sind aus $mainTerm. Im Abitur geht Teil 2 über andere Halbjahre – nimm auch eins aus einem anderen Halbjahr.", color = yellow)
                    picked.isEmpty() && forExam ->
                        CaptionText("Ohne Auswahl nimmt die App Themen aus anderen Halbjahren als " + (mainTerm ?: "Teil 1") + ".")
                    else -> Unit
                }
                if (terms.isNotEmpty()) {
                    CaptionText(
                        "Halbjahre ändern",
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.tap(); vm.nav.push(de.abicoach.ui.Screen.HalfYears) }.padding(vertical = 4.dp),
                        color = accent,
                    )
                }
                if (picked.isNotEmpty()) {
                    Gap(6.dp)
                    val first =
                        main?.let { Knowledge.topicShort(it) } ?: when {
                            s.kind == SessionKind.SIMULATION -> "Text aus Teil 1"
                            s.topicChoice == Knowledge.RANDOM -> "zufälliges Themenfeld"
                            else -> "Themenfeld aus deinem Kurs"
                        }
                    CaptionText("Geprüft wird: " + (listOf(first) + picked.map { Knowledge.topicShort(it) }).joinToString(" → "))
                }
            }
        }
        if (s.kind.hasPart1) {
            AbiCard {
                CardTitle("Material für Teil 1", icon = R.drawable.article_24dp)
                if (s.kind != SessionKind.PLACEMENT) {
                    ChoiceChips(
                        options = sources,
                        selected = s.source,
                        label = { it.label },
                        onSelect = { vm.tap(); s = s.copy(source = it) },
                        tagPrefix = "source",
                    )
                    Gap(6.dp)
                    CaptionText(s.source.description + if (s.source != Knowledge.Source.NEW && !hasMaterial) " (Noch kein Material da – dann wird es ein neuer Text.)" else "")
                    Gap(12.dp)
                }
                if ((s.source != Knowledge.Source.MATERIAL && s.source != Knowledge.Source.REAL) || s.kind == SessionKind.PLACEMENT) {
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
        if (s.kind.hasPart2 || s.kind == SessionKind.TRAINING) {
            val style = s.talkStyle ?: app.settings.talkStyle
            AbiCard(modifier = Modifier.testTag("setup_talk_style")) {
                CardTitle("Gesprächsstil des Prüfers", icon = R.drawable.forum_24dp)
                ChoiceChips(
                    options = de.abicoach.data.TalkStyle.entries,
                    selected = style,
                    label = { it.label },
                    onSelect = { vm.tap(); s = s.copy(talkStyle = it) },
                    tagPrefix = "talkstyle",
                )
                Gap(6.dp)
                CaptionText(style.description)
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
            CardTitle("Deine KI für diese Übung: ${app.ai.providerLabel()}", icon = R.drawable.auto_awesome_24dp)
            val claude = app.ai.provider == de.abicoach.ai.Provider.ANTHROPIC
            if (s.kind.hasPart2 || !s.kind.hasPart1) {
                CaptionText("Prüfer: ${app.ai.examinerLabel()}" + if (claude) " – antwortet, wenn du fertig bist" else " – hört und spricht gleichzeitig")
            }
            CaptionText("Mitschrift: ${app.ai.transcriberLabel()}" + if (claude) "" else " (wortgetreu, mit deinen Fehlern)")
            if (s.feedback != FeedbackMode.PRUEFUNG) CaptionText("Fehlerprüfung beim Sprechen: ${app.ai.fastLabel()}")
            if (s.kind.hasPart1) CaptionText("Text und Aufgaben: ${app.ai.brainLabel()}")
            CaptionText("Bewertung: ${app.ai.brainLabel()}" + if (app.ai.hearsRecordings) " · Aufnahme anhören: ${app.ai.listenerLabel()}" else " (nach der Mitschrift)")
        }
    }
}

/** Waiting while the AI writes the material. */
@Composable
fun GeneratingScreen(vm: AppViewModel) {
    BackHandler { vm.cancelGenerating() }
    AskNotificationPermission()
    DetailScaffold(title = "Material wird erstellt", onBack = { vm.cancelGenerating() }, backIcon = R.drawable.close_24dp, tag = "generating") {
        LoadingCard(vm.progressText.ifBlank { "Einen Moment …" }, detail = "Meist 20–60 Sekunden. Die Vorbereitungszeit beginnt erst danach.")
        AbiCard {
            CardTitle("Du musst nicht warten", icon = R.drawable.notifications_active_24dp, iconTint = green)
            BodyText("Der Text entsteht im Hintergrund, auch wenn du die App verlässt oder das Handy sperrst. Du bekommst eine Nachricht, sobald er fertig ist – die Vorbereitungszeit startet erst, wenn du ihn öffnest.")
            Gap(8.dp)
            ActionButton("Weiter in der App stöbern", R.drawable.route_24dp, accent, { vm.tap(); vm.nav.openTab(Tab.WEG) }, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("generating_background"))
        }
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
    KeepScreenOn()
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
            Surface(color = androidx.compose.ui.graphics.Color.Transparent) {
                Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    StartButton(if (draft.focusTask in 1..3) "Sprechen" else "Vortrag beginnen", onClick = start, modifier = Modifier.testTag("prep_start"), icon = R.drawable.mic_24dp)
                }
            }
        },
    ) {
        if (remaining <= 0) Banner("Die Vorbereitungszeit ist um – beginne jetzt deinen Vortrag.", yellow, icon = R.drawable.timer_24dp)
        MessageBanners(vm)
        if (draft.retryOf.isNotBlank()) {
            val (review, errors) = remember(draft.id) { vm.retryHints() }
            AbiCard(modifier = Modifier.testTag("prep_retry")) {
                CardTitle("Zweiter Versuch", icon = R.drawable.refresh_24dp, iconTint = accent)
                review?.let { r ->
                    CaptionText("Beim ersten Mal: ${r.points} Punkte für Aufgabe ${draft.focusTask}.")
                    if (r.missing.isNotEmpty()) {
                        Gap(6.dp)
                        BodyText("Das hat gefehlt:")
                        r.missing.take(4).forEach { BulletText(it) }
                    }
                    if (r.tip.isNotBlank()) {
                        Gap(6.dp)
                        CaptionText("Tipp: ${r.tip}")
                    }
                }
                if (errors.isNotEmpty()) {
                    Gap(8.dp)
                    BodyText("Achte auf diese Fehler:")
                    errors.forEach { BulletText("„${it.quote}“ → „${it.correction}“") }
                }
            }
        }
        GoalsCard(draft.goals)
        AbiCard(modifier = Modifier.testTag("prep_text")) {
            PillRow(listOfNotNull(Knowledge.topicShort(task.topicId), task.textType.ifBlank { null }, "${task.wordCount} Wörter"))
            Gap(10.dp)
            Text(task.title, fontSize = dimens.fonts.label, lineHeight = dimens.fonts.label * 1.2f, color = MaterialTheme.colorScheme.onPrimary)
            if (task.sourceNote.isNotBlank()) CaptionText(task.sourceNote)
            if (task.adapted.isNotBlank()) CaptionText("Für dich angepasst – ${task.adapted}", color = accent)
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
                onValueChange = { vm.updateNotes(it) },
                label = "Stichpunkte",
                singleLine = false,
                minLines = 6,
                maxLines = 20,
                tag = "prep_notes",
            )
            Gap(6.dp)
            CaptionText("Nur Stichpunkte – ganze Sätze verleiten zum Ablesen, und das kostet Punkte. Deine Notizen und die Zeit bleiben gespeichert, auch wenn du die App verlässt.")
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
    KeepScreenOn()
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
        pinned = {
            val mode = draft?.feedback ?: FeedbackMode.FLUESTERN
            if (mode != FeedbackMode.PRUEFUNG) WhisperBar(state, mode)
            if (vm.liveTranscript) LiveTranscriptPanel(state)
        },
        bottom = {
            Surface(color = androidx.compose.ui.graphics.Color.Transparent) {
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
                Spacer(Modifier.width(8.dp))
                TranscriptToggle(vm.liveTranscript) { vm.toggleLiveTranscript() }
            }
            Gap(10.dp)
            TechChips(state, draft?.feedback ?: FeedbackMode.FLUESTERN)
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
        val goals = draft?.goals.orEmpty()
        if (goals.isNotEmpty()) {
            ExpandableCard("Deine Ziele", subtitle = "${goals.size} Ziele aus deinem Lernstand", icon = R.drawable.target_24dp, initiallyOpen = state.part != 1, modifier = Modifier.testTag("live_goals")) {
                goals.forEach { BulletText(it.text) }
            }
        }
        if (draft?.feedback != FeedbackMode.PRUEFUNG) {
            AlertCard(state.lastAlert, state.liveErrors.size, draft?.feedback ?: FeedbackMode.FLUESTERN, state.checkInfo, state.checkFailed)
        }
        if (!vm.liveTranscript && (state.part != 1 || vm.app.settings.showTranscript)) {
            TranscriptCard(state.utterances, state.interim, state.examinerLive)
        }
    }
}

/**
 * Pinned above the scrolling page while speaking: the newest hint in large type, readable from an arm's length away.
 * A fresh hint lights up for a few seconds; the last one stays visible (dimmed) until the next one comes.
 */
@Composable
private fun WhisperBar(state: LiveUiState, mode: FeedbackMode) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(400)
        }
    }
    val alert = state.lastAlert
    val fresh = alert != null && now - state.alertAt < 6_000
    val repeated = state.repeatOk && now - state.repeatAt < 5_000
    val container by animateColorAsState(
        targetValue =
            when {
                repeated -> green
                fresh -> yellow
                alert != null -> yellow.copy(alpha = 0.22f)
                else -> MaterialTheme.colorScheme.primary
            },
        animationSpec = tween(350),
        label = "whisper",
    )
    val ink = Color(0xFF1B1B1B)
    Column(
        Modifier
            .fillMaxWidth()
            .background(container)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("whisper_bar"),
    ) {
        if (repeated) {
            Text("✓ Richtig wiederholt – weiter so!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        } else if (alert == null) {
            Text(
                if (mode == FeedbackMode.COACH) "Coach aktiv – Hinweise erscheinen hier." else "Flüstern aktiv – Hinweise erscheinen hier.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            )
        } else {
            if (state.alertGoal) {
                Text("Dein Ziel heute – genau dieser Fehler:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (fresh) ink else accent)
            }
            Text(
                "„${alert.quote}“",
                fontSize = 17.sp,
                color = if (fresh) Color(0xFF7A1111) else red,
                textDecoration = TextDecoration.LineThrough,
            )
            Text(
                "→ „${alert.correction}“",
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
                color = if (fresh) ink else green,
            )
            if (state.repeatTarget != null && mode == FeedbackMode.COACH) {
                Text("Sag es noch einmal richtig.", fontSize = 14.sp, color = if (fresh) ink else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            }
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
private fun AlertCard(alert: de.abicoach.data.LiveError?, count: Int, mode: FeedbackMode, checkInfo: String?, checkFailed: Boolean) {
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
        if (checkInfo != null) {
            Gap(6.dp)
            CaptionText(checkInfo, color = if (checkFailed) red else null)
        }
    }
}

/** The small "Mitschrift" button in the live card: shows or hides who said what. */
@Composable
private fun TranscriptToggle(open: Boolean, onClick: () -> Unit) {
    val color = if (open) accent else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (open) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.background)
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("live_transcript_toggle"),
    ) {
        Icon(painterResource(R.drawable.chat_24dp), contentDescription = if (open) "Mitschrift ausblenden" else "Mitschrift zeigen", tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text("Mitschrift", fontSize = 12.sp, color = color, fontWeight = if (open) FontWeight.Bold else null)
    }
}

private enum class Who(val label: String) { EXAMINER("Prüfer"), COACH("Coach"), YOU("Du") }

private data class TranscriptLine(val who: Who, val text: String, val at: Long, val live: Boolean = false)

/**
 * Pinned below the hints while the small button is on: what the examiner, the coach (the app) and the student said,
 * newest at the bottom, scrolling along by itself.
 */
@Composable
private fun LiveTranscriptPanel(state: LiveUiState) {
    val lines =
        remember(state.utterances, state.appSaid, state.interim, state.examinerLive) {
            val said =
                state.utterances.map { TranscriptLine(if (it.speaker == Speaker.STUDENT) Who.YOU else Who.EXAMINER, it.text, it.atMs) } +
                    state.appSaid.map { TranscriptLine(Who.COACH, it.text, it.atMs) }
            val live =
                listOfNotNull(
                    state.examinerLive.takeIf { it.isNotBlank() }?.let { TranscriptLine(Who.EXAMINER, it, Long.MAX_VALUE - 1, live = true) },
                    state.interim.takeIf { it.isNotBlank() }?.let { TranscriptLine(Who.YOU, it, Long.MAX_VALUE, live = true) },
                )
            (said.sortedBy { it.at }.takeLast(40) + live)
        }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(lines.size, lines.lastOrNull()?.text) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("live_transcript_panel"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mitschrift", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.weight(1f))
            Who.entries.forEach { w ->
                Text(w.label, fontSize = 11.sp, color = whoColor(w), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (lines.isEmpty()) {
            CaptionText("Sobald gesprochen wird, erscheint hier, wer was gesagt hat.")
        } else {
            androidx.compose.foundation.lazy.LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp)) {
                items(lines.size) { i ->
                    val line = lines[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            line.who.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = whoColor(line.who),
                            modifier = Modifier.width(48.dp).padding(top = 2.dp),
                        )
                        Text(
                            line.text,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            fontStyle = if (line.live) FontStyle.Italic else null,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (line.live) 0.65f else 0.95f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun whoColor(w: Who): Color =
    when (w) {
        Who.EXAMINER -> green
        Who.COACH -> yellow
        Who.YOU -> accent
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
    val status by vm.app.analysis.status.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var asked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    val cloud = vm.app.settings.cloudAnalysis && vm.app.ai.canRunInCloud
    if (confirmCancel) {
        PopUp(
            title = "Auswertung abbrechen?",
            description = "Die Bewertung wird gestoppt, auch in der Cloud. Deine Aufnahme und die Mitschrift bleiben gespeichert – du kannst die Sitzung später jederzeit auswerten.",
            confirmText = "Abbrechen",
            onConfirm = {
                confirmCancel = false
                val running = status.id
                if (running != null) vm.cancelAnalysis(running) else vm.nav.pop()
            },
            onDismiss = { confirmCancel = false },
            cancelText = "Weiter auswerten",
            confirmColor = red,
        )
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    // Android 13+: the "Auswertung fertig" notification needs a one-time permission.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!asked && !de.abicoach.session.Notifications.canNotify(context)) {
            asked = true
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    DetailScaffold(title = "Auswertung", onBack = { vm.tap(); vm.nav.pop() }, tag = "analyzing") {
        val error = status.error ?: vm.errorMessage
        if (error != null) {
            Banner(error, red, icon = R.drawable.error_24dp)
            status.id?.let { id ->
                ActionButton("Erneut versuchen", R.drawable.refresh_24dp, accent, { vm.tap(); vm.analyze(id) }, modifier = Modifier.testTag("analyze_retry"))
            }
        } else {
            LoadingCard(
                status.text.ifBlank { "Bitte warten …" },
                detail = "Die KI hört deine Aufnahme an und bewertet nach dem NRW-Raster. Meist 1–2 Minuten.",
            )
            AbiCard {
                CardTitle(if (cloud) "Läuft in der Cloud weiter" else "Du musst nicht warten", icon = R.drawable.notifications_active_24dp, iconTint = green)
                BodyText(
                    if (cloud) {
                        "Die Bewertung läuft bei Google in der Cloud – auch wenn du die App schließt, das Handy sperrst oder kurz offline bist. Sobald sie fertig ist, holt die App das Ergebnis ab und meldet sich."
                    } else {
                        "Die Auswertung läuft im Hintergrund weiter, auch wenn du die App schließt oder das Handy sperrst. Sobald sie fertig ist, bekommst du eine Benachrichtigung."
                    },
                )
                Gap(8.dp)
                ActionButton("Weiter in der App stöbern", R.drawable.route_24dp, accent, { vm.tap(); vm.nav.openTab(Tab.WEG) }, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("analyze_background"))
                Gap(8.dp)
                ActionButton(
                    "Auswertung abbrechen",
                    R.drawable.close_24dp,
                    red,
                    { vm.tap(); confirmCancel = true },
                    subtitle = "Die Sitzung bleibt gespeichert – du kannst sie später auswerten.",
                    container = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("analyze_cancel"),
                )
            }
        }
        AbiCard {
            CardTitle("Gut zu wissen", icon = R.drawable.lightbulb_24dp, iconTint = yellow)
            tips.take(2).forEach { BulletText(it) }
        }
    }
}
