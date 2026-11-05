package de.abicoach.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.abicoach.R
import de.abicoach.ai.Provider
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.AbiTextField
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.BulletText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.ChoiceChips
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.StartButton
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class SetupStep(val title: String) {
    WELCOME("Willkommen"),
    AI("Deine KI"),
    KEY("Schlüssel"),
    EXAM("Prüfung"),
    COURSE("Kurs"),
    MIC("Mikrofon"),
    DONE("Fertig"),
}

/**
 * The first start: step by step everything the app needs to work – which AI, its key (checked right away), the exam
 * date, the course topics and the microphone. At the end a checklist shows what is still missing, then the guided
 * tour can start. Someone with a backup from another phone loads it right here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val app = vm.app
    val settings = app.settings
    val version by settings.version.collectAsState()
    val context = LocalContext.current
    val steps = SetupStep.entries
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    var micOk by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val askMic = rememberMicGate { micOk = true }
    var showDate by remember { mutableStateOf(false) }
    val importLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.inspectBackup(uri)
        }
    RestorePrompt(vm)
    BackHandler(enabled = index > 0) { index-- }

    fun go(next: Int) {
        vm.tap()
        index = next.coerceIn(0, steps.lastIndex)
    }

    val keyReady = version >= 0 && app.ai.ready
    val keyChecked = vm.keyChecks.containsKey(settings.provider.id) && vm.keyChecks[settings.provider.id] == null
    val examSet = runCatching { LocalDate.parse(settings.examDate) }.isSuccess

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().testTag("onboarding")) {
        // Where am I: the steps as a thin line, the name of this one.
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (index > 0) {
                TextButton(onClick = { go(index - 1) }, modifier = Modifier.testTag("setup_back")) { Text("Zurück", color = MaterialTheme.colorScheme.onPrimary) }
            } else {
                Text("AbiCoach einrichten", fontSize = AbiTheme.dimens.fonts.caption, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f))
            }
            Spacer(Modifier.weight(1f))
            Text("${index + 1} / ${steps.size} · ${step.title}", fontSize = AbiTheme.dimens.fonts.caption, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            steps.indices.forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= index) accent else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)),
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                SetupStep.WELCOME -> {
                    SetupHero(R.drawable.school_24dp, Color(0xFF3D7BFF), "Willkommen bei AbiCoach", "Dein Trainer für die mündliche Englisch-Prüfung im Abitur (NRW, Grundkurs) – mit echtem Sprechen.")
                    AbiCard(modifier = Modifier.testTag("setup_needs")) {
                        CardTitle("Damit die App funktioniert", icon = R.drawable.task_alt_24dp, iconTint = green)
                        NeedRow(1, "Eine KI und ihr Schlüssel", "Pflicht – die KI ist Prüfer, Coach und Bewerterin.")
                        NeedRow(2, "Dein Prüfungstermin", "Damit die App bis dahin plant.")
                        NeedRow(3, "Deine Kursthemen", "Damit Texte und Fragen zu deinem Unterricht passen.")
                        NeedRow(4, "Das Mikrofon", "Du übst, indem du sprichst.")
                        Gap(4.dp)
                        CaptionText("Dauert etwa 3 Minuten. Alles kannst du später unter Einstellungen ändern.")
                    }
                    ActionButton(
                        "Ich habe eine Sicherung",
                        R.drawable.upload_24dp,
                        accent,
                        { vm.tap(); importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        subtitle = "Lernstand von einem anderen Handy laden (Einstellungen → Daten → Lernstand sichern)",
                        modifier = Modifier.testTag("setup_restore"),
                    )
                }
                SetupStep.AI -> {
                    SetupHero(R.drawable.auto_awesome_24dp, Color(0xFF8A63D2), "Welche KI soll dich prüfen?", "Eine Firma macht alles: Prüfer, Mitschrift, Bewertung und Coach.")
                    AbiCard(modifier = Modifier.testTag("setup_ai")) {
                        ChoiceChips(Provider.entries, settings.provider, { it.label }, { vm.chooseProvider(it) }, tagPrefix = "setup_provider")
                        Gap(8.dp)
                        CaptionText(providerNote(settings.provider))
                        Gap(10.dp)
                        Text(
                            "Empfehlung: Google Gemini – der Prüfer hört und spricht gleichzeitig wie ein Mensch, es ist günstig, und zum Ausprobieren gibt es einen kostenlosen Tarif.",
                            fontSize = AbiTheme.dimens.fonts.body,
                            color = green,
                        )
                    }
                }
                SetupStep.KEY -> {
                    val p = settings.provider
                    SetupHero(R.drawable.key_24dp, Color(0xFFF2A93B), "Dein Schlüssel für ${p.company}", "Mit dem Schlüssel darf die App die KI für dich nutzen. Er bleibt verschlüsselt auf deinem Handy.")
                    AbiCard(modifier = Modifier.testTag("setup_key_how")) {
                        CardTitle("So bekommst du ihn", icon = R.drawable.info_24dp)
                        BulletText("Tippe unten auf „Schlüssel holen“ und melde dich bei ${p.company} an.")
                        BulletText(if (p == Provider.GOOGLE) "Tippe auf „Create API key“ (API-Schlüssel erstellen)." else "Erstelle einen neuen API-Schlüssel (secret key).")
                        BulletText("Kopiere ihn, komm zurück und füge ihn hier ein – dann auf „Speichern“.")
                        if (p != Provider.GOOGLE) CaptionText("Bei ${p.company} brauchst du ein Guthaben, sonst funktioniert der Schlüssel nicht.")
                    }
                    AbiCard(modifier = Modifier.testTag("setup_key")) {
                        androidx.compose.runtime.key(p.id) {
                            KeyBlock(
                                vm = vm,
                                provider = p.id,
                                title = p.label,
                                role = "Macht alles in der App",
                                current = settings.keyOf(p),
                                url = p.keyUrl,
                                required = true,
                                onSave = { settings.setKey(p, it) },
                            )
                        }
                    }
                }
                SetupStep.EXAM -> {
                    SetupHero(R.drawable.event_24dp, Color(0xFFE5534B), "Wann ist deine Prüfung?", "Die App plant bis dahin: erst breit üben, dann vertiefen, kurz vorher ganze Prüfungen.")
                    AbiCard(modifier = Modifier.testTag("setup_exam")) {
                        val date = runCatching { LocalDate.parse(settings.examDate) }.getOrNull()
                        ActionButton(
                            date?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "Datum wählen",
                            R.drawable.calendar_clock_24dp,
                            if (date != null) green else accent,
                            { vm.tap(); showDate = true },
                            subtitle = if (date == null) "Weißt du es noch nicht genau? Nimm ungefähr – du kannst es jederzeit ändern." else "Tippe, um es zu ändern",
                            container = MaterialTheme.colorScheme.background,
                            modifier = Modifier.testTag("setup_exam_date"),
                        )
                        Gap(10.dp)
                        CaptionText("Englisch als 4. Abiturfach, mündlich, Grundkurs NRW: Teil 1 Vortrag zu neuem Material, Teil 2 Prüfungsgespräch.")
                    }
                }
                SetupStep.COURSE -> {
                    SetupHero(R.drawable.menu_book_24dp, Color(0xFF2FB67C), "Was macht ihr im Kurs?", "Dann passen Prüfungstexte und Fragen zu deinem Unterricht.")
                    AbiCard(modifier = Modifier.testTag("setup_course")) {
                        var topics by remember { mutableStateOf(settings.courseTopics) }
                        AbiTextField(
                            topics,
                            { topics = it; settings.courseTopics = it },
                            label = "Themen, Bücher, Filme aus dem Unterricht",
                            placeholder = "z. B. The Hate U Give, Brexit, Purple Hibiscus",
                            singleLine = false,
                            minLines = 2,
                            tag = "setup_topics",
                        )
                        Gap(10.dp)
                        CaptionText("Noch besser: Lade nach der Einrichtung unter „Kurs“ dein Schulbuch, deine Klausuren und Arbeitsblätter hoch – als PDF oder Foto. Die Tour zeigt dir, wo.")
                    }
                }
                SetupStep.MIC -> {
                    SetupHero(R.drawable.mic_24dp, Color(0xFF3D7BFF), "Darf die App zuhören?", "Du übst, indem du sprichst. Die Aufnahme bleibt auf deinem Handy; zum Bewerten geht sie nur an deine KI.")
                    AbiCard(modifier = Modifier.testTag("setup_mic")) {
                        if (micOk) {
                            Text("✓ Mikrofon ist erlaubt", fontSize = AbiTheme.dimens.fonts.body, fontWeight = FontWeight.Bold, color = green)
                        } else {
                            ActionButton("Mikrofon erlauben", R.drawable.mic_24dp, accent, { vm.tap(); askMic() }, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("setup_mic_allow"))
                        }
                        Gap(8.dp)
                        CaptionText("Tipp: Mit Kopfhörern hörst du den Prüfer besser und kannst ihn auch unterbrechen.")
                    }
                }
                SetupStep.DONE -> {
                    SetupHero(R.drawable.rocket_launch_24dp, Color(0xFF2FB67C), if (keyReady) "Alles bereit!" else "Fast fertig", "Hier siehst du, was eingerichtet ist.")
                    AbiCard(modifier = Modifier.testTag("setup_summary")) {
                        CheckLine(keyReady, "KI-Schlüssel (${settings.provider.company})", if (keyReady) (if (keyChecked) "eingetragen und geprüft" else "eingetragen") else "fehlt – ohne Schlüssel kann die App nicht üben", required = true) { go(SetupStep.KEY.ordinal) }
                        CheckLine(examSet, "Prüfungstermin", if (examSet) "eingestellt" else "noch offen") { go(SetupStep.EXAM.ordinal) }
                        CheckLine(settings.courseTopics.isNotBlank(), "Kursthemen", if (settings.courseTopics.isNotBlank()) "eingetragen" else "noch offen – oder lade Material hoch") { go(SetupStep.COURSE.ordinal) }
                        CheckLine(micOk, "Mikrofon", if (micOk) "erlaubt" else "noch nicht erlaubt") { go(SetupStep.MIC.ordinal) }
                    }
                    CaptionText("Als Nächstes zeigt dir eine kurze Tour, was wo in der App ist. Du kannst sie überspringen und später unter Einstellungen wieder starten.")
                }
            }
        }
        // The button that moves on.
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            when (step) {
                SetupStep.WELCOME -> StartButton("Einrichten", onClick = { go(1) }, icon = R.drawable.arrow_forward_24dp, modifier = Modifier.testTag("setup_next"))
                SetupStep.KEY -> {
                    StartButton(
                        if (keyReady) "Weiter" else "Erst den Schlüssel speichern",
                        onClick = { go(index + 1) },
                        enabled = keyReady,
                        icon = R.drawable.arrow_forward_24dp,
                        modifier = Modifier.testTag("setup_next"),
                    )
                    if (!keyReady) {
                        TextButton(onClick = { go(index + 1) }, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("setup_key_later")) {
                            Text("Später eintragen", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        }
                    }
                }
                SetupStep.DONE -> {
                    StartButton("Tour starten", onClick = { vm.finishOnboarding(withTour = true) }, icon = R.drawable.explore_24dp, modifier = Modifier.testTag("setup_tour"))
                    TextButton(onClick = { vm.finishOnboarding(withTour = false) }, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("setup_finish")) {
                        Text("Ohne Tour loslegen", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                    }
                }
                else -> StartButton("Weiter", onClick = { go(index + 1) }, icon = R.drawable.arrow_forward_24dp, modifier = Modifier.testTag("setup_next"))
            }
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
}

@Composable
private fun SetupHero(icon: Int, tint: Color, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.95f), tint.copy(alpha = 0.55f)))),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
        Gap(16.dp)
        Text(title, fontSize = AbiTheme.dimens.fonts.label * 1.3f, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimary)
        Gap(8.dp)
        Text(text, fontSize = AbiTheme.dimens.fonts.body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
    }
}

@Composable
private fun NeedRow(number: Int, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text("$number", fontSize = AbiTheme.dimens.fonts.caption, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = AbiTheme.dimens.fonts.body, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            CaptionText(text)
        }
    }
}

@Composable
private fun CheckLine(ok: Boolean, title: String, state: String, required: Boolean = false, onClick: () -> Unit) {
    ActionButton(
        title,
        if (ok) R.drawable.check_circle_24dp else R.drawable.info_24dp,
        if (ok) green else if (required) red else yellow,
        onClick,
        subtitle = state,
        container = MaterialTheme.colorScheme.background,
    )
    Gap(6.dp)
}
