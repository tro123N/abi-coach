package de.abicoach.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.learn.PatternState
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.WorkshopController.Stage
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.AreaIntro
import de.abicoach.ui.components.Banner
import de.abicoach.ui.components.BarRow
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.BulletText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.LoadingCard
import de.abicoach.ui.components.Pill
import de.abicoach.ui.components.ProgressLine
import de.abicoach.ui.components.StartButton
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow

/** The Fehler-Werkstatt: one error pattern, five steps, and a tutor that remembers what helps the student. */
@Composable
fun WorkshopScreen(vm: AppViewModel) {
    val w = vm.workshop
    val dimens = AbiTheme.dimens
    val record = rememberMicGate { w.startRecording() }
    BackHandler { vm.closeWorkshop() }
    val lesson = w.lesson
    val pattern = w.pattern
    DetailScaffold(
        title = "Fehler-Werkstatt",
        onBack = { vm.closeWorkshop() },
        tag = "workshop",
        bottom = { WorkshopBottom(vm, record) },
    ) {
        MessageBanners(vm)
        if (w.stage == Stage.EMPTY) {
            AreaIntro(R.drawable.school_24dp, "Deine Fehler-Werkstatt", "Hier behebst du deine typischen Fehler Schritt für Schritt – mit deinen eigenen Sätzen.")
            AbiCard {
                CardTitle("Noch keine Fehler gesammelt", icon = R.drawable.psychology_24dp)
                BodyText("Sobald du übst, merkt sich die App jede Fehlerart. Dann holst du sie hier einzeln in die Werkstatt.")
            }
            return@DetailScaffold
        }
        if (pattern != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lesson?.title ?: pattern.title, fontSize = dimens.fonts.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    CaptionText("${pattern.categoryLabel} · ${pattern.total}× bei dir · " + (lesson?.let { "Niveau ${it.level}" } ?: ""))
                }
                Pill(pattern.state.label, stateTint(pattern.state))
            }
            StageSteps(w.stage)
        }
        if (w.stage == Stage.LOADING || lesson == null) {
            LoadingCard("Die KI baut deine Lerneinheit …", detail = w.memory?.takeIf { it.rounds > 0 }?.let { "Sie beginnt da, wo es zuletzt noch hakte." })
            return@DetailScaffold
        }
        when (w.stage) {
            Stage.LEARN -> {
                w.memory?.takeIf { it.rounds > 0 && (it.stillHard.isNotBlank() || it.understood.isNotBlank()) }?.let { m ->
                    AbiCard(modifier = Modifier.testTag("ws_memory")) {
                        CardTitle("Das weiß die KI schon über dich", icon = R.drawable.psychology_24dp)
                        if (m.understood.isNotBlank()) GoalLine(true, "Kannst du: ${m.understood}")
                        if (m.stillHard.isNotBlank()) GoalLine(false, "Noch schwer: ${m.stillHard}")
                        if (m.helps.isNotBlank()) CaptionText("Hilft dir: ${m.helps}")
                    }
                }
                AbiCard(modifier = Modifier.testTag("ws_learn")) {
                    CardTitle("1 · Verstehen", icon = R.drawable.lightbulb_24dp, iconTint = yellow)
                    Text(lesson.explanation, fontSize = dimens.fonts.reading, lineHeight = dimens.fonts.reading * 1.4f, color = MaterialTheme.colorScheme.onPrimary)
                    if (lesson.hook.isNotBlank()) {
                        Gap(10.dp)
                        Banner(lesson.hook, yellow, icon = R.drawable.tips_and_updates_24dp)
                    }
                    if (lesson.contrast.isNotEmpty()) {
                        Gap(12.dp)
                        CaptionText("Falsch → richtig")
                        lesson.contrast.forEach { (wrong, right) ->
                            Gap(6.dp)
                            Text("„$wrong“", fontSize = dimens.fonts.body, color = red, textDecoration = TextDecoration.LineThrough)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("„$right“", fontSize = dimens.fonts.body, color = green, modifier = Modifier.weight(1f))
                                SpeakButton(vm, right, prefetch = true)
                            }
                        }
                    }
                }
            }
            Stage.RECOGNIZE -> {
                val item = lesson.recognize.getOrNull(w.recognizeIndex)
                if (item != null) {
                    AbiCard(modifier = Modifier.testTag("ws_recognize")) {
                        CardTitle("2 · Erkennen – ${w.recognizeIndex + 1} von ${lesson.recognize.size}", icon = R.drawable.visibility_24dp)
                        CaptionText("Ist der Satz richtig?")
                        Gap(8.dp)
                        Text(item.sentence, fontSize = dimens.fonts.body * 1.12f, lineHeight = dimens.fonts.body * 1.5f, color = MaterialTheme.colorScheme.onPrimary)
                        Gap(12.dp)
                        val answer = w.recognizeAnswer
                        if (answer == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ActionButton("Richtig", R.drawable.check_24dp, green, { w.answer(true) }, Modifier.weight(1f).testTag("ws_right"), container = MaterialTheme.colorScheme.background)
                                ActionButton("Falsch", R.drawable.close_24dp, red, { w.answer(false) }, Modifier.weight(1f).testTag("ws_wrong"), container = MaterialTheme.colorScheme.background)
                            }
                        } else {
                            val ok = answer == item.correct
                            Banner(
                                (if (ok) "Genau! " else "Nicht ganz. ") + (if (item.correct) "Der Satz ist richtig." else "Der Satz ist falsch.") + if (item.why.isNotBlank()) " ${item.why}" else "",
                                if (ok) green else yellow,
                                icon = if (ok) R.drawable.task_alt_24dp else R.drawable.info_24dp,
                            )
                            if (!item.correct && item.fix.isNotBlank()) {
                                Gap(6.dp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("„${item.fix}“", fontSize = dimens.fonts.body, color = green, modifier = Modifier.weight(1f))
                                    SpeakButton(vm, item.fix)
                                }
                            }
                        }
                    }
                }
            }
            Stage.SPEAK -> {
                val drill = lesson.drills.getOrNull(w.drillIndex)
                if (drill != null) {
                    AbiCard(modifier = Modifier.testTag("ws_speak")) {
                        CardTitle("3 · Richtig sagen – ${w.drillIndex + 1} von ${lesson.drills.size}", icon = R.drawable.record_voice_over_24dp)
                        Text(drill.instruction, fontSize = dimens.fonts.label, lineHeight = dimens.fonts.label * 1.25f, color = MaterialTheme.colorScheme.onPrimary)
                        Gap(10.dp)
                        if (drill.type == de.abicoach.learn.DrillType.SAY_RIGHT) {
                            CaptionText("So hast du es gesagt:")
                            Text("„${drill.cue}“", fontSize = dimens.fonts.body, color = red, textDecoration = TextDecoration.LineThrough)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(drill.cue, fontSize = dimens.fonts.body * 1.08f, lineHeight = dimens.fonts.body * 1.45f, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f))
                                SpeakButton(vm, drill.cue, prefetch = true)
                            }
                        }
                        RecordingState(w.recording, w.busy)
                        w.drillResult?.let { r ->
                            Gap(10.dp)
                            Banner(r.tip.ifBlank { if (r.ok) "Richtig!" else "Noch nicht ganz." }, if (r.ok) green else yellow, icon = if (r.ok) R.drawable.task_alt_24dp else R.drawable.info_24dp)
                            if (r.heard.isNotBlank()) CaptionText("Verstanden habe ich: „${r.heard}“")
                            Gap(6.dp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("„${drill.target}“", fontSize = dimens.fonts.body, color = green, modifier = Modifier.weight(1f))
                                SpeakButton(vm, drill.target, prefetch = true)
                            }
                            if (!r.ok) {
                                Gap(8.dp)
                                ActionButton("Gleich noch einmal", R.drawable.refresh_24dp, accent, { record() }, container = MaterialTheme.colorScheme.background)
                            }
                        }
                    }
                }
            }
            Stage.TALK -> {
                val prompt = lesson.talk.getOrNull(w.talkIndex)
                if (prompt != null) {
                    AbiCard(modifier = Modifier.testTag("ws_talk")) {
                        CardTitle("4 · Frei sprechen – ${w.talkIndex + 1} von ${lesson.talk.size}", icon = R.drawable.forum_24dp)
                        CaptionText("Antworte in drei, vier Sätzen – wie im Prüfungsgespräch.")
                        Gap(8.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(prompt.question, fontSize = dimens.fonts.body * 1.1f, lineHeight = dimens.fonts.body * 1.5f, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f))
                            SpeakButton(vm, prompt.question, prefetch = true)
                        }
                        if (prompt.hint.isNotBlank()) {
                            Gap(6.dp)
                            CaptionText("Tipp: ${prompt.hint}")
                        }
                        RecordingState(w.recording, w.busy)
                        w.talkResult?.let { r ->
                            Gap(10.dp)
                            Banner(
                                r.tip.ifBlank { if (r.ok) "Richtig verwendet!" else "Noch nicht ganz." },
                                if (r.ok) green else yellow,
                                icon = if (r.ok) R.drawable.task_alt_24dp else R.drawable.info_24dp,
                            )
                            if (r.heard.isNotBlank()) CaptionText("Verstanden habe ich: „${r.heard}“")
                            if (!r.used && r.heard.isNotBlank()) CaptionText("Die Regel kam in deiner Antwort nicht vor – versuch, sie einzubauen.", color = yellow)
                            if (r.better.isNotBlank()) {
                                Gap(6.dp)
                                CaptionText("So klingt es richtig:")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("„${r.better}“", fontSize = dimens.fonts.body, color = green, modifier = Modifier.weight(1f))
                                    SpeakButton(vm, r.better)
                                }
                            }
                            if (!r.ok) {
                                Gap(8.dp)
                                ActionButton("Noch einmal antworten", R.drawable.refresh_24dp, accent, { record() }, container = MaterialTheme.colorScheme.background)
                            }
                        }
                    }
                }
            }
            Stage.DONE -> WorkshopResult(vm)
            else -> Unit
        }
        if (w.stage != Stage.DONE && w.stepsTotal > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CaptionText("${w.stepsDone} von ${w.stepsTotal} Aufgaben")
                Spacer(Modifier.width(10.dp))
                ProgressLine(w.stepsDone.toFloat() / w.stepsTotal, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WorkshopResult(vm: AppViewModel) {
    val w = vm.workshop
    val run = w.run ?: return
    val pattern = w.pattern
    val score = run.score
    AbiCard(modifier = Modifier.testTag("ws_result")) {
        CardTitle("5 · Ergebnis", icon = R.drawable.emoji_events_24dp, iconTint = if (score >= 70) green else yellow)
        Text(
            "$score % richtig",
            fontSize = AbiTheme.dimens.fonts.label * 1.3f,
            fontWeight = FontWeight.Bold,
            color = if (score >= 80) green else if (score >= 50) yellow else red,
        )
        Gap(8.dp)
        if (run.recognize.second > 0) BarRow("Erkennen", run.recognize.first.toFloat() / run.recognize.second, "${run.recognize.first}/${run.recognize.second}", accent)
        Gap(6.dp)
        if (run.speak.second > 0) BarRow("Richtig sagen", run.speak.first.toFloat() / run.speak.second, "${run.speak.first}/${run.speak.second}", accent)
        Gap(6.dp)
        if (run.talk.second > 0) BarRow("Frei sprechen", run.talk.first.toFloat() / run.talk.second, "${run.talk.first}/${run.talk.second}", green)
        Gap(10.dp)
        val before = w.stateBefore
        if (pattern != null && before != null && before != pattern.state) {
            Banner("Dein Fehler „${pattern.title}“ ist jetzt: ${pattern.state.label}", green, icon = R.drawable.trending_up_24dp)
        } else {
            CaptionText(if (score >= 70) "Gut! Die App fragt den Fehler in ein paar Tagen wieder ab – erst dann gilt er als behoben." else "Morgen kommt dieser Fehler wieder dran – mit neuen Sätzen.")
        }
    }
    AbiCard(modifier = Modifier.testTag("ws_tutor")) {
        CardTitle("Das hat die KI über dich gelernt", icon = R.drawable.psychology_24dp)
        val m = w.memory
        when {
            w.reflecting -> {
                ProgressLine(null)
                CaptionText("Die KI wertet die Runde aus und merkt sich, was dir hilft …")
            }
            m == null -> CaptionText("Noch nichts.")
            else -> {
                if (m.understood.isNotBlank()) GoalLine(true, "Kannst du jetzt: ${m.understood}")
                if (m.stillHard.isNotBlank()) GoalLine(false, "Noch schwer: ${m.stillHard}")
                if (m.helps.isNotBlank()) BulletText("Hilft dir: ${m.helps}")
                if (m.nextFocus.isNotBlank()) BulletText("Nächste Runde: ${m.nextFocus}")
                CaptionText("Runde ${m.rounds} · nächstes Mal Niveau ${m.level}. Auch das Sprech-Training und der Prüfer in deinen Übungen achten jetzt genau darauf.")
            }
        }
    }
    ActionButton("Neue Runde mit neuen Sätzen", R.drawable.refresh_24dp, accent, { vm.tap(); w.again() }, modifier = Modifier.testTag("ws_again"))
    ActionButton("Nächster Fehler", R.drawable.arrow_forward_24dp, yellow, { vm.tap(); w.nextPattern() }, modifier = Modifier.testTag("ws_next_pattern"))
    ActionButton("Fertig", R.drawable.check_24dp, green, { vm.closeWorkshop() })
}

/** The one big button at the bottom: go on, speak, or stop the recording – depending on the step. */
@Composable
private fun WorkshopBottom(vm: AppViewModel, record: () -> Unit) {
    val w = vm.workshop
    val speaking = w.stage == Stage.SPEAK || w.stage == Stage.TALK
    val visible = w.stage == Stage.LEARN || (w.stage == Stage.RECOGNIZE && w.recognizeAnswer != null) || speaking
    if (!visible) return
    val hasResult = if (w.stage == Stage.SPEAK) w.drillResult != null else w.talkResult != null
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            when {
                w.stage == Stage.LEARN ->
                    StartButton("Verstanden – weiter", onClick = { vm.tap(); w.next() }, icon = R.drawable.arrow_forward_24dp, modifier = Modifier.testTag("ws_next"))
                !speaking || (hasResult && !w.busy && !w.recording) ->
                    StartButton("Weiter", onClick = { vm.tap(); w.next() }, icon = R.drawable.arrow_forward_24dp, modifier = Modifier.testTag("ws_next"))
                w.recording ->
                    StartButton("Fertig", onClick = { w.stopRecording() }, icon = R.drawable.stop_24dp, iconTint = red, modifier = Modifier.testTag("ws_stop"))
                else ->
                    StartButton(if (w.busy) "Die KI hört zu …" else "Sprechen", onClick = { record() }, enabled = !w.busy, icon = R.drawable.mic_24dp, modifier = Modifier.testTag("ws_record"))
            }
        }
    }
}

