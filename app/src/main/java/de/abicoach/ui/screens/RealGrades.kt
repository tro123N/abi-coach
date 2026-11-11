package de.abicoach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.profile.Profile
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.ChoiceChips
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.PopUp
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.yellow

private val GRADE_KINDS = listOf("Probe-Prüfung", "Kommunikationsprüfung", "Mündliche Note im Zeugnis")

/**
 * The honest check of the forecast: the student enters real grades for speaking from school, and the app shows
 * whether its own points tend to be too friendly or too strict – and what to expect with that in mind.
 */
@Composable
fun RealGradesBlock(vm: AppViewModel, profile: Profile) {
    var adding by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(GRADE_KINDS.first()) }
    var value by remember { mutableStateOf(9) }
    Gap(14.dp)
    BodyText("Abgleich mit echten Noten")
    Gap(2.dp)
    if (profile.realGrades.isEmpty()) {
        CaptionText("Die Punkte der App schätzt eine KI. Trag eine echte Note fürs Sprechen aus der Schule ein – dann siehst du, ob die App eher zu freundlich oder zu streng ist.")
    } else {
        profile.realGrades.sortedByDescending { it.at }.take(4).forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CaptionText(
                    "${formatDate(g.at)} · ${g.label}: ${g.points} P." + (g.forecast?.let { " · App damals: ${points(it)}" } ?: " · damals noch keine Prognose"),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painterResource(R.drawable.close_24dp),
                    contentDescription = "Note entfernen",
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 6.dp).size(28.dp).clip(CircleShape).clickable { vm.tap(); vm.removeRealGrade(g.at) }.padding(5.dp),
                )
            }
        }
        profile.calibration()?.let { c ->
            Gap(4.dp)
            val few = if (c.count == 1) " Eine einzelne Note zählt dabei nur zur Hälfte." else ""
            val text =
                when {
                    c.confirmed -> "Passt: Deine echten Noten liegen nah an der Prognose der App."
                    c.offset < 0 -> "Die App war bisher freundlicher als deine Lehrkraft. Rechne eher mit ${c.adjustedPoints} Punkten.$few"
                    else -> "Die App war bisher strenger als deine Lehrkraft. Rechne eher mit ${c.adjustedPoints} Punkten.$few"
                }
            CaptionText(text, color = if (c.confirmed) green else yellow, modifier = Modifier.testTag("real_grade_result"))
        }
    }
    Gap(8.dp)
    ActionButton(
        "Echte Note eintragen",
        R.drawable.school_24dp,
        accent,
        { vm.tap(); adding = true },
        subtitle = "Probe-Prüfung, Kommunikationsprüfung oder mündliche Zeugnisnote",
        container = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("real_grade_add"),
    )
    if (adding) {
        PopUp(
            title = "Echte Note eintragen",
            description = "Nur Noten fürs Sprechen – eine Klausur misst etwas anderes. Die App merkt sich dazu ihre Prognose von heute.",
            confirmText = "Speichern",
            onConfirm = {
                vm.addRealGrade(value, kind)
                adding = false
            },
            onDismiss = { adding = false },
        ) {
            ChoiceChips(GRADE_KINDS, kind, { it }, { vm.tap(); kind = it }, tagPrefix = "real_grade_kind")
            Gap(10.dp)
            CaptionText("Punkte (0–15)")
            Gap(4.dp)
            ChoiceChips((15 downTo 0).toList(), value, { it.toString() }, { vm.tap(); value = it }, tagPrefix = "real_grade_points")
        }
    }
}
