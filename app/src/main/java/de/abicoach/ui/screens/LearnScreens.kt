package de.abicoach.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionKind
import de.abicoach.data.SessionRecord
import de.abicoach.learn.DrillType
import de.abicoach.learn.ErrorPattern
import de.abicoach.learn.PatternState
import de.abicoach.memory.DossierMaker
import de.abicoach.memory.ExamUnits
import de.abicoach.profile.Profile
import de.abicoach.session.LiveUiState
import de.abicoach.session.MicTest
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.Screen
import de.abicoach.ui.SessionSetup
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.AreaIntro
import de.abicoach.ui.components.Banner
import de.abicoach.ui.components.BarRow
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.BulletText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.ExpandableCard
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.GoalRow
import de.abicoach.ui.components.LoadingCard
import de.abicoach.ui.components.Pill
import de.abicoach.ui.components.PopUp
import de.abicoach.ui.components.ProgressLine
import de.abicoach.ui.components.StartButton
import de.abicoach.ui.components.Tab
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.pointsColor
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow

@Composable
private fun stateColor(state: PatternState): Color =
    when (state) {
        PatternState.AKTIV -> red
        PatternState.NEU -> yellow
        PatternState.BESSER -> accent
        PatternState.BEHOBEN -> green
    }

// ---------------------------------------------------------------------------------------------------------------
// Speaking training
// ---------------------------------------------------------------------------------------------------------------