/** The five steps as a row of small marks. */
@Composable
private fun StageSteps(current: Stage) {
    val steps = listOf(Stage.LEARN, Stage.RECOGNIZE, Stage.SPEAK, Stage.TALK, Stage.DONE)
    val index = steps.indexOf(current)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().testTag("ws_steps")) {
        steps.forEachIndexed { i, s ->
            val done = index >= 0 && i < index
            val active = i == index
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (done) green else if (active) accent else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))
                        .padding(vertical = 3.dp),
                )
                CaptionText(s.label, color = if (active) accent else null)
            }
        }
    }
}

@Composable
private fun RecordingState(recording: Boolean, busy: Boolean) {
    if (recording) {
        Gap(10.dp)
        Banner("Ich höre zu – sprich jetzt und tippe dann auf „Fertig“.", accent, icon = R.drawable.mic_24dp)
    }
    if (busy) {
        Gap(10.dp)
        ProgressLine(null)
        CaptionText("Die KI hört sich deine Antwort an …")
    }
}

@Composable
private fun GoalLine(ok: Boolean, text: String) {
    de.abicoach.ui.components.GoalRow(ok = ok, text = text)
    Gap(4.dp)
}

@Composable
private fun stateTint(state: PatternState) =
    when (state) {
        PatternState.AKTIV -> red
        PatternState.NEU -> yellow
        PatternState.BESSER -> accent
        PatternState.BEHOBEN -> green
    }
