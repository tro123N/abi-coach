package de.abicoach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.ai.Provider
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Quality
import de.abicoach.data.Settings
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.AbiTextField
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.ChoiceChips
import de.abicoach.ui.components.ExpandableCard
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.PopUp
import de.abicoach.ui.components.ProgressLine
import de.abicoach.ui.components.SwitchRow
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.LocalDarkTheme
import de.abicoach.ui.theme.Palette
import de.abicoach.ui.theme.ThemeMode
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.palettePreview
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Keys, models, exam date, voices, design and data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val app = vm.app
    val settings = app.settings
    // Reading the version makes this screen redraw after every change of a setting.
    val version = settings.version.collectAsState().value
    val context = LocalContext.current
    var showDate by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    DetailScaffold(title = "Einstellungen", onBack = { vm.tap(); vm.nav.pop() }, tag = "settings") {
        if (version < 0) return@DetailScaffold
        MessageBanners(vm)
        AbiCard(modifier = Modifier.testTag("settings_ai")) {
            CardTitle("Deine KI", icon = R.drawable.auto_awesome_24dp)
            CaptionText("Wähle, wessen KI alles in der App macht. Zum Vergleichen kannst du jederzeit wechseln – jede Firma braucht ihren eigenen Schlüssel.")
            Gap(8.dp)
            ChoiceChips(Provider.entries, settings.provider, { it.label }, { vm.chooseProvider(it) }, tagPrefix = "provider")
            Gap(6.dp)
            CaptionText(providerNote(settings.provider))
            Gap(12.dp)
            ChoiceChips(Quality.entries, settings.quality, { it.label }, { vm.tap(); settings.quality = it }, tagPrefix = "quality")
            Gap(6.dp)
            CaptionText(settings.quality.description)
            // The key of the chosen company first; the others for comparing.
            (listOf(settings.provider) + Provider.entries.filter { it != settings.provider }).forEach { p ->
                Gap(14.dp)
                androidx.compose.runtime.key(p.id) {
                    KeyBlock(
                        vm = vm,
                        provider = p.id,
                        title = p.label,
                        role = if (p == settings.provider) "Macht gerade alles in der App" else "Zum Vergleichen – tippe oben auf „${p.label}“, um zu wechseln",
                        current = settings.keyOf(p),
                        url = p.keyUrl,
                        required = p == settings.provider,
                        onSave = { settings.setKey(p, it) },
                    )
                }
            }
            Gap(10.dp)
            CaptionText("Die Schlüssel bleiben verschlüsselt auf deinem Handy und gehen nur an die jeweilige Firma.")
        }
        SystemCheckCard(vm)
        AbiCard {
            CardTitle("Wer macht was", icon = R.drawable.hub_24dp)
            CaptionText(app.ai.providerLabel())
            Gap(4.dp)
            RoleRow("Prüfer im Gespräch", app.ai.examinerLabel())
            RoleRow("Mitschrift", app.ai.transcriberLabel())
            RoleRow("Bewertung, Aufgaben, Coach", app.ai.brainLabel())
            RoleRow("Fehlerprüfung beim Sprechen", app.ai.fastLabel())
            RoleRow("Aufnahme anhören", if (app.ai.hearsRecordings) app.ai.listenerLabel() else "– (Claude hört nicht)")
            RoleRow("Material lesen", app.ai.readerLabel())
            RoleRow("Suche im Material", app.ai.searchLabel())
            RoleRow("Vorlesen", app.ai.voiceLabel())
            Gap(4.dp)
            CaptionText("Fällt ein Modell aus (kein Zugang, Limit erreicht), springt automatisch ein anderes Modell derselben Firma ein.")
        }
        AbiCard {
            CardTitle("Prüfung", icon = R.drawable.event_24dp)
            val date = runCatching { LocalDate.parse(settings.examDate) }.getOrNull()
            ActionButton(
                date?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "Prüfungstermin wählen",
                R.drawable.event_24dp,
                accent,
                { vm.tap(); showDate = true },
                subtitle = Planner_days(vm)?.let { "noch $it Tage" } ?: "Damit die App bis zum Termin plant",
                container = MaterialTheme.colorScheme.background,
                modifier = Modifier.testTag("settings_date"),
            )
            Gap(12.dp)
            BodyText("Übungszeit pro Tag")
            Gap(6.dp)
            ChoiceChips(listOf(10, 15, 20, 30, 45), settings.dailyMinutes, { "$it Min." }, { vm.tap(); settings.dailyMinutes = it }, tagPrefix = "minutes")
            Gap(12.dp)
            BodyText("Rückmeldung beim Üben (Standard)")
            Gap(6.dp)
            ChoiceChips(FeedbackMode.entries, settings.defaultFeedback, { it.label }, { vm.tap(); settings.defaultFeedback = it }, tagPrefix = "deffeedback")
            Gap(12.dp)
            var topics by remember { mutableStateOf(settings.courseTopics) }
            AbiTextField(
                topics,
                { topics = it },
                label = "Themen, Lektüren und Filme aus dem Unterricht",
                placeholder = "z. B. The Hate U Give, Brexit, Purple Hibiscus, Black Mirror",
                singleLine = false,
                minLines = 2,
                maxLines = 5,
            )
            if (topics != settings.courseTopics) {
                Gap(6.dp)
                ActionButton("Speichern", R.drawable.check_24dp, green, { vm.tap(); settings.courseTopics = topics }, container = MaterialTheme.colorScheme.background)
            }
        }
        ReminderCard(vm)
        AbiCard {
            CardTitle("Stimmen", icon = R.drawable.record_voice_over_24dp)
            when (settings.provider) {
                Provider.GOOGLE -> {
                    BodyText("Prüfer (${app.ai.examinerLabel()})")
                    Gap(6.dp)
                    ChoiceChips(Settings.GEMINI_VOICES, settings.geminiVoice, { it }, { vm.tap(); settings.geminiVoice = it }, tagPrefix = "gvoice")
                    Gap(12.dp)
                    BodyText("Vorlesen und Coach")
                    Gap(6.dp)
                    ChoiceChips(Settings.GEMINI_VOICES, settings.ttsVoice, { it }, { vm.tap(); settings.ttsVoice = it }, tagPrefix = "ttsvoice")
                }
                Provider.OPENAI -> {
                    BodyText("Prüfer (${app.ai.examinerLabel()})")
                    Gap(6.dp)
                    ChoiceChips(Settings.OPENAI_VOICES, settings.oaVoice, { it }, { vm.tap(); settings.oaVoice = it }, tagPrefix = "ovoice")
                    Gap(12.dp)
                    BodyText("Vorlesen und Coach")
                    Gap(6.dp)
                    ChoiceChips(Settings.OPENAI_VOICES, settings.oaTtsVoice, { it }, { vm.tap(); settings.oaTtsVoice = it }, tagPrefix = "ottsvoice")
                }
                Provider.ANTHROPIC ->
                    CaptionText("Claude spricht mit der Stimme deines Handys. Welche Stimme das ist, stellst du in den Android-Einstellungen unter „Sprachausgabe“ ein (am besten Englisch, Großbritannien).")
            }
            Gap(10.dp)
            ActionButton(
                if (vm.speaking != null) "Stopp" else "Probehören",
                if (vm.speaking != null) R.drawable.stop_24dp else R.drawable.volume_up_24dp,
                accent,
                { if (vm.speaking != null) vm.stopSpeaking() else vm.speak("Good morning. Let's start with your presentation. Take your time.") },
                container = MaterialTheme.colorScheme.background,
            )
        }
        AbiCard {
            CardTitle("Aufnahme und Anzeige", icon = R.drawable.mic_24dp)
            SwitchRow(
                "Aufnahme für die Auswertung mitschicken",
                if (app.ai.hearsRecordings) {
                    "Die KI hört Aussprache und Flüssigkeit. Ohne Aufnahme wird nur das Transkript bewertet."
                } else {
                    "Claude kann keine Aufnahmen hören – mit Claude wird nur das Transkript bewertet."
                },
                settings.uploadAudio,
                { settings.uploadAudio = it },
            )
            if (settings.provider == Provider.GOOGLE) {
                SwitchRow(
                    "Auswertung in der Cloud",
                    "Die Bewertung läuft bei Google weiter, auch wenn du die App schließt oder das Handy sperrst. Dauert sie länger als 6 Minuten, wertet die App direkt aus – sie hängt nie fest.",
                    settings.cloudAnalysis,
                    { settings.cloudAnalysis = it },
                )
            }
            SwitchRow("Mitschrift beim Vortrag zeigen", "Im Gespräch wird sie immer gezeigt.", settings.showTranscript, { settings.showTranscript = it })
            SwitchRow(
                "Flüstern auch hörbar",
                "Mit Kopfhörern sagt eine leise Stimme den Hinweis. Über den Lautsprecher bleibt es bei Anzeige und Vibration.",
                settings.whisperAudio,
                { settings.whisperAudio = it },
            )
            SwitchRow(
                "Nächsten Text vorab erstellen",
                "Die App bereitet im Hintergrund einen Prüfungstext vor, damit der Start sofort geht.",
                settings.prefetchMaterial,
                { settings.prefetchMaterial = it },
            )
        }
        AbiCard(modifier = Modifier.testTag("settings_design")) {
            CardTitle("Design", icon = R.drawable.palette_24dp)
            ChoiceChips(ThemeMode.entries, settings.themeMode, { it.label }, { vm.tap(); settings.themeMode = it }, tagPrefix = "theme")
            Gap(12.dp)
            BodyText("Farbthema")
            Gap(8.dp)
            val dark = LocalDarkTheme.current
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Palette.entries.filter { dark || it != Palette.SCHWARZ }.forEach { p ->
                    val (bg, card, acc) = palettePreview(p, dark)
                    val selected = settings.palette == p
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable { vm.tap(); settings.palette = p }) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(bg)
                                    .border(if (selected) 3.dp else 1.dp, if (selected) acc else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f), CircleShape),
                        ) {
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(card))
                            Box(Modifier.size(10.dp).clip(CircleShape).background(acc))
                        }
                        Spacer(Modifier.height(4.dp))
                        CaptionText(p.label)
                    }
                }
            }
            Gap(10.dp)
            SwitchRow("Töne", null, settings.sound, { settings.sound = it })
            SwitchRow("Vibration", "Auch für Hinweise beim Sprechen (Flüstern)", settings.vibration, { settings.vibration = it })
        }
        ExpandableCard("Erweitert: Modelle", subtitle = "Nur ändern, wenn ${settings.provider.company} ein Modell umbenennt", icon = R.drawable.tune_24dp) {
            when (settings.provider) {
                Provider.GOOGLE -> {
                    ModelField("Prüfer (Live-Stimme)", settings.geminiLiveModel) { settings.geminiLiveModel = it }
                    ModelField("Gemini – Mitschrift", settings.transcribeModel) { settings.transcribeModel = it }
                    ModelField("Gemini – stärkstes Modell", settings.geminiBestModel) { settings.geminiBestModel = it }
                    ModelField("Gemini – schnelles Modell", settings.geminiTextModel) { settings.geminiTextModel = it }
                    ModelField("Gemini – Fehlerprüfung beim Sprechen", settings.geminiFastModel) { settings.geminiFastModel = it }
                    ModelField("Gemini – Material lesen", settings.geminiReaderModel) { settings.geminiReaderModel = it }
                    ModelField("Gemini – Suche (Vektoren)", settings.embeddingModel) { settings.embeddingModel = it }
                    ModelField("Gemini – Vorlesen", settings.ttsModel) { settings.ttsModel = it }
                }
                Provider.OPENAI -> {
                    ModelField("Prüfer (Live-Stimme)", settings.oaLiveModel) { settings.oaLiveModel = it }
                    ModelField("Mitschrift (live)", settings.oaTranscribeModel) { settings.oaTranscribeModel = it }
                    ModelField("Stärkstes Modell", settings.oaBrainModel) { settings.oaBrainModel = it }
                    ModelField("Normales Modell (auch Material lesen)", settings.oaTextModel) { settings.oaTextModel = it }
                    ModelField("Fehlerprüfung beim Sprechen", settings.oaFastModel) { settings.oaFastModel = it }
                    ModelField("Aufnahmen anhören (Audio)", settings.oaAudioModel) { settings.oaAudioModel = it }
                    ModelField("Aufnahmen abschreiben", settings.oaFileTranscribeModel) { settings.oaFileTranscribeModel = it }
                    ModelField("Suche (Vektoren)", settings.oaEmbeddingModel) { settings.oaEmbeddingModel = it }
                    ModelField("Vorlesen", settings.oaTtsModel) { settings.oaTtsModel = it }
                }
                Provider.ANTHROPIC -> {
                    ModelField("Stärkstes Modell", settings.clBrainModel) { settings.clBrainModel = it }
                    ModelField("Normales Modell (auch Material lesen)", settings.clTextModel) { settings.clTextModel = it }
                    ModelField("Schnelles Modell (Prüfer, Fehlerprüfung)", settings.clFastModel) { settings.clFastModel = it }
                }
            }
        }
        AbiCard {
            CardTitle("Daten", icon = R.drawable.folder_24dp)
            ActionButton("Alle Sitzungen löschen", R.drawable.delete_24dp, red, { confirm = "sessions" }, container = MaterialTheme.colorScheme.background)
            Gap(8.dp)
            ActionButton("Lernprofil zurücksetzen", R.drawable.refresh_24dp, yellow, { confirm = "profile" }, container = MaterialTheme.colorScheme.background)
            Gap(6.dp)
            CaptionText("Dein Kursmaterial verwaltest du unter „Kurs“.")
        }
        AbiCard {
            CardTitle("Über AbiCoach", icon = R.drawable.info_24dp)
            val versionName = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            BodyText("Version $versionName")
            CaptionText(
                "AbiCoach steht unter der GNU GPL v3. Design und Bauteile stammen aus Mental Math Plus (tro123N), " +
                    "das auf „Mental Math“ von HeldDerTierwelt aufbaut. Erstellt mit der KI Claude (Anthropic). " +
                    "Kein offizielles Angebot des Landes NRW.",
            )
        }
    }

    if (showDate) {
        val initial = runCatching { LocalDate.parse(settings.examDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> settings.examDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    showDate = false
                }) { Text("OK", color = accent) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Abbrechen", color = MaterialTheme.colorScheme.onPrimary) } },
        ) {
            DatePicker(state = state)
        }
    }
    confirm?.let { what ->
        PopUp(
            title = if (what == "sessions") "Alle Sitzungen löschen?" else "Lernprofil zurücksetzen?",
            description = if (what == "sessions") "Alle Auswertungen und Aufnahmen werden gelöscht." else "Prognose, Kompetenzen und das Lerner-Gedächtnis beginnen von vorn. Die Sitzungen bleiben.",
            confirmText = if (what == "sessions") "Löschen" else "Zurücksetzen",
            onConfirm = {
                if (what == "sessions") {
                    app.store.deleteAll()
                } else {
                    app.profiles.reset()
                    app.insights.reset()
                }
                confirm = null
            },
            onDismiss = { confirm = null },
            confirmColor = red,
        )
    }
}

