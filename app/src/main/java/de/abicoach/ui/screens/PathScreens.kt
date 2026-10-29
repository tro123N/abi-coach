package de.abicoach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.data.FeedbackMode
import de.abicoach.data.Knowledge
import de.abicoach.path.Level
import de.abicoach.path.LevelType
import de.abicoach.path.PathEngine
import de.abicoach.path.PathProgress
import de.abicoach.path.World
import de.abicoach.profile.Profile
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.Screen
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.AreaIntro
import de.abicoach.ui.components.Banner
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.BulletText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.ChoiceChips
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.GoalRow
import de.abicoach.ui.components.LevelBadge
import de.abicoach.ui.components.MarkdownText
import de.abicoach.ui.components.MiniAction
import de.abicoach.ui.components.StarRow
import de.abicoach.ui.components.StartButton
import de.abicoach.ui.components.Tab
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.yellow

/** The climbing card of the Abi-Weg: refresh, continue, and the small shortcuts. */
@Composable
fun ClimbCard(vm: AppViewModel, worlds: List<World>, progress: PathProgress, profile: Profile) {
    val dimens = AbiTheme.dimens
    val next = PathEngine.next(worlds, progress, profile)
    val due = PathEngine.due(worlds, progress)
    val (stars, maxStars) = PathEngine.starsTotal(worlds, progress)
    val all = PathEngine.all(worlds).filter { it.level.type != LevelType.LEARN }
    val passed = all.count { progress.of(it.level.id).passed }
    AbiCard(modifier = Modifier.testTag("climb")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("$passed von ${all.size} Levels", fontSize = dimens.fonts.label, color = MaterialTheme.colorScheme.onPrimary)
                CaptionText("$stars von $maxStars Sternen")
            }
            StarRow(stars = if (maxStars == 0) 0 else (3 * stars + maxStars - 1) / maxStars, size = dimens.icons.status * 0.8f)
        }
        Gap(12.dp)
        due.firstOrNull()?.let { d ->
            val rusty = progress.of(d.level.id).rusty
            ActionButton(
                text = "Auffrischen: ${d.level.title}",
                subtitle = if (due.size > 1) "Noch ${due.size - 1} weitere fällig" else if (rusty) "Eingerostet – erst auffrischen, dann geht es weiter" else "Wiederholen, damit es sitzt",
                icon = R.drawable.refresh_24dp,
                iconTint = yellow,
                onClick = { vm.openLevel(d.level.id) },
                container = MaterialTheme.colorScheme.background,
                modifier = Modifier.testTag("climb_refresh"),
            )
            Gap(8.dp)
        }
        if (next != null) {
            ActionButton(
                text = "Weiterklettern",
                subtitle = "Welt ${next.world.number}: ${next.level.title}",
                icon = R.drawable.terrain_24dp,
                iconTint = green,
                onClick = { vm.openLevel(next.level.id) },
                container = MaterialTheme.colorScheme.background,
                modifier = Modifier.testTag("climb_next"),
            )
        } else {
            BodyText("Du hast alle Levels geschafft. Wiederhole regelmäßig, damit nichts einrostet.")
        }
        Gap(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniAction(R.drawable.flag_24dp, "Einstufung", { vm.tap(); vm.nav.push(Screen.PlacementIntro) }, Modifier.weight(1f))
            MiniAction(R.drawable.record_voice_over_24dp, "Training", { vm.tap(); vm.nav.openTab(Tab.TRAINING) }, Modifier.weight(1f))
            MiniAction(R.drawable.bar_chart_24dp, "Statistik", { vm.tap(); vm.nav.openTab(Tab.STATS) }, Modifier.weight(1f))
        }
    }
}

