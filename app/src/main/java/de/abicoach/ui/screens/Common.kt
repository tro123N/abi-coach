package de.abicoach.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.abicoach.R
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.ui.Tint
import de.abicoach.ui.components.Banner
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.LevelBadge
import de.abicoach.ui.components.PopUp
import de.abicoach.ui.components.StarRow
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.pointsColor
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM. HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormat)

fun formatDay(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dayFormat)

fun mmss(seconds: Int): String = "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

fun points(value: Double): String = "%.1f".format(value).replace('.', ',')

@Composable
fun tintColor(tint: Tint): Color =
    when (tint) {
        Tint.GREEN -> green
        Tint.ACCENT -> accent
        Tint.YELLOW -> yellow
        Tint.RED -> red
        Tint.NEUTRAL -> MaterialTheme.colorScheme.onPrimary
    }

fun kindIcon(kind: SessionKind): Int =
    when (kind) {
        SessionKind.SIMULATION -> R.drawable.workspace_premium_24dp
        SessionKind.PLACEMENT -> R.drawable.flag_24dp
        SessionKind.PART1 -> R.drawable.co_present_24dp
        SessionKind.PART2 -> R.drawable.forum_24dp
        SessionKind.TRAINING -> R.drawable.chat_24dp
    }

/** One past session: symbol, title, date and topic, points with stars. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SessionRow(record: SessionRecord, selected: Boolean? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val dimens = AbiTheme.dimens
    val debrief = record.debrief
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.components.cornerRadius))
                .combinedClickable(onLongClick = onLongClick, onClick = onClick)
                .testTag("session_${record.id}")
                .padding(vertical = 6.dp),
    ) {
        if (selected != null) {
            androidx.compose.material3.Checkbox(
                checked = selected,
                onCheckedChange = null,
                colors =
                    androidx.compose.material3.CheckboxDefaults.colors(
                        checkedColor = accent,
                        checkmarkColor = Color.White,
                        uncheckedColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
                    ),
                modifier = Modifier.testTag("select_${record.id}"),
            )
            Spacer(Modifier.width(6.dp))
        }
        LevelBadge(
            label = "",
            locked = false,
            mastered = debrief != null && debrief.totalPoints >= 13,
            attention = debrief == null,
            highlighted = debrief != null,
            size = dimens.components.button * 0.6f,
            icon = kindIcon(record.kind),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(record.title, fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CaptionText(
                formatDate(record.startedAt) + " · " + Knowledge.topicShort(record.topicId) + " · " + mmss(record.durationSec) +
                    if (debrief == null) " · noch nicht ausgewertet" else "",
            )
        }
        if (debrief != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${debrief.totalPoints} P.", fontSize = dimens.fonts.body, color = pointsColor(debrief.totalPoints.toDouble()))
                StarRow(stars = debrief.stars, size = dimens.icons.status * 0.55f)
            }
        }
    }
}

/** Fehler- und Hinweisbänder, die auf mehreren Seiten erscheinen. */
@Composable
fun MessageBanners(vm: AppViewModel) {
    if (vm.errorMessage == null && vm.notice == null && vm.readyDebrief == null && vm.readyMaterialMinutes == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.readyMaterialMinutes > 0) {
            Banner(
                "Dein Prüfungstext ist fertig. Tippe hier, um die Vorbereitung zu starten – die ${vm.readyMaterialMinutes} Minuten laufen erst dann.",
                accent,
                icon = R.drawable.article_24dp,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.openPrep() }.testTag("ready_material"),
            )
        }
        vm.readyDebrief?.let { (id, points) ->
            Banner(
                "Deine Auswertung ist fertig: $points Punkte. Tippe zum Ansehen.",
                accent,
                icon = R.drawable.task_alt_24dp,
                onClose = { vm.readyDebrief = null },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.readyDebrief = null; vm.tap(); vm.nav.push(de.abicoach.ui.Screen.Debrief(id)) }.testTag("ready_debrief"),
            )
        }
        vm.errorMessage?.let { Banner(it, red, icon = R.drawable.error_24dp, onClose = { vm.errorMessage = null }) }
        vm.notice?.let { Banner(it, green, icon = R.drawable.info_24dp, onClose = { vm.notice = null }) }
    }
}