@Suppress("FunctionName")
private fun Planner_days(vm: AppViewModel): Long? = de.abicoach.ui.Planner.daysUntilExam(vm.app)

/** What the chosen company does in the app, in plain words. */
private fun providerNote(p: Provider): String =
    when (p) {
        Provider.GOOGLE ->
            "Gemini macht alles selbst: Prüfer (hört und spricht gleichzeitig), wortgetreue Mitschrift, Bewertung, Coach, Kursgedächtnis und Vorlesen. " +
                "Die Bewertung kann in Googles Cloud weiterlaufen, wenn das Handy aus ist."
        Provider.OPENAI ->
            "OpenAI macht alles selbst: GPT-Live als Prüfer (hört und spricht gleichzeitig), wortgetreue Mitschrift, GPT bewertet und coacht, " +
                "das GPT-Audiomodell hört deine Aufnahmen an, die GPT-Stimme liest vor."
        Provider.ANTHROPIC ->
            "Claude denkt: bewertet, schreibt Texte und Aufgaben, coacht und ist dein Prüfer. Hören und sprechen kann Claude nicht – das übernehmen " +
                "Spracherkennung und Stimme deines Handys (Android 13 oder neuer). Darum gibt es mit Claude keine Aussprache-Bewertung aus der Aufnahme, " +
                "der Prüfer antwortet erst, wenn du fertig bist, und die Kurssuche geht nur über Stichwörter."
    }