/** One world with its levels, opened like in the Kletterweg. */
@Composable
fun WorldCard(
    vm: AppViewModel,
    world: World,
    worldIndex: Int,
    worlds: List<World>,
    progress: PathProgress,
    profile: Profile,
    nextId: String?,
    openByDefault: Boolean,
) {
    val dimens = AbiTheme.dimens
    var open by rememberSaveable(world.id) { mutableStateOf(openByDefault) }
    val unlocked = PathEngine.isWorldUnlocked(worlds, worldIndex, progress, profile)
    val scored = world.levels.filter { it.type != LevelType.LEARN }
    val stars = scored.sumOf { progress.of(it.id).stars }
    val done = world.levels.count { progress.of(it.id).passed }
    val attention = world.levels.any { progress.of(it.id).let { p -> p.passed && p.rusty } }
    AbiCard(modifier = Modifier.alpha(if (unlocked) 1f else 0.6f).testTag("world_${world.id}")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.components.cornerRadius))
                    .clickable { vm.tap(); open = !open },
        ) {
            LevelBadge(
                label = world.number.toString(),
                locked = !unlocked,
                mastered = done == world.levels.size,
                attention = attention,
                highlighted = unlocked && done < world.levels.size,
                size = dimens.components.button * 0.75f,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(world.title, fontSize = dimens.fonts.label, lineHeight = dimens.fonts.label * 1.15f, color = MaterialTheme.colorScheme.onPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CaptionText("${world.subtitle} · $done/${world.levels.size}" + if (scored.isNotEmpty()) " · $stars★" else "")
            }
            Icon(
                painter = painterResource(if (open) R.drawable.keyboard_arrow_up_24dp else R.drawable.keyboard_arrow_down_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(dimens.icons.menuItem),
            )
        }
        if (open) {
            Gap(10.dp)
            world.levels.forEachIndexed { i, level ->
                val p = progress.of(level.id)
                val levelUnlocked = PathEngine.isLevelUnlocked(world, i, progress, unlocked)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(dimens.components.cornerRadius))
                            .clickable { vm.openLevel(level.id) }
                            .testTag("level_${level.id}")
                            .padding(vertical = 6.dp),
                ) {
                    LevelBadge(
                        label = (i + 1).toString(),
                        locked = !levelUnlocked,
                        mastered = p.passed,
                        attention = p.passed && p.rusty,
                        highlighted = level.id == nextId,
                        size = dimens.components.button * 0.6f,
                        icon = if (level.type == LevelType.LEARN && !p.passed && levelUnlocked) R.drawable.menu_book_24dp else null,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            level.title,
                            fontSize = dimens.fonts.body,
                            lineHeight = dimens.fonts.body * 1.2f,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (levelUnlocked) 1f else 0.6f),
                        )
                        CaptionText("${level.type.label} · ${level.method}")
                    }
                    if (level.type != LevelType.LEARN) {
                        Spacer(Modifier.width(8.dp))
                        StarRow(stars = p.stars, size = dimens.icons.status * 0.7f)
                    }
                }
            }
        }
    }
}

private fun practiceText(level: Level): String =
    when (level.type) {
        LevelType.LEARN -> ""
        LevelType.TASK ->
            "Du bekommst einen neuen kurzen Text, passend zu deinem Kurs, und bearbeitest nur Aufgabe ${level.focusTask}. " +
                "Etwa ${level.minutes} Minuten mit Vorbereitung."
        LevelType.PRESENTATION -> "Ein neuer Text mit allen drei Aufgaben: Vorbereitung, dann der ganze Vortrag. Etwa ${level.minutes} Minuten."
        LevelType.TALK -> "Ein Gespräch mit dem KI-Prüfer, etwa ${level.minutes} Minuten."
        LevelType.SIMULATION -> "Eine komplette Prüfung wie im Abi, etwa ${level.minutes} Minuten."
    }

