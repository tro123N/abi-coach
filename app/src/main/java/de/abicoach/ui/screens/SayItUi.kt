package de.abicoach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.Banner
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.PopUp
import de.abicoach.ui.components.ProgressLine
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow

/** A small microphone next to a mistake: opens "Sag es richtig". A tick shows that it was said correctly. */
@Composable
fun SayItIcon(vm: AppViewModel, wrong: String, right: String, why: String = "") {
    val done = right in vm.sayDone
    IconButton(onClick = { vm.sayOpen(wrong, right, why) }, modifier = Modifier.testTag("sayit")) {
        Icon(
            painterResource(if (done) R.drawable.check_24dp else R.drawable.mic_24dp),
            contentDescription = "Sag es richtig",
            tint = if (done) green else accent,
        )
    }
}

/**
 * "Sag es richtig": shows the mistake and the right form; the student says it, the AI listens and answers at once.
 * Shown on top of any page that has [SayItIcon]s.
 */
@Composable
fun SayItDialog(vm: AppViewModel) {
    val record = rememberMicGate { vm.sayStart() }
    val target = vm.sayTarget ?: return
    val dimens = AbiTheme.dimens
    PopUp(
        title = "Sag es richtig",
        description = "",
        confirmText = "Schließen",
        onConfirm = { vm.sayClose() },
        onDismiss = { vm.sayClose() },
        cancelText = null,
    ) {
        CaptionText("Du hast gesagt:")
        Text("„${target.wrong}“", fontSize = dimens.fonts.body, color = red, textDecoration = TextDecoration.LineThrough)
        Gap(8.dp)
        CaptionText("Richtig:")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("„${target.right}“", fontSize = dimens.fonts.label, color = green, modifier = Modifier.weight(1f))
            SpeakButton(vm, target.right)
        }
        if (target.why.isNotBlank()) CaptionText(target.why)
        Gap(14.dp)
        when {
            vm.sayRecording ->
                ActionButton("Fertig", R.drawable.stop_24dp, red, { vm.sayStop() }, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("sayit_stop"))
            vm.sayBusy -> {
                ProgressLine(null)
                CaptionText("Die KI hört zu …")
            }
            else ->
                ActionButton(
                    if (vm.sayResult == null) "Aufnehmen" else "Noch einmal",
                    R.drawable.mic_24dp,
                    accent,
                    { record() },
                    container = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("sayit_record"),
                )
        }
        vm.sayResult?.let { r ->
            Gap(10.dp)
            if (r.ok) {
                Banner(r.tip.ifBlank { "Richtig gesagt!" }, green, icon = R.drawable.task_alt_24dp)
            } else {
                Banner(r.tip.ifBlank { "Noch nicht ganz – versuche es noch einmal." }, yellow, icon = R.drawable.info_24dp)
            }
            if (r.heard.isNotBlank()) {
                Gap(6.dp)
                CaptionText("Verstanden habe ich:")
                BodyText("„${r.heard}“")
            }
        }
    }
}