@Composable
private fun KeyBlock(vm: AppViewModel, provider: String, title: String, role: String, current: String, url: String, required: Boolean, onSave: (String) -> Unit) {
    val uri = LocalUriHandler.current
    val dimens = AbiTheme.dimens
    var editing by remember(provider) { mutableStateOf(current.isBlank()) }
    var value by remember(provider) { mutableStateOf("") }
    val check = vm.keyChecks[provider]
    val checked = vm.keyChecks.containsKey(provider)
    Column(Modifier.fillMaxWidth().testTag("key_$provider")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary)
                CaptionText(role)
            }
            Text(
                if (current.isBlank()) "fehlt" else "✓ eingetragen",
                fontSize = dimens.fonts.caption,
                color = if (current.isBlank()) (if (required) red else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)) else green,
            )
        }
        Gap(6.dp)
        if (editing) {
            AbiTextField(value, { value = it }, label = "Schlüssel einfügen", secret = true, tag = "keyfield_$provider")
            Gap(6.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "Speichern",
                    R.drawable.check_24dp,
                    green,
                    {
                        vm.tap()
                        onSave(value.trim())
                        vm.keySaved(provider)
                        editing = false
                        value = ""
                        vm.checkKey(provider)
                    },
                    Modifier.weight(1f),
                    enabled = value.isNotBlank(),
                    container = MaterialTheme.colorScheme.background,
                )
                ActionButton("Schlüssel holen", R.drawable.open_in_new_24dp, accent, { runCatching { uri.openUri(url) } }, Modifier.weight(1f), container = MaterialTheme.colorScheme.background)
            }
            if (current.isNotBlank()) {
                TextButton(onClick = { editing = false; value = "" }) { Text("Abbrechen", color = MaterialTheme.colorScheme.onPrimary) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Prüfen", R.drawable.verified_24dp, accent, { vm.tap(); vm.checkKey(provider) }, Modifier.weight(1f), enabled = vm.keyChecking == null, container = MaterialTheme.colorScheme.background)
                ActionButton("Ändern", R.drawable.key_24dp, MaterialTheme.colorScheme.onPrimary, { vm.tap(); editing = true }, Modifier.weight(1f), container = MaterialTheme.colorScheme.background)
            }
            TextButton(onClick = { onSave(""); vm.keySaved(provider); editing = true }) {
                Text("Entfernen", color = red.copy(alpha = 0.8f), fontSize = dimens.fonts.caption)
            }
        }
        if (vm.keyChecking == provider) {
            ProgressLine(null)
        } else if (checked) {
            CaptionText(check ?: "Schlüssel funktioniert.", color = if (check == null) green else red)
        }
    }
}

