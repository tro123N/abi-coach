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
import de.abicoach.ai.AiHub
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
            CardTitle("KI", icon = R.drawable.auto_awesome_24dp)
            ChoiceChips(Quality.entries, settings.quality, { it.label }, { vm.tap(); settings.quality = it }, tagPrefix = "quality")
            Gap(6.dp)
            CaptionText(settings.quality.description)
            Gap(14.dp)
            KeyBlock(
                vm = vm,
                provider = "gemini",
                title = "Google Gemini",
                role = "Pflicht · Mitschrift, Gedächtnis, Aussprache, Vorlesen",
                current = settings.geminiKey,
                url = "https://aistudio.google.com/apikey",
                onSave = { settings.geminiKey = it },
            )
            Gap(14.dp)
            KeyBlock(
                vm = vm,
                provider = "openai",
                title = "OpenAI",
                role = "Optional · Prüfer GPT-Live-1 – hört und spricht gleichzeitig (ca. 5 ct pro Minute)",
                current = settings.openAiKey,
                url = "https://platform.openai.com/api-keys",
                onSave = { settings.openAiKey = it },
            )
            Gap(14.dp)
            KeyBlock(
                vm = vm,
                provider = "anthropic",
                title = "Anthropic Claude",
                role = "Optional · Bewertung, Prüfungsaufgaben und Coach mit Claude Opus 5.5",
                current = settings.anthropicKey,
                url = "https://console.anthropic.com/settings/keys",
                onSave = { settings.anthropicKey = it },
            )
            Gap(10.dp)
            CaptionText("Die Schlüssel bleiben verschlüsselt auf deinem Handy und gehen nur an den jeweiligen Anbieter.")
        }
        AbiCard {
            CardTitle("Wer macht was", icon = R.drawable.hub_24dp)
            RoleRow("Prüfer im Gespräch", app.ai.examinerLabel())
            RoleRow("Wortgetreue Mitschrift", AiHub.pretty(settings.transcribeModel))
            RoleRow("Bewertung, Aufgaben, Coach", app.ai.brainLabel())
            RoleRow("Fehlerprüfung beim Sprechen", app.ai.fastLabel())
            RoleRow("Aufnahme anhören", app.ai.listenerLabel())
            RoleRow("Material lesen", AiHub.pretty(settings.geminiReaderModel))
            RoleRow("Suche im Material", AiHub.pretty(settings.embeddingModel))
            RoleRow("Vorlesen", AiHub.pretty(settings.ttsModel))
            Gap(4.dp)
            CaptionText("Fällt ein Anbieter aus (kein Guthaben, kein Zugang), springt automatisch Gemini ein.")
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
        AbiCard {
            CardTitle("Stimmen", icon = R.drawable.record_voice_over_24dp)
            if (app.ai.useOpenAi) {
                BodyText("Prüfer (GPT-Live-1)")
                Gap(6.dp)
                ChoiceChips(Settings.OPENAI_VOICES, settings.openAiVoice, { it.replaceFirstChar { c -> c.uppercase() } }, { vm.tap(); settings.openAiVoice = it }, tagPrefix = "oaivoice")
            } else {
                BodyText("Prüfer (Gemini Live)")
                Gap(6.dp)
                ChoiceChips(Settings.GEMINI_VOICES, settings.geminiVoice, { it }, { vm.tap(); settings.geminiVoice = it }, tagPrefix = "gvoice")
            }
            Gap(12.dp)
            BodyText("Vorlesen von Musterantworten")
            Gap(6.dp)
            ChoiceChips(Settings.GEMINI_VOICES, settings.ttsVoice, { it }, { vm.tap(); settings.ttsVoice = it }, tagPrefix = "ttsvoice")
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
                "Aufnahme für die Auswertung hochladen",
                "Gemini hört Aussprache und Flüssigkeit. Ohne Aufnahme wird nur das Transkript bewertet.",
                settings.uploadAudio,
                { settings.uploadAudio = it },
            )
            SwitchRow("Mitschrift beim Vortrag zeigen", "Im Gespräch wird sie immer gezeigt.", settings.showTranscript, { settings.showTranscript = it })
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
        ExpandableCard("Erweitert: Modelle", subtitle = "Nur ändern, wenn ein Anbieter ein Modell umbenennt", icon = R.drawable.tune_24dp) {
            ModelField("Claude – Bewertung", settings.claudeBrainModel) { settings.claudeBrainModel = it }
            ModelField("Claude – schnelle Prüfung", settings.claudeFastModel) { settings.claudeFastModel = it }
            ModelField("OpenAI – Prüfer", settings.openAiLiveModel) { settings.openAiLiveModel = it }
            ModelField("Gemini – Prüfer (ohne OpenAI)", settings.geminiLiveModel) { settings.geminiLiveModel = it }
            ModelField("Gemini – Mitschrift", settings.transcribeModel) { settings.transcribeModel = it }
            ModelField("Gemini – stärkstes Modell", settings.geminiBestModel) { settings.geminiBestModel = it }
            ModelField("Gemini – schnelles Modell", settings.geminiTextModel) { settings.geminiTextModel = it }
            ModelField("Gemini – Material lesen", settings.geminiReaderModel) { settings.geminiReaderModel = it }
            ModelField("Gemini – Suche (Vektoren)", settings.embeddingModel) { settings.embeddingModel = it }
            ModelField("Gemini – Vorlesen", settings.ttsModel) { settings.ttsModel = it }
        }
        AbiCard {
            CardTitle("Daten", icon = R.drawable.folder_24dp)
            ActionButton("Alle Sitzungen löschen", R.drawable.delete_24dp, red, { confirm = "sessions" }, container = MaterialTheme.colorScheme.background)
            Gap(8.dp)
            ActionButton("Lernprofil zurücksetzen", R.drawable.refresh_24dp, yellow, { confirm = "profile" }, container = MaterialTheme.colorScheme.background)
            Gap(6.dp)
            CaptionText("Dein Kursmaterial verwaltest du unter „Mein Kurs“.")
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
            description = if (what == "sessions") "Alle Auswertungen und Aufnahmen werden gelöscht." else "Prognose und Kompetenzen beginnen von vorn. Die Sitzungen bleiben.",
            confirmText = if (what == "sessions") "Löschen" else "Zurücksetzen",
            onConfirm = {
                if (what == "sessions") app.store.deleteAll() else app.profiles.reset()
                confirm = null
            },
            onDismiss = { confirm = null },
            confirmColor = red,
        )
    }
}

@Suppress("FunctionName")
private fun Planner_days(vm: AppViewModel): Long? = de.abicoach.ui.Planner.daysUntilExam(vm.app)

@Composable
private fun KeyBlock(vm: AppViewModel, provider: String, title: String, role: String, current: String, url: String, onSave: (String) -> Unit) {
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
                color = if (current.isBlank()) (if (provider == "gemini") red else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)) else green,
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
