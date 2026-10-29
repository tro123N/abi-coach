package de.abicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.abicoach.audio.Tts
import de.abicoach.data.Debrief
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(vm: AppViewModel) {
    val sessions by vm.app.store.sessions.collectAsStateWithLifecycle()
    ScreenFrame("AbiCoach Englisch") {
        Text(
            "Mündliche Abiprüfung NRW · 4. Fach",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        vm.daysUntilExam()?.let { days ->
            Text("Noch $days Tage bis zur Prüfung", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (!vm.hasKey) {
            InfoCard(
                title = "Einmalig: kostenlosen Gemini-Schlüssel eintragen",
                body = "Auf aistudio.google.com mit deinem Google-Konto anmelden, „Get API key“ wählen, den Schlüssel kopieren und in den Einstellungen einfügen.",
            ) {
                Button(onClick = { vm.nav.push(Screen.Settings) }) { Text("Zu den Einstellungen") }
            }
        }
        vm.errorMessage?.let { ErrorText(it) }
        ActionButton("Prüfungssimulation", "Neues Material, Vorbereitung mit Timer, Vortrag und Prüfungsgespräch – wie im Abi") {
            vm.errorMessage = null
            vm.nav.push(Screen.SimSetup)
        }
        ActionButton("Gesprächstraining", "Frei sprechen zu einem Abi-Thema – mit Korrekturen nach Wahl") {
            vm.errorMessage = null
            vm.nav.push(Screen.TrainingSetup)
        }
        ActionButton("Verlauf", "${sessions.size} Sitzungen mit Punkten und Nachbesprechung") { vm.nav.push(Screen.History) }
        ActionButton("Mein Fehlerprofil", "Deine häufigsten Fehler – und gezielt daran üben") { vm.nav.push(Screen.Profile) }
        OutlinedButton(onClick = { vm.nav.push(Screen.Settings) }, modifier = Modifier.fillMaxWidth()) { Text("Einstellungen") }
        sessions.firstOrNull { it.debrief != null }?.debrief?.let { last ->
            Text(
                "Zuletzt: ${last.totalPoints} Punkte (${gradeLabel(last.totalPoints)}, geschätzt)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- Setup screens

@Composable
fun SimSetupScreen(vm: AppViewModel) {
    var topic by rememberSaveable { mutableStateOf(Knowledge.RANDOM) }
    var textType by rememberSaveable { mutableStateOf(Knowledge.TEXT_TYPES.first()) }
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var mode by rememberSaveable { mutableStateOf(FeedbackMode.PRUEFUNG) }
    ScreenFrame("Prüfungssimulation", onBack = { vm.nav.pop() }) {
        Text(
            "Ablauf: neues Material → $minutes Min. Vorbereitung → Teil 1 Vortrag (ca. 10 Min.) → " +
                "Teil 2 Prüfungsgespräch (ca. 10 Min.) → Nachbesprechung mit Punkteschätzung.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SectionTitle("Themenbereich")
        (listOf(Knowledge.RANDOM) + Knowledge.TOPIC_AREAS).forEach { t -> OptionRow(t, null, topic == t) { topic = t } }
        SectionTitle("Textsorte")
        Knowledge.TEXT_TYPES.forEach { t -> OptionRow(t, null, textType == t) { textType = t } }
        SectionTitle("Vorbereitungszeit")
        listOf(30, 15, 5).forEach { m ->
            OptionRow(if (m == 30) "30 Minuten (wie im Abi)" else "$m Minuten (zum Üben)", null, minutes == m) { minutes = m }
        }
        SectionTitle("Rückmeldung während der Prüfung")
        FeedbackMode.entries.forEach { f -> OptionRow(f.label, f.description, mode == f) { mode = f } }
        vm.errorMessage?.let { ErrorText(it) }
        val busy = vm.busy
        if (busy != null) {
            BusyRow(busy)
        } else {
            Button(onClick = { vm.startSimulation(topic, textType, minutes, mode) }, modifier = Modifier.fillMaxWidth()) {
                Text("Material erstellen und Vorbereitung starten")
            }
        }
    }
}

@Composable
fun TrainingSetupScreen(vm: AppViewModel, withMic: (() -> Unit) -> Unit) {
    var topic by rememberSaveable { mutableStateOf(Knowledge.RANDOM) }
    var mode by rememberSaveable { mutableStateOf(FeedbackMode.COACH) }
    ScreenFrame("Gesprächstraining", onBack = { vm.nav.pop() }) {
        Text(
            "Ein Gespräch wie im zweiten Prüfungsteil. Du kannst jederzeit beenden – danach kommt die Nachbesprechung.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SectionTitle("Thema")
        (listOf(Knowledge.RANDOM) + Knowledge.TOPIC_AREAS).forEach { t -> OptionRow(t, null, topic == t) { topic = t } }
        SectionTitle("Rückmeldung")
        FeedbackMode.entries.forEach { f -> OptionRow(f.label, f.description, mode == f) { mode = f } }
        vm.errorMessage?.let { ErrorText(it) }
        Button(onClick = { withMic { vm.startTraining(topic, mode) } }, modifier = Modifier.fillMaxWidth()) {
            Text("Training starten")
        }
    }
}

// ---------------------------------------------------------------- Preparation

@Composable
fun PrepScreen(vm: AppViewModel, withMic: (() -> Unit) -> Unit) {
    val task = vm.task
    if (task == null) {
        ScreenFrame("Vorbereitung", onBack = { vm.nav.pop() }) { Text("Kein Material vorhanden.") }
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val remaining = ((vm.prepEndsAt - now) / 1000).coerceAtLeast(0)
    ScreenFrame("Vorbereitung", onBack = { vm.nav.pop() }) {
        Text(
            if (remaining > 0) "Noch ${formatSeconds(remaining)}" else "Zeit abgelaufen – starte deinen Vortrag",
            style = MaterialTheme.typography.headlineMedium,
            color = if (remaining > 60) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(
            "Im Abi darfst du ein- und zweisprachige Wörterbücher nutzen. Notiere nur Stichpunkte – Ablesen ist nicht erlaubt.",
            style = MaterialTheme.typography.bodySmall,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${task.textType} · ${task.sourceNote}", style = MaterialTheme.typography.bodySmall)
                Text(task.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        SectionTitle("Aufgaben")
        task.tasks.forEachIndexed { i, t ->
            Text("${i + 1}. ${t.instruction}", style = MaterialTheme.typography.bodyLarge)
            Text("Anforderungsbereich ${t.afb} · Operator: ${t.operator}", style = MaterialTheme.typography.bodySmall)
        }
        SectionTitle("Deine Stichpunkte")
        OutlinedTextField(
            value = vm.notes,
            onValueChange = { vm.notes = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            placeholder = { Text("• intro: text type, author, topic\n• task 1: …\n• task 2: …\n• my opinion: …") },
        )
        if (vm.notes.lines().any { it.trim().split(Regex("\\s+")).size > 8 }) {
            ErrorText("Tipp: Halte die Notizen kurz – ganze Sätze verleiten zum Ablesen.")
        }
        Button(onClick = { withMic { vm.startPart1() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Teil 1 starten: Vortrag")
        }
    }
}

// ---------------------------------------------------------------- Live

@Composable
fun LiveScreen(vm: AppViewModel, onCancel: () -> Unit) {
    val conversation = vm.live
    if (conversation == null) {
        ScreenFrame("Sitzung") { BusyRow(vm.busy ?: "Einen Moment …") }
        return
    }
    val state by conversation.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var showTranscript by remember { mutableStateOf(vm.app.settings.showTranscript || vm.feedbackMode != FeedbackMode.PRUEFUNG) }
    var showNotes by remember { mutableStateOf(true) }
    val title =
        when (state.part) {
            1 -> "Teil 1 · Vortrag"
            2 -> "Teil 2 · Prüfungsgespräch"
            else -> "Gesprächstraining"
        }
    ScreenFrame(title) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatSeconds(state.elapsedSec.toLong()), style = MaterialTheme.typography.displaySmall)
            Text("  von ca. 10:00", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(state.status, style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { state.micLevel }, modifier = Modifier.fillMaxWidth())
        state.error?.let { ErrorText(it) }
        state.info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.part != 0 && state.elapsedSec >= 600) {
            Text("Die 10 Minuten sind erreicht – du kannst diesen Teil beenden.", color = MaterialTheme.colorScheme.primary)
        }
        state.lastAlert?.let { ErrorCard(it, highlight = true) }

        val task = vm.task
        if (state.part in 1..2 && task != null) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Material und Notizen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showNotes = !showNotes }) { Text(if (showNotes) "ausblenden" else "zeigen") }
                    }
                    if (showNotes) {
                        Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        task.tasks.forEachIndexed { i, t -> Text("${i + 1}. ${t.instruction}", style = MaterialTheme.typography.bodySmall) }
                        if (vm.notes.isNotBlank()) {
                            HorizontalDivider()
                            Text(vm.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        SwitchRow("Mitschrift anzeigen", showTranscript) { showTranscript = it }
        if (showTranscript) {
            state.utterances.takeLast(40).forEach { UtteranceRow(it) }
            if (state.examinerLive.isNotBlank()) Text("Prüfer: ${state.examinerLive}", style = MaterialTheme.typography.bodyMedium)
            if (state.interim.isNotBlank()) InterimText(state.interim)
        }

        val busy = vm.busy
        if (busy != null) {
            BusyRow(busy)
        } else {
            if (state.part == 1) {
                Button(onClick = { vm.finishPart1() }, modifier = Modifier.fillMaxWidth()) { Text("Vortrag beendet → weiter zu Teil 2") }
            } else {
                Button(onClick = { vm.finishSession() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.part == 2) "Prüfung beenden und auswerten" else "Beenden und auswerten")
                }
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Abbrechen") }
        }
        Text(
            "Tipp: Mit Kopfhörern klappt es am besten – dann hört die App nur dich.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- Analysis

@Composable
fun AnalyzingScreen(vm: AppViewModel) {
    ScreenFrame("Auswertung", onBack = { vm.nav.resetTo(Screen.Home) }) {
        val busy = vm.busy
        if (busy != null) BusyRow(busy)
        vm.errorMessage?.let { message ->
            ErrorText(message)
            vm.analyzingId?.let { id ->
                Button(onClick = { vm.analyze(id) }) { Text("Nochmal versuchen") }
            }
        }
        Text(
            "Die Auswertung dauert meist unter einer Minute. Die Sitzung ist gespeichert – du findest sie auch im Verlauf.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun DebriefScreen(vm: AppViewModel, id: String) {
    val sessions by vm.app.store.sessions.collectAsStateWithLifecycle()
    val record = sessions.firstOrNull { it.id == id }
    val context = LocalContext.current
    val tts = remember { Tts(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    var showTranscript by remember { mutableStateOf(false) }
    ScreenFrame("Nachbesprechung", onBack = { vm.nav.pop() }) {
        if (record == null) {
            Text("Sitzung nicht gefunden.")
            return@ScreenFrame
        }
        Text("${record.kind.label} · ${formatDate(record.startedAt)}", style = MaterialTheme.typography.bodySmall)
        Text(record.task?.title ?: record.topicArea, style = MaterialTheme.typography.titleMedium)
        val d = record.debrief
        if (d == null) {
            Text("Diese Sitzung ist noch nicht ausgewertet.")
            Button(onClick = { vm.openAnalysis(record.id) }) { Text("Jetzt auswerten") }
            return@ScreenFrame
        }
        PointsCard(d)

        SectionTitle("Das bringt dir die meisten Punkte")
        Bullets(d.levers)

        if (d.strengths.isNotEmpty()) {
            SectionTitle("Das war schon stark")
            Bullets(d.strengths)
        }

        SectionTitle("Fehler (${d.errors.size})")
        if (d.errors.isEmpty()) Text("Keine belegten Fehler gefunden.")
        d.errors.forEach { ErrorCard(it) }
        if (d.droppedUnverified > 0) {
            Text(
                "${d.droppedUnverified} Korrektur(en) ohne Beleg im Transkript wurden verworfen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (d.upgrades.isNotEmpty()) {
            SectionTitle("So klingt die 15-Punkte-Version")
            d.upgrades.forEach { u ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Du: ${u.original}", style = MaterialTheme.typography.bodySmall)
                        Text(u.upgraded, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (u.why.isNotBlank()) Text(u.why, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { tts.say(u.upgraded) }) { Text("▶ Vorlesen und nachsprechen") }
                    }
                }
            }
        }

        SectionTitle("Aussprache")
        when {
            !d.audioAnalysed -> Text("Ohne Aufnahme keine Aussprache-Analyse (Einstellungen → Aufnahme senden).", style = MaterialTheme.typography.bodySmall)
            d.pronunciation.isEmpty() -> Text("Keine auffälligen Ausspracheprobleme gefunden.")
            else ->
                d.pronunciation.forEach { p ->
                    Text("${p.word}: ${p.issue}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(p.tip, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { tts.say(p.word) }) { Text("▶ ${p.word} anhören") }
                }
        }

        if (d.contentFeedback.isNotEmpty()) {
            SectionTitle("Inhalt")
            Bullets(d.contentFeedback)
        }

        if (d.phrases.isNotEmpty()) {
            SectionTitle("Redemittel zum Lernen")
            d.phrases.forEach { p ->
                Column {
                    Text(p.phrase, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("${p.meaningDe} – „${p.example}“", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SwitchRow("Mitschrift anzeigen", showTranscript) { showTranscript = it }
        if (showTranscript) record.utterances.forEach { UtteranceRow(it) }

        Button(onClick = { vm.nav.resetTo(Screen.Home) }, modifier = Modifier.fillMaxWidth()) { Text("Zur Startseite") }
    }
}

@Composable
private fun PointsCard(d: Debrief) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${d.totalPoints} Punkte", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("${gradeLabel(d.totalPoints)} – geschätzt nach dem NRW-Raster", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text("Inhalt (40 %): ${d.inhaltPoints} / 15", style = MaterialTheme.typography.titleSmall)
            Text(d.inhaltReason, style = MaterialTheme.typography.bodySmall)
            Text("Darstellung/Sprache (60 %): ${d.darstellungPoints} / 15", style = MaterialTheme.typography.titleSmall)
            Text(d.darstellungReason, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------- History and profile

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val sessions by vm.app.store.sessions.collectAsStateWithLifecycle()
    ScreenFrame("Verlauf", onBack = { vm.nav.pop() }) {
        val scored = sessions.mapNotNull { it.debrief?.totalPoints }.reversed()
        if (scored.size >= 2) {
            Text("Punkteverlauf: ${scored.takeLast(8).joinToString(" → ")}", style = MaterialTheme.typography.titleSmall)
        }
        if (sessions.isEmpty()) Text("Noch keine Sitzungen.")
        sessions.forEach { s ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${formatDate(s.startedAt)} · ${s.kind.label}", style = MaterialTheme.typography.labelMedium)
                    Text(s.task?.title ?: s.topicArea, style = MaterialTheme.typography.titleSmall)
                    Text(
                        s.debrief?.let { "${it.totalPoints} Punkte (${gradeLabel(it.totalPoints)}, geschätzt)" } ?: "noch nicht ausgewertet",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row {
                        TextButton(onClick = { vm.nav.push(Screen.Debrief(s.id)) }) { Text("Öffnen") }
                        TextButton(onClick = { vm.app.store.delete(s.id) }) { Text("Löschen") }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel, withMic: (() -> Unit) -> Unit) {
    val sessions by vm.app.store.sessions.collectAsStateWithLifecycle()
    val counts = remember(sessions) { vm.app.store.categoryCounts().entries.sortedByDescending { it.value } }
    ScreenFrame("Mein Fehlerprofil", onBack = { vm.nav.pop() }) {
        if (counts.isEmpty()) {
            Text("Noch keine Fehler gesammelt. Starte eine Simulation oder ein Training.")
            return@ScreenFrame
        }
        Text("So oft kamen die Fehlerarten in allen ausgewerteten Sitzungen vor:", style = MaterialTheme.typography.bodyMedium)
        val max = counts.first().value.toFloat()
        counts.forEach { (category, count) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(Knowledge.categoryLabel(category), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("$count×", style = MaterialTheme.typography.titleSmall)
                }
                LinearProgressIndicator(progress = { count / max }, modifier = Modifier.fillMaxWidth())
                vm.app.store.examples(category, 2).forEach { e ->
                    Text("„${e.quote}“ → „${e.correction}“", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val top = counts.take(3).map { it.key }
        Button(
            onClick = { withMic { vm.startTraining(Knowledge.RANDOM, FeedbackMode.COACH, top) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Gezielt üben (Coach-Modus)") }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings = vm.app.settings
    var key by remember { mutableStateOf(settings.geminiKey) }
    var examDate by remember { mutableStateOf(settings.examDate) }
    var course by remember { mutableStateOf(settings.courseTopics) }
    var voice by remember { mutableStateOf(settings.voice) }
    var liveModel by remember { mutableStateOf(settings.liveModel) }
    var transcribeModel by remember { mutableStateOf(settings.transcribeModel) }
    var analysisModel by remember { mutableStateOf(settings.analysisModel) }
    var upload by remember { mutableStateOf(settings.uploadAudio) }
    var transcriptDuringExam by remember { mutableStateOf(settings.showTranscript) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save() {
        settings.geminiKey = key
        settings.examDate = examDate
        settings.courseTopics = course
        settings.voice = voice
        settings.liveModel = liveModel
        settings.transcribeModel = transcribeModel
        settings.analysisModel = analysisModel
        settings.uploadAudio = upload
        settings.showTranscript = transcriptDuringExam
        vm.refreshKey()
    }

    ScreenFrame("Einstellungen", onBack = {
        save()
        vm.nav.pop()
    }) {
        SectionTitle("Gemini-API-Schlüssel")
        Text(
            "Kostenlos auf aistudio.google.com → „Get API key“. Der Schlüssel wird verschlüsselt auf deinem Handy gespeichert und nur an Google gesendet.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim() },
            label = { Text("API-Schlüssel") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            save()
            checkResult = "Prüfe den Schlüssel …"
            scope.launch { checkResult = vm.app.rest.checkKey() ?: "Der Schlüssel funktioniert." }
        }) { Text("Speichern und testen") }
        checkResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        SectionTitle("Deine Prüfung")
        OutlinedTextField(
            value = examDate,
            onValueChange = { examDate = it },
            label = { Text("Prüfungsdatum (JJJJ-MM-TT)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = course,
            onValueChange = { course = it },
            label = { Text("Themen und Lektüren aus deinem Kurs") },
            placeholder = { Text("z. B. Brexit, Purple Hibiscus, The Hate U Give, Fake News, Globalisation") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Mit Kommas trennen. Der Prüfer fragt dann auch dazu, und die Mitschrift erkennt die Namen besser.",
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("Stimme des Prüfers")
        Settings.VOICES.forEach { v -> OptionRow(v, null, voice == v) { voice = v } }

        SectionTitle("Auswertung")
        SwitchRow("Aufnahme für die Aussprache-Analyse an Google senden", upload) { upload = it }
        SwitchRow("Mitschrift auch im Prüfungsmodus anzeigen", transcriptDuringExam) { transcriptDuringExam = it }

        SectionTitle("Datenschutz")
        Text(
            "Im kostenlosen Gemini-Tarif darf Google deine Eingaben (Text und Audio) zur Verbesserung seiner Produkte nutzen. " +
                "Wer das nicht möchte, aktiviert in Google AI Studio die Abrechnung (bezahlter Tarif, wenige Cent pro Sitzung).",
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("Modelle (für Fortgeschrittene)")
        OutlinedTextField(value = liveModel, onValueChange = { liveModel = it }, label = { Text("Prüfer (Live)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = transcribeModel, onValueChange = { transcribeModel = it }, label = { Text("Mitschrift (Live)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = analysisModel, onValueChange = { analysisModel = it }, label = { Text("Auswertung und Material") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Leer lassen setzt den Standard zurück.", style = MaterialTheme.typography.bodySmall)

        Button(onClick = {
            save()
            vm.nav.pop()
        }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") }
    }
}
