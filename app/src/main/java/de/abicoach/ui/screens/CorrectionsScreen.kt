package de.abicoach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.data.Knowledge
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.ProgressLine
import de.abicoach.ui.components.StartButton
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.red

/**
 * The correction round right after a practice session: the student says the three most important mistakes correctly,
 * one by one, and the AI answers at once. The evaluation already runs in the background meanwhile.
 */
@Composable
fun CorrectionsScreen(vm: AppViewModel, id: String) {
    val sessions by vm.app.store.sessions.collectAsState()
    val record = sessions.firstOrNull { it.id == id }
    val status by vm.app.analysis.status.collectAsState()
    val items = remember(id, record?.liveErrors) { record?.let { vm.correctionTargets(it) }.orEmpty() }
    val dimens = AbiTheme.dimens
    SayItDialog(vm)
    DetailScaffold(
        title = "Korrekturrunde",
        onBack = { vm.tap(); vm.finishCorrections(id) },
        tag = "corrections",
        bottom = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    StartButton("Weiter zur Auswertung", onClick = { vm.tap(); vm.finishCorrections(id) }, icon = R.drawable.arrow_forward_24dp, modifier = Modifier.testTag("corrections_next"))
                }
            }
        },
    ) {
        MessageBanners(vm)
        AbiCard {
            CardTitle("Deine wichtigsten Fehler", icon = R.drawable.spellcheck_24dp, iconTint = red)
            BodyText("Sag jeden Satz jetzt noch einmal richtig – so bleibt die Korrektur im Kopf. Die KI hört zu und sagt dir sofort, ob es gestimmt hat.")
        }
        items.forEach { e ->
            val done = e.correction in vm.sayDone
            AbiCard(modifier = Modifier.testTag("correction_item")) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("„${e.quote}“", fontSize = dimens.fonts.body, color = red, textDecoration = TextDecoration.LineThrough)
                        Text("→ „${e.correction}“", fontSize = dimens.fonts.label, color = green)
                        CaptionText(e.explanation + " · " + Knowledge.categoryLabel(e.category))
                    }
                    SpeakButton(vm, e.correction)
                }
                Gap(8.dp)
                ActionButton(
                    if (done) "Richtig gesagt" else "Sag es richtig",
                    if (done) R.drawable.check_24dp else R.drawable.mic_24dp,
                    if (done) green else accent,
                    { vm.sayOpen(e.quote, e.correction, e.explanation) },
                    container = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("correction_say"),
                )
            }
        }
        AbiCard {
            if (record?.debrief != null) {
                CardTitle("Auswertung fertig", icon = R.drawable.task_alt_24dp, iconTint = green)
                CaptionText("Tippe auf „Weiter zur Auswertung“, um sie anzusehen.")
            } else if (status.error != null) {
                CardTitle("Auswertung", icon = R.drawable.error_24dp, iconTint = red)
                CaptionText("Die Auswertung hat nicht geklappt – auf der nächsten Seite kannst du es erneut versuchen.")
            } else {
                CardTitle("Die Auswertung läuft schon", icon = R.drawable.hourglass_bottom_24dp)
                CaptionText(status.text.ifBlank { "Die KI hört deine Aufnahme an und bewertet nach dem NRW-Raster …" })
                Gap(6.dp)
                ProgressLine(null)
            }
        }
    }
}