@Composable
private fun SystemCheckCard(vm: AppViewModel) {
    val micTest = rememberMicGate { vm.tap(); vm.openMicTest() }
    MicTestDialog(vm)
    AbiCard(modifier = Modifier.testTag("settings_check")) {
        CardTitle("System prüfen", icon = R.drawable.verified_24dp)
        CaptionText(
            "Testet deine gewählte KI wirklich durch: Schlüssel, Bewertung, Stimme, Mitschrift, Prüfer und Coach. " +
                "Dauert etwa eine Minute und kostet nur ein paar Cent.",
        )
        Gap(8.dp)
        ActionButton(
            if (vm.systemChecking) "Prüfe …" else "Alles testen",
            R.drawable.verified_24dp,
            accent,
            { vm.tap(); vm.runSystemCheck() },
            enabled = !vm.systemChecking && vm.app.ai.ready,
            container = MaterialTheme.colorScheme.background,
            modifier = Modifier.testTag("check_all"),
        )
        vm.systemCheck.forEach { line ->
            Gap(6.dp)
            CheckRow(line)
        }
        Gap(10.dp)
        ActionButton(
            "Mikrofon-Test: selbst sprechen",
            R.drawable.mic_24dp,
            accent,
            { micTest() },
            enabled = vm.app.ai.ready,
            subtitle = "Du sagst einen Satz mit Fehler – prüft Mikrofon, Ohr, Hinweis und Coach-Stimme wie im echten Training",
            container = MaterialTheme.colorScheme.background,
            modifier = Modifier.testTag("check_mic"),
        )
    }
}