/** One level: goal and stars, the explanation, and the exercise. */
@Composable
fun LevelScreen(vm: AppViewModel, id: String) {
    val app = vm.app
    val progress by app.path.progress.collectAsState()
    val profile by app.profiles.profile.collectAsState()
    val worlds = remember { app.worlds() }
    val located = PathEngine.locate(worlds, id)
    if (located == null) {
        DetailScaffold(title = "Level", onBack = { vm.nav.pop() }) { BodyText("Dieses Level gibt es nicht.") }
        return
    }
    val level = located.level
    val world = located.world
    val p = progress.of(id)
    val unlocked = PathEngine.isUnlocked(worlds, id, progress, profile)
    var feedback by remember { mutableStateOf(if (p.attempts == 0) FeedbackMode.COACH else app.settings.defaultFeedback) }
    val start = rememberMicGate { vm.startLevel(id, feedback) }
    val dimens = AbiTheme.dimens
    val t = PathEngine.thresholds(level)

    DetailScaffold(
        title = level.title,
        onBack = { vm.tap(); vm.stopSpeaking(); vm.nav.pop() },
        tag = "level",
        bottom = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    when {
                        !unlocked -> StartButton("Noch gesperrt", onClick = {}, enabled = false, icon = R.drawable.lock_24dp)
                        level.type == LevelType.LEARN ->
                            StartButton(
                                if (p.passed) "Gelesen" else "Verstanden",
                                onClick = {
                                    vm.markLearned(id)
                                    vm.nav.pop()
                                },
                                icon = R.drawable.check_24dp,
                                modifier = Modifier.testTag("level_done"),
                            )
                        else -> StartButton(if (level.strict) "Prüfung starten" else "Üben", onClick = start, modifier = Modifier.testTag("level_start"))
                    }
                }
            }
        },
    ) {
        AreaIntro(world.icon, "Welt ${world.number}: ${world.title}", level.method)
        MessageBanners(vm)
        if (!unlocked) Banner("Schaffe zuerst das Level davor – oder mach die Einstufung, dann öffnen sich Welten, die du schon kannst.", yellow, icon = R.drawable.lock_24dp)
        if (p.rusty) Banner("Eingerostet: Beim letzten Wiederholen hat es nicht ganz gereicht. Frisch es auf, bevor du weiterkletterst.", yellow, icon = R.drawable.refresh_24dp)
        if (level.type != LevelType.LEARN) {
            AbiCard(modifier = Modifier.testTag("level_goal")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarRow(stars = p.stars, size = dimens.icons.scoreHeader * 0.5f)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (p.passed) "Geschafft" else "Noch offen",
                        fontSize = dimens.fonts.label,
                        color = if (p.passed) green else MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Gap(8.dp)
                GoalRow(ok = p.best >= t.one, text = "★ ab ${t.one} Punkten – das Ziel", neutral = p.best < t.one)
                GoalRow(ok = p.best >= t.two, text = "★★ ab ${t.two} Punkten", neutral = p.best < t.two)
                GoalRow(ok = p.best >= t.three, text = "★★★ ab ${t.three} Punkten", neutral = p.best < t.three)
                CaptionText(
                    "Gemessen: " + (level.skill?.label ?: "Gesamtpunkte") +
                        (if (level.focusTask in 1..3) " (Aufgabe ${level.focusTask})" else "") +
                        (if (p.attempts > 0) " · bestes Ergebnis ${p.best} P. · ${p.attempts} ${if (p.attempts == 1) "Versuch" else "Versuche"}" else ""),
                )
            }
        }
        AbiCard(modifier = Modifier.testTag("level_learn")) {
            CardTitle("Lernen", icon = R.drawable.menu_book_24dp)
            MarkdownText(level.learn)
            if (level.example.isNotBlank()) {
                Gap(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "„${level.example}“",
                        fontSize = dimens.fonts.body,
                        lineHeight = dimens.fonts.body * 1.35f,
                        fontStyle = FontStyle.Italic,
                        color = accent,
                        modifier = Modifier.weight(1f),
                    )
                    SpeakButton(vm, level.example)
                }
                CaptionText("Zum Anhören und Nachsprechen.")
            }
        }
        if (level.type == LevelType.LEARN && level.topicId != null) TopicMaterialCard(vm, level.topicId)
        if (level.type != LevelType.LEARN) {
            AbiCard {
                CardTitle("Übung", icon = R.drawable.mic_24dp, iconTint = green)
                BodyText(practiceText(level))
                Gap(10.dp)
                if (level.strict) {
                    CaptionText("Unter Prüfungsbedingungen: keine Korrekturen beim Sprechen, die Auswertung kommt am Ende.")
                } else {
                    BodyText("Rückmeldung beim Sprechen")
                    Gap(6.dp)
                    ChoiceChips(FeedbackMode.entries, feedback, { it.label }, { vm.tap(); feedback = it }, tagPrefix = "levelfeedback")
                    Gap(6.dp)
                    CaptionText(feedback.description)
                }
            }
        }
    }
}