/**
 * Messages of the app about itself, on the start page only: a crash in the last run (with the report to copy), a
 * session that was rescued after the app was closed in the middle, and a reminder to save the learning progress.
 */
@Composable
fun SystemBanners(vm: AppViewModel) {
    var showCrash by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.crashReport != null) {
            Banner(
                "AbiCoach wurde beim letzten Mal unerwartet beendet. Dein Lernstand ist sicher. Tippe hier für den Fehlerbericht.",
                yellow,
                icon = R.drawable.error_24dp,
                onClose = { vm.dismissCrash() },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.tap(); showCrash = true }.testTag("crash_banner"),
            )
        }
        vm.recoveredSession?.let { id ->
            Banner(
                "Deine letzte Übung wurde unterbrochen – sie ist gerettet. Tippe hier, um sie auszuwerten oder zu löschen.",
                accent,
                icon = R.drawable.task_alt_24dp,
                onClose = { vm.recoveredSession = null },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.recoveredSession = null; vm.openSession(id) }.testTag("recovered_banner"),
            )
        }
        if (vm.backupDue) {
            Banner(
                vm.backupDueText(),
                green,
                icon = R.drawable.download_24dp,
                onClose = { vm.snoozeBackup() },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.tap(); vm.settingsSection = SettingsSection.DATEN.name; vm.nav.push(de.abicoach.ui.Screen.Settings) }.testTag("backup_banner"),
            )
        }
    }
    if (showCrash) CrashReportPopUp(vm.crashReport ?: de.abicoach.data.CrashLog.report()) { showCrash = false; vm.dismissCrash() }
}

/** Shows a crash report (the beginning) and copies the whole of it, so it can be sent to whoever looks after the app. */
@Composable
fun CrashReportPopUp(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    PopUp(
        title = "Fehlerbericht",
        description = "Der Bericht bleibt auf deinem Handy. Kopiere ihn, wenn du ihn weitergeben willst – er enthält keine Schlüssel und nichts von dem, was du gesagt hast.",
        confirmText = "Kopieren",
        cancelText = "Schließen",
        onConfirm = {
            context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("AbiCoach Fehlerbericht", report))
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
                .testTag("crash_report_text"),
        ) {
            Text(report.take(2_500), fontSize = AbiTheme.dimens.fonts.caption, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Keeps the screen on while this composable is shown (preparation, live conversation). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Android 13+: notifications ("Dein Text ist fertig", "Auswertung fertig", "Vorbereitungszeit vorbei") need a one-time
 * permission. Asked once while the page is shown; without it the work still runs, only the message is missing.
 */
@Composable
fun AskNotificationPermission() {
    val context = LocalContext.current
    var asked by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!asked && !de.abicoach.session.Notifications.canNotify(context)) {
            asked = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Returns a function that runs [action] once the microphone may be used (asks for permission if needed).
 */
@Composable
fun rememberMicGate(action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) action() else denied = true }
    if (denied) {
        PopUp(
            title = "Mikrofon erlaubt?",
            description = "Ohne Mikrofon kann AbiCoach nicht zuhören. Erlaube den Zugriff in den Android-Einstellungen der App.",
            confirmText = "OK",
            onConfirm = { denied = false },
            onDismiss = { denied = false },
            cancelText = null,
        )
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** Read-aloud button: the AI's voice, tap again to stop. [prefetch] makes the speech ahead, so the tap plays at once. */
@Composable
fun SpeakButton(vm: AppViewModel, text: String, prefetch: Boolean = false) {
    if (prefetch) androidx.compose.runtime.LaunchedEffect(text) { vm.prefetchSpeech(text) }
    val active = vm.speaking == text
    IconButton(onClick = { if (active) vm.stopSpeaking() else vm.speak(text) }) {
        Icon(
            painterResource(if (active) R.drawable.stop_24dp else R.drawable.volume_up_24dp),
            contentDescription = if (active) "Stopp" else "Vorlesen",
            tint = if (active) red else accent,
            modifier = Modifier.size(AbiTheme.dimens.icons.menuItem * 0.8f),
        )
    }
}
