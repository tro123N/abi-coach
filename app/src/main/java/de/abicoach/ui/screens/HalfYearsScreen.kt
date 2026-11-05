package de.abicoach.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.data.Knowledge
import de.abicoach.memory.HalfYears
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.AreaIntro
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.MultiChoiceChips
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green

/**
 * Which topic field the course covered in which Kurshalbjahr. In a simulation part 2 then takes topic fields from
 * other half-years than part 1 – the oral Abitur must not stay with one half-year (§ 38 APO-GOSt).
 */
@Composable
fun HalfYearsScreen(vm: AppViewModel) {
    val app = vm.app
    val version by app.settings.version.collectAsState()
    val docs by app.memory.docs.collectAsState()
    val inferred = remember(docs) { HalfYears.inferred(app) }
    DetailScaffold(title = "Halbjahre", onBack = { vm.tap(); vm.nav.pop() }, tag = "halfyears") {
        if (version < 0) return@DetailScaffold
        AreaIntro(
            R.drawable.calendar_clock_24dp,
            "Was war wann dran?",
            "Im Abitur darf die mündliche Prüfung nicht bei einem Kurshalbjahr bleiben. Wenn die App weiß, welches Themenfeld ihr wann hattet, " +
                "nimmt sie in Teil 2 einer Simulation Themen aus anderen Halbjahren als in Teil 1 – so wie in der echten Prüfung.",
        )
        val manual = app.settings.halfYearTopics
        HalfYears.Q.forEach { half ->
            val set = manual[half]
            val shown = set ?: inferred.filterValues { it == half }.keys
            AbiCard(modifier = Modifier.testTag("half_$half")) {
                CardTitle(half, icon = R.drawable.event_24dp)
                when {
                    set != null -> CaptionText("Von dir eingestellt.")
                    shown.isNotEmpty() -> CaptionText("Aus deinem Material erkannt – tippe, um es zu ändern.", color = green)
                    else -> CaptionText("Noch nichts eingestellt.")
                }
                Gap(8.dp)
                MultiChoiceChips(
                    options = Knowledge.TOPICS.map { it.id },
                    selected = shown,
                    label = { Knowledge.topicShort(it) },
                    onToggle = { t ->
                        vm.tap()
                        val next = if (t in shown) shown - t else shown + t
                        app.settings.halfYearTopics = app.settings.halfYearTopics + (half to next)
                    },
                    tagPrefix = "half_${half}_topic",
                )
            }
        }
        if (manual.isNotEmpty()) {
            ActionButton(
                "Wieder aus dem Material übernehmen",
                R.drawable.refresh_24dp,
                accent,
                { vm.tap(); app.settings.halfYearTopics = emptyMap() },
                container = MaterialTheme.colorScheme.background,
            )
        }
        CaptionText("Tipp: Beim Hochladen von Material kannst du das Halbjahr angeben – dann erkennt die App es von selbst.")
    }
}
