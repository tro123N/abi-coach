package de.abicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import de.abicoach.data.Knowledge
import de.abicoach.data.LiveError
import de.abicoach.data.Speaker
import de.abicoach.data.Utterance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF1F3A5F)
private val Sky = Color(0xFF9DC3F0)

@Composable
fun AbiTheme(content: @Composable () -> Unit) {
    val scheme =
        if (isSystemInDarkTheme()) {
            darkColorScheme(primary = Sky, secondary = Color(0xFFF2B48C))
        } else {
            lightColorScheme(primary = Navy, onPrimary = Color.White, secondary = Color(0xFF9A4F24))
        }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Standard page: title row with optional back button, scrollable content. */
@Composable
fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    TextButton(onClick = onBack) { Text("‹ Zurück") }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ActionButton(title: String, subtitle: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun OptionRow(label: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun BusyRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun InfoCard(title: String?, body: String, actions: @Composable ColumnScope.() -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

@Composable
fun Bullets(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

/** A correction: what the student said, the better version and why. */
@Composable
fun ErrorCard(error: LiveError, highlight: Boolean = false) {
    val container = if (highlight) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${Knowledge.categoryLabel(error.category)}${if (error.severity == "major") " · wichtig" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(error.quote, style = MaterialTheme.typography.bodyMedium, textDecoration = TextDecoration.LineThrough)
            Text("→ ${error.correction}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (error.explanation.isNotBlank()) Text(error.explanation, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun UtteranceRow(utterance: Utterance) {
    val student = utterance.speaker == Speaker.STUDENT
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (student) "Du" else "Prüfer",
            style = MaterialTheme.typography.labelMedium,
            color = if (student) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        )
        Text(utterance.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun InterimText(text: String) {
    Text("… $text", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun formatSeconds(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun formatDate(millis: Long): String = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY).format(Date(millis))

fun gradeLabel(points: Int): String =
    when {
        points >= 13 -> "sehr gut"
        points >= 10 -> "gut"
        points >= 7 -> "befriedigend"
        points >= 5 -> "ausreichend"
        points == 4 -> "schwach ausreichend"
        points >= 1 -> "mangelhaft"
        else -> "ungenügend"
    }