@Composable
private fun TopicMaterialCard(vm: AppViewModel, topicId: Int) {
    val app = vm.app
    val facts = remember(topicId) { app.course.factsFor(topicId, 20) }
    val vocab =
        remember(topicId) {
            app.memory.allDigests().flatMap { (_, d) -> d.vocabulary.filter { it.topic == topicId } }.distinctBy { it.en.lowercase() }.take(20)
        }
    AbiCard {
        CardTitle("Aus deinem Material", icon = R.drawable.school_24dp)
        if (facts.isEmpty() && vocab.isEmpty()) {
            BodyText("Zu „${Knowledge.topicShort(topicId)}“ hat die App noch kein Material. Lade unter „Mein Kurs“ hoch, was ihr dazu im Unterricht hattet.")
            Gap(8.dp)
            ActionButton("Zu Mein Kurs", R.drawable.cloud_upload_24dp, accent, { vm.tap(); vm.nav.openTab(Tab.KURS) }, container = MaterialTheme.colorScheme.background)
        } else {
            facts.forEach { BulletText(it) }
            if (vocab.isNotEmpty()) {
                Gap(10.dp)
                BodyText("Wortschatz", color = accent)
                vocab.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                            BodyText(v.en)
                            CaptionText(v.de)
                        }
                        SpeakButton(vm, v.en)
                    }
                }
            }
            Gap(8.dp)
            ActionButton("Frag dein Material", R.drawable.forum_24dp, accent, { vm.tap(); vm.nav.push(Screen.Ask) }, container = MaterialTheme.colorScheme.background)
        }
    }
}

/** Shown at the top of the debrief when the session belonged to a level. */
@Composable
fun LevelResultCard(vm: AppViewModel, levelId: String, points: Int) {
    val level = de.abicoach.path.Curriculum.level(levelId) ?: return
    val dimens = AbiTheme.dimens
    val stars = PathEngine.stars(level, points)
    val passed = points >= level.goal
    val worlds = remember { vm.app.worlds() }
    val progress by vm.app.path.progress.collectAsState()
    val profile by vm.app.profiles.profile.collectAsState()
    val next = PathEngine.next(worlds, progress, profile)
    AbiCard(border = if (passed) green.copy(alpha = 0.6f) else null, modifier = Modifier.testTag("level_result")) {
        CardTitle("Level: ${level.title}", icon = R.drawable.terrain_24dp, iconTint = green)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarRow(stars = stars, size = dimens.icons.scoreHeader * 0.5f)
            Spacer(Modifier.width(12.dp))
            Text(if (passed) "Geschafft!" else "Noch nicht geschafft", fontSize = dimens.fonts.label, color = if (passed) green else MaterialTheme.colorScheme.onPrimary)
        }
        CaptionText("Gemessen: ${level.skill?.label ?: "Gesamtpunkte"} · $points P. (Ziel: ${level.goal} P.)")
        Gap(10.dp)
        if (passed && next != null && next.level.id != levelId) {
            ActionButton("Weiter: ${next.level.title}", R.drawable.terrain_24dp, green, { vm.openLevel(next.level.id) }, subtitle = "Das nächste Level", container = MaterialTheme.colorScheme.background)
            Gap(8.dp)
        }
        ActionButton("Zum Level", R.drawable.arrow_back_24dp, MaterialTheme.colorScheme.onPrimary, { vm.openLevel(levelId) }, subtitle = "Nochmal oder nachlesen", container = MaterialTheme.colorScheme.background)
    }
}
