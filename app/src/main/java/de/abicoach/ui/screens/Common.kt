package de.abicoach.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
@Composable
fun SessionRow(record: SessionRecord, onClick: () -> Unit) {
    val dimens = AbiTheme.dimens
    val debrief = record.debrief
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.components.cornerRadius))
                .clickable { onClick() }
                .testTag("session_${record.id}")
                .padding(vertical = 6.dp),
    ) {
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
    if (vm.errorMessage == null && vm.notice == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.errorMessage?.let { Banner(it, red, icon = R.drawable.error_24dp, onClose = { vm.errorMessage = null }) }
        vm.notice?.let { Banner(it, green, icon = R.drawable.info_24dp, onClose = { vm.notice = null }) }
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