@Composable
private fun CheckRow(line: de.abicoach.session.CheckLine) {
    val (symbol, color) =
        when (line.state) {
            de.abicoach.session.CheckState.OK -> "✓" to green
            de.abicoach.session.CheckState.FAIL -> "✗" to red
            de.abicoach.session.CheckState.WARN -> "!" to yellow
            de.abicoach.session.CheckState.RUN -> "…" to accent
            de.abicoach.session.CheckState.WAIT -> "·" to MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
        }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(symbol, color = color, fontSize = AbiTheme.dimens.fonts.body, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            BodyText(line.title)
            if (line.detail.isNotBlank()) CaptionText(line.detail, color = if (line.state == de.abicoach.session.CheckState.FAIL) red else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun RoleRow(role: String, model: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        CaptionText(role, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        BodyText(model, color = accent)
    }
}

@Composable
private fun ModelField(label: String, current: String, onSave: (String) -> Unit) {
    var value by remember(current) { mutableStateOf(current) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        AbiTextField(value, { value = it }, label = label, modifier = Modifier.weight(1f))
        if (value != current && value.isNotBlank()) {
            TextButton(onClick = { onSave(value.trim()) }) { Text("OK", color = accent) }
        }
    }
}

@Composable
private fun ReminderCard(vm: AppViewModel) {
    val app = vm.app
    val settings = app.settings
    val context = LocalContext.current
    val permission =
        androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                settings.reminderOn = true
                de.abicoach.path.Reminder.schedule(context, settings.reminderHour)
            } else {
                vm.errorMessage = "Ohne Erlaubnis für Benachrichtigungen kann die App nicht erinnern."
            }
        }
    AbiCard(modifier = Modifier.testTag("settings_reminder")) {
        CardTitle("Erinnerung", icon = R.drawable.notifications_active_24dp)
        SwitchRow(
            "Tägliche Erinnerung",
            "Nur wenn du an dem Tag noch nicht genug geübt hast – mit dem nächsten Schritt auf deinem Abi-Weg.",
            settings.reminderOn,
            { on ->
                if (!on) {
                    settings.reminderOn = false
                    de.abicoach.path.Reminder.cancel(context)
                } else if (android.os.Build.VERSION.SDK_INT >= 33 && !de.abicoach.path.Reminder.canNotify(context)) {
                    permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    settings.reminderOn = true
                    de.abicoach.path.Reminder.schedule(context, settings.reminderHour)
                }
            },
        )
        if (settings.reminderOn) {
            Gap(8.dp)
            ChoiceChips(
                listOf(15, 16, 17, 18, 19, 20, 21),
                settings.reminderHour,
                { "$it Uhr" },
                { hour ->
                    vm.tap()
                    settings.reminderHour = hour
                    de.abicoach.path.Reminder.schedule(context, hour)
                },
                tagPrefix = "reminderhour",
            )
        }
    }
}
