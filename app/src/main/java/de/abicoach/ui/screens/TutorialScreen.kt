package de.abicoach.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.Screen
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.StartButton
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.yellow
import kotlinx.coroutines.launch

private data class TutorialPage(val icon: Int, val tint: Color, val title: String, val lines: List<String>)

private fun pages(): List<TutorialPage> =
    listOf(
        TutorialPage(
            R.drawable.school_24dp,
            Color(0xFF3D7BFF),
            "Willkommen bei AbiCoach",
            listOf(
                "Dein Trainer für die mündliche Englisch-Prüfung im Abitur (NRW, Grundkurs).",
                "Du übst genau wie in der Prüfung: Teil 1 ist dein Vortrag zu einem neuen Text, Teil 2 das Prüfungsgespräch.",
                "Die KI ist Prüfer, Coach und Bewerterin – nach dem NRW-Raster.",
            ),
        ),
        TutorialPage(
            R.drawable.key_24dp,
            Color(0xFFF2A93B),
            "Deine KI",
            listOf(
                "Unter Einstellungen → KI wählst du, wer alles macht: Google, OpenAI oder Anthropic.",
                "Dort trägst du den Schlüssel der Firma ein. Er bleibt verschlüsselt auf deinem Handy.",
                "Mit „System prüfen“ testest du in einer Minute, ob alles läuft.",
            ),
        ),
        TutorialPage(
            R.drawable.menu_book_24dp,
            Color(0xFF2FB67C),
            "Dein Kurs",
            listOf(
                "Unter „Kurs“ lädst du Schulbuch, Klausuren und Arbeitsblätter hoch – als Foto oder PDF.",
                "Die App liest alles und baut Texte, Fragen und den Prüfer genau auf deinen Unterricht.",
                "Unter „Halbjahre“ siehst du, welches Thema wann dran war – Teil 2 der Simulation mischt die Halbjahre wie im Abi.",
            ),
        ),
        TutorialPage(
            R.drawable.mic_24dp,
            Color(0xFF3D7BFF),
            "Üben",
            listOf(
                "Simulation, Vortrag, Prüfungsgespräch oder freies Training – du wählst.",
                "Rückmeldung: Prüfung (wie im Abi, alles danach), Flüstern (Hinweise auf dem Bildschirm) oder Coach (unterbricht sofort).",
                "Beim Gespräch stellst du ein, ob der Prüfer streng führt oder mehr auf dich eingeht.",
            ),
        ),
        TutorialPage(
            R.drawable.chat_24dp,
            Color(0xFF8A63D2),
            "Während du sprichst",
            listOf(
                "Der kleine Knopf „Mitschrift“ zeigt, was der Prüfer, der Coach und du gesagt habt.",
                "Fehler erscheinen oben groß – mit der richtigen Form.",
                "Tipp: Mit Kopfhörern kannst du den Prüfer auch unterbrechen.",
            ),
        ),
        TutorialPage(
            R.drawable.grading_24dp,
            Color(0xFFE5534B),
            "Die Auswertung",
            listOf(
                "Nach jeder Übung bewertet die KI nach dem NRW-Raster: Inhalt 40 %, Darstellung 60 %.",
                "Du bekommst Punkte, deine Fehler, 15-Punkte-Sätze zum Anhören und deine nächsten Schritte.",
                "Nicht ausgewertete Sitzungen wertest du aus oder löschst sie – du entscheidest.",
            ),
        ),
        TutorialPage(
            R.drawable.target_24dp,
            Color(0xFFF2A93B),
            "Gezielt besser werden",
            listOf(
                "Fehler-Werkstatt: Deine typischen Fehler, Schritt für Schritt behoben – die KI merkt sich, was dir hilft.",
                "Kompetenzen üben: Jede der 11 Kompetenzen des Rasters einzeln trainieren.",
                "Abi-Weg: Level für Level bis zur Prüfung.",
            ),
        ),
        TutorialPage(
            R.drawable.trending_up_24dp,
            Color(0xFF2FB67C),
            "Dein Fortschritt",
            listOf(
                "Unter „Fortschritt“ siehst du deine Prognose und im Graphen, wie du über die Zeit besser wirst.",
                "Unter Einstellungen → Daten sicherst du deinen ganzen Lernstand als Datei und holst ihn auf einem neuen Handy zurück.",
                "Viel Erfolg – fang am besten mit der Einstufung an!",
            ),
        ),
    )

/** The tutorial at the very first start: eight short pages, can be skipped and opened again in the settings. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(vm: AppViewModel) {
    val list = pages()
    val pager = rememberPagerState { list.size }
    val scope = rememberCoroutineScope()
    val dimens = AbiTheme.dimens
    val last = pager.currentPage == list.lastIndex
    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else vm.finishTutorial()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("tutorial")) {
        // Fixed height, so the page number does not jump when "Überspringen" disappears on the last page.
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${pager.currentPage + 1} / ${list.size}", fontSize = dimens.fonts.caption, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            if (!last) {
                TextButton(onClick = { vm.finishTutorial() }, modifier = Modifier.testTag("tutorial_skip")) {
                    Text("Überspringen", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).testTag("tutorial_pager")) { index ->
            val page = list[index]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(128.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(page.tint.copy(alpha = 0.95f), page.tint.copy(alpha = 0.55f)))),
                ) {
                    Icon(painterResource(page.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text(page.title, fontSize = dimens.fonts.label * 1.35f, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.height(18.dp))
                page.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 8.dp).size(7.dp).clip(CircleShape).background(page.tint))
                        Spacer(Modifier.width(12.dp))
                        Text(line, fontSize = dimens.fonts.body, lineHeight = dimens.fonts.body * 1.4f, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            list.indices.forEach { i ->
                val active = i == pager.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(if (active) 22.dp else 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (active) accent else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            if (last) {
                if (!vm.app.ai.ready) {
                    ActionButton(
                        "KI-Schlüssel eintragen",
                        R.drawable.key_24dp,
                        yellow,
                        {
                            vm.finishTutorial()
                            vm.nav.push(Screen.Settings)
                        },
                        modifier = Modifier.testTag("tutorial_keys"),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                StartButton("Los geht's", onClick = { vm.finishTutorial() }, iconTint = green, modifier = Modifier.testTag("tutorial_done"))
            } else {
                StartButton(
                    "Weiter",
                    onClick = {
                        vm.tap()
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    icon = R.drawable.arrow_forward_24dp,
                    modifier = Modifier.testTag("tutorial_next"),
                )
            }
        }
    }
}