/** Short spoken exercises on the student's own error patterns, checked by Gemini at once. */
@Composable
fun DrillsScreen(vm: AppViewModel) {
    val patterns by vm.app.errorMemory.patterns.collectAsState()
    val record = rememberMicGate { vm.drillStart() }
    val dimens = AbiTheme.dimens
    BackHandler { vm.closeDrills() }
    val current = vm.drills.getOrNull(vm.drillIndex)
    val result = vm.drillResult
    DetailScaffold(
        title = "Sprech-Training",
        onBack = { vm.closeDrills() },
        tag = "drills",
        bottom = {
            if (current != null && !vm.drillLoading) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        when {
                            vm.drillRecording ->
                                StartButton("Fertig", onClick = { vm.drillStop() }, icon = R.drawable.stop_24dp, iconTint = red, modifier = Modifier.testTag("drill_stop"))
                            result != null ->
                                StartButton(
                                    if (vm.drillIndex + 1 >= vm.drills.size && result.ok) "Auswertung" else "Weiter",
                                    onClick = { vm.tap(); vm.drillNext() },
                                    icon = R.drawable.arrow_forward_24dp,
                                    modifier = Modifier.testTag("drill_next"),
                                )
                            else ->
                                StartButton(
                                    if (vm.drillBusy) "Gemini hört zu …" else "Sprechen",
                                    onClick = { record() },
                                    enabled = !vm.drillBusy,
                                    icon = R.drawable.mic_24dp,
                                    modifier = Modifier.testTag("drill_record"),
                                )
                        }
                    }
                }
            }
        },
    ) {
        MessageBanners(vm)
        when {
            vm.drillLoading -> {
                val names = vm.drillFocus.mapNotNull { k -> patterns.firstOrNull { it.key == k }?.title }
                LoadingCard("Die KI schreibt Übungen zu deinen Fehlern …", detail = if (names.isEmpty()) null else "Heute: " + names.joinToString(", "))
            }
            vm.drills.isEmpty() ->
                AbiCard {
                    CardTitle("Noch keine Fehler gesammelt", icon = R.drawable.psychology_24dp)
                    BodyText("Sobald du übst, merkt sich die App jede Fehlerart. Hier trainierst du dann genau die Fehler, die dich Punkte kosten – mit Sätzen zu deinen Kursthemen.")
                    Gap(8.dp)
                    ActionButton(
                        "Gesprächstraining mit Coach",
                        R.drawable.forum_24dp,
                        accent,
                        { vm.tap(); vm.nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.TRAINING, 0, FeedbackMode.COACH))) },
                        container = MaterialTheme.colorScheme.background,
                    )
                }
            vm.drillDone -> DrillSummary(vm, patterns)
            current != null -> {
                val pattern = patterns.firstOrNull { it.key == current.patternKey }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CaptionText("Übung ${vm.drillIndex + 1} von ${vm.drills.size}")
                    Spacer(Modifier.width(10.dp))
                    ProgressLine((vm.drillIndex + 1f) / vm.drills.size, modifier = Modifier.weight(1f))
                }
                AbiCard(modifier = Modifier.testTag("drill_card")) {
                    FlowPills(listOfNotNull(current.type.label to accent, pattern?.let { it.title to stateColor(it.state) }))
                    Gap(10.dp)
                    Text(current.instruction, fontSize = dimens.fonts.label, lineHeight = dimens.fonts.label * 1.25f, color = MaterialTheme.colorScheme.onPrimary)
                    Gap(10.dp)
                    if (current.type == DrillType.SAY_RIGHT) {
                        CaptionText("So hast du es gesagt:")
                        Text("„${current.cue}“", fontSize = dimens.fonts.body, color = red, textDecoration = TextDecoration.LineThrough)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(current.cue, fontSize = dimens.fonts.body * 1.08f, lineHeight = dimens.fonts.body * 1.45f, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f))
                            SpeakButton(vm, current.cue)
                        }
                    }
                    if (vm.drillRecording) {
                        Gap(10.dp)
                        Banner("Ich höre zu – sprich jetzt und tippe dann auf „Fertig“.", accent, icon = R.drawable.mic_24dp)
                    }
                    if (vm.drillBusy) {
                        Gap(10.dp)
                        ProgressLine(null)
                        CaptionText("Gemini hört sich deine Antwort an …")
                    }
                    result?.let { r ->
                        Gap(10.dp)
                        Banner(r.tip.ifBlank { if (r.ok) "Richtig!" else "Noch nicht ganz." }, if (r.ok) green else yellow, icon = if (r.ok) R.drawable.task_alt_24dp else R.drawable.info_24dp)
                        if (r.heard.isNotBlank()) {
                            Gap(6.dp)
                            CaptionText("Verstanden habe ich: „${r.heard}“")
                        }
                        Gap(6.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                CaptionText(if (r.ok) "Musterlösung:" else "So geht es richtig:")
                                Text("„${current.target}“", fontSize = dimens.fonts.body, color = green)
                            }
                            SpeakButton(vm, current.target)
                        }
                        if (!r.ok) {
                            Gap(8.dp)
                            ActionButton("Gleich noch einmal", R.drawable.refresh_24dp, accent, { record() }, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("drill_again"))
                        }
                    }
                }
                if (result == null && !vm.drillRecording && !vm.drillBusy) {
                    CaptionText(
                        "Überspringen",
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.tap(); vm.drillSkip() }.padding(6.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DrillSummary(vm: AppViewModel, patterns: List<ErrorPattern>) {
    val firsts = vm.drillOutcomes.filter { !it.drill.id.endsWith("-again") }
    val right = firsts.count { it.ok }
    AbiCard(modifier = Modifier.testTag("drill_summary")) {
        CardTitle("Runde geschafft", icon = R.drawable.emoji_events_24dp, iconTint = yellow)
        BodyText("$right von ${firsts.size} Übungen auf Anhieb richtig.")
        Gap(8.dp)
        firsts.groupBy { it.drill.patternKey }.forEach { (key, list) ->
            val p = patterns.firstOrNull { it.key == key }
            val ok = list.all { it.ok }
            GoalRow(ok = ok, text = (p?.title ?: key) + if (ok) " – kommt in ${p?.let { daysUntil(it.dueAt) } ?: "ein paar"} Tagen wieder" else " – morgen noch einmal")
        }
        Gap(8.dp)
        CaptionText("Die App merkt sich das: Was sitzt, kommt seltener wieder; was noch hakt, bald wieder – und der Coach achtet im Gespräch genau darauf.")
        Gap(10.dp)
        ActionButton("Neue Runde", R.drawable.refresh_24dp, accent, { vm.tap(); vm.startDrills() }, container = MaterialTheme.colorScheme.background)
        Gap(8.dp)
        ActionButton("Fertig", R.drawable.check_24dp, green, { vm.closeDrills() }, container = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("drill_done"))
    }
}

private fun daysUntil(at: Long): Int = ((at - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(1)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowPills(items: List<Pair<String, Color>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (text, color) -> Pill(text, color) }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Error memory
// ---------------------------------------------------------------------------------------------------------------

/** Every kind of mistake with its state, examples and when it is due. */
@Composable
fun ErrorsScreen(vm: AppViewModel) {
    val patterns by vm.app.errorMemory.patterns.collectAsState()
    val now = remember(patterns) { System.currentTimeMillis() }
    val due = patterns.count { it.isDue(now) }
    DetailScaffold(title = "Fehlergedächtnis", onBack = { vm.tap(); vm.nav.pop() }, tag = "errors") {
        AreaIntro(
            R.drawable.psychology_24dp,
            "Dein Fehlergedächtnis",
            "Jede Fehlerart aus Übungen, Auswertungen und Klausuren – mit Beispielen und Verlauf. Der Coach unterbricht bei aktiven Fehlern, der Prüfer fragt gezielt danach, das Sprech-Training übt sie im Wiederholplan.",
        )
        MessageBanners(vm)
        if (patterns.isEmpty()) {
            AbiCard { BodyText("Noch keine Fehler gesammelt. Nach deiner ersten ausgewerteten Übung steht hier, woran du arbeiten solltest.") }
            return@DetailScaffold
        }
        if (due > 0) {
            ActionButton(
                "Sprech-Training starten",
                R.drawable.spellcheck_24dp,
                yellow,
                { vm.openDrills() },
                subtitle = "$due Fehlermuster sind fällig · ca. 5 Minuten",
                modifier = Modifier.testTag("errors_train"),
            )
        }
        listOf(PatternState.AKTIV, PatternState.NEU, PatternState.BESSER, PatternState.BEHOBEN).forEach { state ->
            val group = patterns.filter { it.state == state }.sortedByDescending { it.weight(now) }
            if (group.isEmpty()) return@forEach
            AbiCard {
                CardTitle(
                    when (state) {
                        PatternState.AKTIV -> "Aktiv (${group.size})"
                        PatternState.NEU -> "Neu (${group.size})"
                        PatternState.BESSER -> "Wird besser (${group.size})"
                        PatternState.BEHOBEN -> "Behoben (${group.size})"
                    },
                    icon =
                        when (state) {
                            PatternState.AKTIV -> R.drawable.warning_24dp
                            PatternState.NEU -> R.drawable.info_24dp
                            PatternState.BESSER -> R.drawable.trending_up_24dp
                            PatternState.BEHOBEN -> R.drawable.task_alt_24dp
                        },
                    iconTint = stateColor(state),
                )
                group.take(if (state == PatternState.BEHOBEN) 8 else 20).forEach { p ->
                    PatternRow(vm, p, now)
                    Gap(6.dp)
                }
            }
        }
    }
}

@Composable
private fun PatternRow(vm: AppViewModel, p: ErrorPattern, now: Long) {
    val dimens = AbiTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("pattern_row")) {
        Column(Modifier.weight(1f)) {
            Text(p.title, fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary)
            val extra = buildString {
                append(p.categoryLabel).append(" · ").append(p.total).append("× in ").append(p.sessions).append(if (p.sessions == 1) " Sitzung" else " Sitzungen")
                if (p.fromKlausur > 0) append(" · ").append(p.fromKlausur).append("× in Klausuren")
                if (p.cleanStreak > 0) append(" · seit ").append(p.cleanStreak).append(if (p.cleanStreak == 1) " Sitzung" else " Sitzungen").append(" weg")
                if (p.state != PatternState.BEHOBEN && p.isDue(now)) append(" · fällig")
            }
            CaptionText(extra)
            p.examples.firstOrNull()?.let { ex ->
                Text("„${ex.wrong}“ → „${ex.right}“", fontSize = dimens.fonts.caption, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            }
        }
        Pill(p.state.label, stateColor(p.state))
        if (p.state != PatternState.BEHOBEN) {
            IconButton(onClick = { vm.openDrills(listOf(p.key)) }) {
                Icon(painterResource(R.drawable.mic_24dp), contentDescription = "Trainieren", tint = accent, modifier = Modifier.size(dimens.icons.menuItem * 0.8f))
            }
        }
    }
}

/** The most important open error patterns (statistics and debrief). */
@Composable
fun ErrorMemoryCard(vm: AppViewModel) {
    val patterns by vm.app.errorMemory.patterns.collectAsState()
    if (patterns.isEmpty()) return
    val now = remember(patterns) { System.currentTimeMillis() }
    val open = patterns.filter { it.state != PatternState.BEHOBEN }.sortedByDescending { it.weight(now) }
    val fixed = patterns.count { it.state == PatternState.BEHOBEN }
    val due = patterns.count { it.isDue(now) }
    AbiCard(modifier = Modifier.testTag("stats_error_memory")) {
        CardTitle("Fehlergedächtnis", icon = R.drawable.psychology_24dp, iconTint = red)
        CaptionText("${open.size} offene Fehlerarten · $fixed behoben" + if (due > 0) " · $due fällig" else "")
        Gap(8.dp)
        open.take(4).forEach { p ->
            PatternRow(vm, p, now)
            Gap(6.dp)
        }
        Gap(4.dp)
        if (due > 0) {
            ActionButton("Sprech-Training", R.drawable.spellcheck_24dp, yellow, { vm.openDrills() }, subtitle = "$due Fehlermuster fällig", container = MaterialTheme.colorScheme.background)
            Gap(8.dp)
        }
        ActionButton("Alle Fehlerarten", R.drawable.format_list_bulleted_24dp, accent, { vm.tap(); vm.nav.push(Screen.Errors) }, container = MaterialTheme.colorScheme.background)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Profile: readiness and topic compass
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun ReadinessCard(profile: Profile) {
    val r = profile.readiness()
    if (r.part1 == null && r.part2 == null) return
    AbiCard(modifier = Modifier.testTag("stats_readiness")) {
        CardTitle("Prüfungsbereitschaft", icon = R.drawable.sports_score_24dp, iconTint = green)
        r.part1?.let { BarRow("Teil 1 – Vortrag", (it / 15).toFloat(), points(it) + " P.", pointsColor(it)) }
        Gap(6.dp)
        r.part2?.let { BarRow("Teil 2 – Prüfungsgespräch", (it / 15).toFloat(), points(it) + " P.", pointsColor(it)) }
        Gap(6.dp)
        CaptionText("Aus den Kompetenzen, die der jeweilige Teil verlangt (40 % Inhalt, 60 % Darstellung).")
    }
}

/** How well each topic field sits: practised (points), only material, or nothing yet. Tap opens the dossier. */
@Composable
fun TopicCompassCard(vm: AppViewModel) {
    val app = vm.app
    val profile by app.profiles.profile.collectAsState()
    val sessions by app.store.sessions.collectAsState()
    val docs by app.memory.docs.collectAsState()
    val practice = remember(sessions) { app.store.topicCounts() }
    val coverage = remember(docs) { app.memory.stats().chunksPerTopic }
    AbiCard(modifier = Modifier.testTag("stats_topics")) {
        CardTitle("Themen-Kompass", icon = R.drawable.hub_24dp)
        CaptionText("Beherrschung je Themenfeld aus deinen Sitzungen. Gelb: Material da, aber noch nicht geübt. Tippe für das Dossier.")
        Gap(8.dp)
        Knowledge.TOPICS.forEach { t ->
            val state = profile.topics[t.id]
            val n = practice[t.id] ?: 0
            val m = coverage.getOrElse(t.id) { 0 }
            val modifier = if (m > 0) Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.openDossier(t.id) } else Modifier
            when {
                state != null -> BarRow(t.short, (state.score / 15).toFloat(), points(state.score) + " P. · $n×", pointsColor(state.score), modifier = modifier)
                m > 0 -> BarRow(t.short, 0.02f, "noch nicht geübt", yellow, modifier = modifier)
                else -> BarRow(t.short, 0f, "–", MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f))
            }
            Gap(5.dp)
        }
    }
}

/** "↑" / "↓" after a skill when its last ratings clearly go up or down. */
fun trendMark(trend: Double?): String =
    when {
        trend == null -> ""
        trend >= 0.35 -> " ↑"
        trend <= -0.35 -> " ↓"
        else -> ""
    }

// ---------------------------------------------------------------------------------------------------------------
// Dossiers
// ---------------------------------------------------------------------------------------------------------------

/** The list of topic dossiers on the course page. */
@Composable
fun DossiersCard(vm: AppViewModel) {
    val app = vm.app
    val all by app.dossiers.all.collectAsState()
    val status by app.dossiers.status.collectAsState()
    val docs by app.memory.docs.collectAsState()
    val covered = remember(docs) { app.course.coveredTopics() }
    val stale = remember(docs, all) { covered.map { it.first }.filter { DossierMaker.isStale(app, it) }.toSet() }
    val dimens = AbiTheme.dimens
    AbiCard(modifier = Modifier.testTag("course_dossiers")) {
        CardTitle("Themen-Dossiers", icon = R.drawable.library_books_24dp)
        CaptionText("Die KI verdichtet dein ganzes Material zu jedem Themenfeld: Kernwissen, Beispiele, Zitate, Argumente und Verbindungen. Prüfer, Aufgaben und Coach greifen darauf zu.")
        Gap(8.dp)
        if (covered.isEmpty()) {
            BodyText("Sobald dein Material gelesen ist, entstehen hier die Dossiers.")
        }
        covered.forEach { (topic, chunks) ->
            val d = all[topic]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(dimens.components.cornerRadius)).clickable { vm.openDossier(topic) }.padding(vertical = 6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Knowledge.topicShort(topic), fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary)
                    CaptionText(
                        when {
                            status.working == topic || vm.dossierBusy == topic -> "wird gerade erstellt …"
                            d == null -> "noch nicht erstellt · $chunks Abschnitte Material"
                            topic in stale -> "neues Material da – wird aktualisiert"
                            else -> "${d.keyPoints.size} Kernpunkte · ${d.arguments.size} Argumente · ${d.links.size} Verbindungen"
                        },
                    )
                }
                Pill(if (d == null) "neu" else if (topic in stale) "veraltet" else "fertig", if (d == null) yellow else if (topic in stale) accent else green)
            }
        }
        status.error?.let {
            Gap(6.dp)
            CaptionText(it, color = red)
        }
        if (covered.isNotEmpty()) {
            Gap(8.dp)
            ActionButton(
                "Alle erstellen und aktualisieren",
                R.drawable.refresh_24dp,
                accent,
                { vm.tap(); vm.refreshDossiers() },
                subtitle = "Läuft im Hintergrund",
                container = MaterialTheme.colorScheme.background,
            )
        }
    }
}

/** The learning dossier of one topic field. */
@Composable
fun DossierScreen(vm: AppViewModel, topic: Int) {
    val all by vm.app.dossiers.all.collectAsState()
    val d = all[topic]
    val field = Knowledge.topic(topic)
    val dimens = AbiTheme.dimens
    DetailScaffold(title = "Dossier: ${Knowledge.topicShort(topic)}", onBack = { vm.tap(); vm.nav.pop() }, tag = "dossier") {
        MessageBanners(vm)
        if (d == null) {
            AbiCard {
                CardTitle(field?.english ?: "Topic field", icon = R.drawable.library_books_24dp)
                BodyText("Für dieses Themenfeld gibt es noch kein Dossier. Die KI liest dafür alles, was du dazu hochgeladen hast, und verdichtet es (etwa eine halbe Minute).")
                Gap(8.dp)
                if (vm.dossierBusy == topic) {
                    ProgressLine(null)
                    CaptionText("Wird erstellt …")
                } else {
                    ActionButton("Dossier jetzt erstellen", R.drawable.auto_awesome_24dp, accent, { vm.tap(); vm.makeDossier(topic) }, container = MaterialTheme.colorScheme.background)
                }
            }
            return@DetailScaffold
        }
        AbiCard(modifier = Modifier.testTag("dossier_overview")) {
            CardTitle(field?.english ?: "Topic field", icon = R.drawable.library_books_24dp)
            BodyText(d.overview)
            Gap(6.dp)
            CaptionText("Erstellt ${formatDate(d.madeAt)}" + if (d.by.isNotBlank()) " von ${d.by}" else "")
        }
        if (d.keyPoints.isNotEmpty()) {
            AbiCard {
                CardTitle("Kernwissen", icon = R.drawable.lightbulb_24dp, iconTint = yellow)
                d.keyPoints.forEach { BulletText(it) }
            }
        }
        if (d.examples.isNotEmpty()) {
            AbiCard {
                CardTitle("Beispiele aus deinem Kurs", icon = R.drawable.menu_book_24dp)
                d.examples.forEach { e ->
                    BulletText(e.text)
                    if (e.source.isNotBlank()) CaptionText("   " + e.source)
                }
            }
        }
        if (d.arguments.isNotEmpty()) {
            AbiCard {
                CardTitle("Argumente für Aufgabe 3 und Teil 2", icon = R.drawable.forum_24dp)
                d.arguments.forEach { a ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                        Pill(
                            when (a.side) {
                                "pro" -> "pro"
                                "contra" -> "contra"
                                else -> "Abwägung"
                            },
                            when (a.side) {
                                "pro" -> green
                                "contra" -> red
                                else -> accent
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            BodyText(a.claim)
                            if (a.support.isNotBlank()) CaptionText(a.support)
                        }
                    }
                }
            }
        }
        if (d.quotes.isNotEmpty()) {
            AbiCard {
                CardTitle("Zitate", icon = R.drawable.format_list_bulleted_24dp)
                d.quotes.forEach { q ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("„${q.text}“", fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary)
                            if (q.source.isNotBlank()) CaptionText(q.source)
                        }
                        SpeakButton(vm, q.text)
                    }
                    Gap(4.dp)
                }
            }
        }
        if (d.terms.isNotEmpty()) {
            ExpandableCard("Themenwortschatz", subtitle = "${d.terms.size} Wörter und Wendungen", icon = R.drawable.translate_24dp, initiallyOpen = false) {
                d.terms.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.en, fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary)
                            CaptionText(t.de)
                        }
                        SpeakButton(vm, t.en)
                    }
                }
            }
        }
        if (d.links.isNotEmpty()) {
            AbiCard {
                CardTitle("Verbindungen für den Themenwechsel", icon = R.drawable.account_tree_24dp)
                d.links.forEach { l ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.openDossier(l.topic) }.padding(vertical = 4.dp)) {
                        Text("→ " + Knowledge.topicShort(l.topic), fontSize = dimens.fonts.body, color = accent)
                        CaptionText(l.bridge)
                    }
                }
            }
        }
        if (d.ownIdeas.isNotEmpty()) {
            AbiCard {
                CardTitle("Deine eigenen guten Ideen", icon = R.drawable.thumb_up_24dp, iconTint = green)
                d.ownIdeas.forEach { BulletText(it) }
            }
        }
        if (d.questions.isNotEmpty()) {
            AbiCard {
                CardTitle("So könnte der Prüfer fragen", icon = R.drawable.quiz_24dp)
                d.questions.forEach { BulletText(it) }
                Gap(8.dp)
                ActionButton(
                    "Prüfungsgespräch zu diesem Thema",
                    R.drawable.forum_24dp,
                    accent,
                    { vm.tap(); vm.nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.PART2, 0, vm.app.settings.defaultFeedback).copy(topicChoice = topic))) },
                    container = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("dossier_talk"),
                )
            }
        }
        ActionButton(
            if (vm.dossierBusy == topic) "Wird neu erstellt …" else "Dossier neu erstellen",
            R.drawable.refresh_24dp,
            MaterialTheme.colorScheme.onPrimary,
            { vm.tap(); vm.makeDossier(topic) },
            enabled = vm.dossierBusy == null,
            subtitle = "Nach neuem Material – sonst aktualisiert es sich von selbst",
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Real exam tasks
// ---------------------------------------------------------------------------------------------------------------

/** Training tab: original exam tasks from the uploaded Abitur material. */
@Composable
fun RealTasksCard(vm: AppViewModel) {
    val docs by vm.app.memory.docs.collectAsState()
    val sessions by vm.app.store.sessions.collectAsState()
    val units = remember(docs) { ExamUnits.all(vm.app) }
    val used = remember(sessions) { ExamUnits.used(vm.app) }
    AbiCard(modifier = Modifier.testTag("train_real")) {
        CardTitle("Echte Prüfungsaufgaben", icon = R.drawable.workspace_premium_24dp, iconTint = green)
        if (units.isEmpty()) {
            CaptionText("Lade unter „Kurs“ echtes Abi-Material hoch (Art: „Echte Abi-Prüfungsaufgaben“ oder „Abi-Vorbereitung“). Die App findet darin Text, Original-Aufgaben und Erwartungshorizont – dann übst du hier genau diese Aufgaben.")
            Gap(8.dp)
            ActionButton("Material hochladen", R.drawable.cloud_upload_24dp, accent, { vm.tap(); vm.nav.openTab(Tab.KURS) }, container = MaterialTheme.colorScheme.background)
        } else {
            CaptionText("${units.size} Original-Aufgaben aus deinem Material · ${units.count { it.id !in used }} noch nicht geübt. Bewertet wird nach dem Original-Erwartungshorizont.")
            Gap(8.dp)
            ActionButton(
                "Nächste Original-Aufgabe",
                R.drawable.workspace_premium_24dp,
                green,
                {
                    vm.tap()
                    vm.nav.push(Screen.Setup(SessionSetup.defaults(SessionKind.PART1, 0, vm.app.settings.defaultFeedback).copy(source = Knowledge.Source.REAL, prepMinutes = 20)))
                },
                subtitle = "Vortrag mit den drei Original-Aufgaben",
                container = MaterialTheme.colorScheme.background,
            )
            Gap(8.dp)
            ActionButton("Alle Aufgaben ansehen", R.drawable.format_list_bulleted_24dp, accent, { vm.openRealTasks() }, container = MaterialTheme.colorScheme.background)
        }
    }
}

@Composable
fun RealTasksScreen(vm: AppViewModel) {
    val docs by vm.app.memory.docs.collectAsState()
    val sessions by vm.app.store.sessions.collectAsState()
    val units = remember(docs) { ExamUnits.all(vm.app) }
    val used = remember(sessions) { ExamUnits.used(vm.app) }
    DetailScaffold(title = "Echte Prüfungsaufgaben", onBack = { vm.tap(); vm.nav.pop() }, tag = "real_tasks") {
        if (units.isEmpty()) {
            AbiCard { BodyText("Noch keine Original-Aufgaben gefunden. Lade Abi-Material unter „Kurs“ hoch.") }
            return@DetailScaffold
        }
        units.forEach { u ->
            AbiCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.title, fontSize = AbiTheme.dimens.fonts.label, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f))
                    if (u.id in used) Pill("geübt", green)
                }
                CaptionText(u.source + " · " + Knowledge.topicShort(u.topic) + if (u.solution.isNotBlank()) " · mit Lösungsvorschlag" else "")
                Gap(6.dp)
                u.tasks.take(3).forEachIndexed { i, t -> BulletText("${i + 1}. ${t.instruction}") }
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Vortrag", R.drawable.co_present_24dp, accent, { vm.startRealTask(u.id, whole = false) }, modifier = Modifier.weight(1f), container = MaterialTheme.colorScheme.background)
                    ActionButton("Ganze Prüfung", R.drawable.workspace_premium_24dp, green, { vm.startRealTask(u.id, whole = true) }, modifier = Modifier.weight(1f), container = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Technical status of the live chain
// ---------------------------------------------------------------------------------------------------------------

/** Small chips under the live status: what works right now (microphone, transcript, ear, coach, examiner). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TechChips(state: LiveUiState, mode: FeedbackMode) {
    val d = state.diag
    val neutral = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("live_tech")) {
        Pill(if (d.micHeard) "Mikro ✓" else "Mikro: still", if (d.micHeard) green else if (state.elapsedSec > 8) yellow else neutral)
        Pill(
            "Mitschrift: " + d.transcript,
            when (d.transcript) {
                "läuft" -> green
                "aus" -> if (mode == FeedbackMode.PRUEFUNG) red else yellow
                else -> neutral
            },
        )
        if (mode != FeedbackMode.PRUEFUNG) {
            val earColor = if (d.checkFailures > 0 && d.checks == 0) red else if (d.heard > 0) green else neutral
            Pill("Ohr: ${d.heard}/${d.segments}" + if (d.checkFailures > 0) " · ${d.checkFailures} Störung" else "", earColor)
        }
        if (mode == FeedbackMode.COACH) {
            Pill("Coach: ${d.coachSpoken}" + if (d.repeatsOk > 0) " · ${d.repeatsOk} ✓" else "", if (d.coachSpoken > 0) green else neutral)
        }
        if (state.hasExaminer) {
            Pill(
                "Prüfer: " + d.examiner.ifBlank { "–" },
                when (d.examiner) {
                    "bereit" -> green
                    "Fehler" -> red
                    else -> neutral
                },
            )
        }
    }
}

/** Debrief: what the microphone, the ear and the coach did – with a button to copy it. */
@Composable
fun TechCard(record: SessionRecord) {
    if (record.tech.isBlank()) return
    val context = LocalContext.current
    ExpandableCard("Technik-Protokoll", subtitle = "Was Mikrofon, Ohr, Mitschrift und Coach gemacht haben", icon = R.drawable.tune_24dp, modifier = Modifier.testTag("debrief_tech")) {
        Text(record.tech, fontSize = AbiTheme.dimens.fonts.caption, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
        Gap(8.dp)
        ActionButton(
            "Protokoll kopieren",
            R.drawable.content_copy_24dp,
            accent,
            {
                runCatching {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("AbiCoach Technik-Protokoll", record.tech))
                }
            },
            subtitle = "Zum Einfügen in eine Nachricht, falls etwas nicht funktioniert",
            container = MaterialTheme.colorScheme.background,
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Microphone test
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun MicTestDialog(vm: AppViewModel) {
    val test = vm.micTest ?: return
    val st by test.state.collectAsState()
    val finished = st.phase == MicTest.Phase.DONE || st.phase == MicTest.Phase.FAILED
    PopUp(
        title = "Mikrofon-Test",
        description = "",
        confirmText = if (finished) "Schließen" else "Abbrechen",
        onConfirm = { vm.closeMicTest() },
        onDismiss = { vm.closeMicTest() },
        cancelText = null,
    ) {
        when (st.phase) {
            MicTest.Phase.FAILED -> Banner(st.message, red, icon = R.drawable.error_24dp)
            MicTest.Phase.DONE -> Banner(st.message, if (st.errors.isNotEmpty()) green else yellow, icon = if (st.errors.isNotEmpty()) R.drawable.task_alt_24dp else R.drawable.info_24dp)
            else -> BodyText(st.message)
        }
        Gap(10.dp)
        if (st.phase == MicTest.Phase.LISTENING) {
            ProgressLine(st.level.coerceIn(0f, 1f), color = if (st.level > 0.05f) green else accent)
            CaptionText("Pegel ${"%.2f".format(st.level)} · lautester bisher ${"%.2f".format(st.maxLevel)} · ${st.seconds} s")
        }
        if (st.phase == MicTest.Phase.CHECKING) ProgressLine(null)
        if (st.transcript.isNotBlank()) {
            GoalRow(ok = true, text = "Mikrofon und Pausen-Erkennung")
            GoalRow(ok = true, text = "Gemini hat verstanden (${"%.1f".format(st.ms / 1000.0)} s): „${st.transcript}“")
            GoalRow(ok = st.errors.isNotEmpty(), text = if (st.errors.isEmpty()) "Kein Fehler gefunden" else "Hinweis: " + st.errors.joinToString("; ") { "„${it.quote}“ → „${it.correction}“" })
            if (st.voice.isNotBlank()) GoalRow(ok = true, text = "Coach-Stimme: ${st.voice}")
        }
    }
}
