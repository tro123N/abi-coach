package de.abicoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.abicoach.R
import de.abicoach.ui.components.Tab
import de.abicoach.ui.components.dockSpace
import de.abicoach.ui.theme.accent

/** One stop of the guided tour: which area to show, which element to light up, and what it does. */
data class TourStep(
    val tab: Tab?,
    /** The element to light up (see [tourTarget]); null = a note in the middle of the screen. */
    val target: String?,
    val icon: Int,
    val title: String,
    val text: String,
)

/** The stops of the tour through the app, in order. */
object TourSteps {
    val all: List<TourStep> =
        listOf(
            TourStep(
                null, null, R.drawable.explore_24dp, "Kurze Tour durch AbiCoach",
                "Ich zeige dir in einer Minute, was wo ist. Tippe auf „Weiter“ – oder überspring die Tour, du findest sie jederzeit unter Einstellungen.",
            ),
            TourStep(
                Tab.WEG, "today_overview", R.drawable.trending_up_24dp, "Heute: deine Prognose",
                "Hier siehst du, wie viele Punkte du gerade bekommen würdest und wo du am Prüfungstag voraussichtlich stehst. Sie wird nach jeder Übung genauer.",
            ),
            TourStep(
                Tab.WEG, "today_plan", R.drawable.target_24dp, "Dein Plan für heute",
                "Die App rechnet aus, welche Übung dir gerade die meisten Punkte bringt, und erklärt es dir. Ein Tipp – und es geht los.",
            ),
            TourStep(
                Tab.WEG, "dock_TRAINING", R.drawable.mic_24dp, "Üben",
                "Hier unten wechselst du zwischen den vier Bereichen. Unter „Üben“ findest du alles, wofür du sprichst.",
            ),
            TourStep(
                Tab.TRAINING, "train_views", R.drawable.terrain_24dp, "Frei üben oder Abi-Weg",
                "„Frei üben“: du wählst selbst. „Abi-Weg“: Level für Level vom ersten Vortrag bis zur Generalprobe – mit Erklärung vor jeder Übung.",
            ),
            TourStep(
                Tab.TRAINING, "train_exam", R.drawable.school_24dp, "Die ganze Prüfung",
                "Die Simulation läuft wie im Abitur: neuer Text, Vorbereitungszeit, dein Vortrag (Teil 1) und das Prüfungsgespräch (Teil 2). Danach bewertet die KI nach dem NRW-Raster.",
            ),
            TourStep(
                Tab.TRAINING, "train_part2", R.drawable.forum_24dp, "Teil 1 und Teil 2 einzeln",
                "Einzelne Aufgaben, nur das Prüfungsgespräch oder ein freies Gespräch. Beim Einrichten wählst du, ob der Prüfer streng führt oder mehr auf dich eingeht.",
            ),
            TourStep(
                Tab.TRAINING, "train_target", R.drawable.psychology_24dp, "Gezielt besser werden",
                "Fehler-Werkstatt: deine typischen Fehler Schritt für Schritt beheben. Kompetenzen: jede der 11 Kompetenzen einzeln üben. Dazu Sprech-Training, Wortschatz und Aussprache.",
            ),
            TourStep(
                Tab.KURS, "course_add", R.drawable.menu_book_24dp, "Dein Kurs",
                "Lade Schulbuch, Klausuren und Arbeitsblätter hoch – als PDF oder Foto. Die KI liest alles und baut Prüfungstexte, Fragen und den Prüfer auf deinen Unterricht.",
            ),
            TourStep(
                Tab.KURS, "course_halfyears", R.drawable.calendar_clock_24dp, "Halbjahre",
                "Stell ein, welches Thema in welchem Halbjahr dran war. Dann mischt Teil 2 der Simulation die Halbjahre – wie im echten Abitur.",
            ),
            TourStep(
                Tab.STATS, "stats_development", R.drawable.insights_24dp, "Dein Fortschritt",
                "Der Graph zeigt, wie du über die Zeit besser wirst – gesamt, nach Inhalt und Sprache oder für jede Kompetenz. Darunter: Fehler, Themen und alle Sitzungen.",
            ),
            TourStep(
                Tab.WEG, "action_settings", R.drawable.settings_24dp, "Einstellungen",
                "Hier wählst du deine KI und trägst den Schlüssel ein, stellst den Prüfungstermin ein und sicherst deinen Lernstand als Datei.",
            ),
            TourStep(
                null, null, R.drawable.rocket_launch_24dp, "Bereit!",
                "Tipp für den Anfang: Mach die Einstufung (etwa 15 Minuten). Danach kennt die App deinen Stand und plant genau für dich.",
            ),
        )
}

/**
 * Where the elements of the tour are on screen. Elements report their place all the time (cheap, not observed);
 * only while the tour runs does a change redraw the overlay.
 */
class TourController {
    private val rects = HashMap<String, Rect>()

    /** The step shown, null while no tour runs. */
    var step by mutableStateOf<Int?>(null)
        internal set

    /** Changes whenever an element of the tour moved while the tour runs. */
    var tick by mutableIntStateOf(0)
        private set

    /** The list of each main area, so the tour can scroll an element into sight. */
    val lists = HashMap<Tab, LazyListState>()

    val active: Boolean get() = step != null

    fun place(key: String, rect: Rect) {
        if (rects[key] == rect) return
        rects[key] = rect
        if (active) tick++
    }

    fun forget(key: String) {
        if (rects.remove(key) != null && active) tick++
    }

    fun rect(key: String): Rect? = rects[key]
}

val LocalTour = staticCompositionLocalOf<TourController?> { null }

/** Marks an element the tour can light up. */
@Composable
fun Modifier.tourTarget(key: String): Modifier {
    val tour = LocalTour.current ?: return this
    DisposableEffect(tour, key) { onDispose { tour.forget(key) } }
    return this.onGloballyPositioned { tour.place(key, it.boundsInRoot()) }
}

/** Registers the list of a main area for the tour (so it can scroll to an element). */
@Composable
fun TourList(tab: Tab, state: LazyListState) {
    val tour = LocalTour.current ?: return
    DisposableEffect(tour, tab, state) {
        tour.lists[tab] = state
        onDispose { if (tour.lists[tab] === state) tour.lists.remove(tab) }
    }
}

/** The dimmed screen with a light around one element and a speech bubble that explains it. */
@Composable
fun TourOverlay(vm: AppViewModel) {
    val tour = vm.tour
    val index = tour.step ?: return
    val step = TourSteps.all.getOrNull(index) ?: return
    val density = LocalDensity.current
    BackHandler { vm.endTour() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("tour"),
    ) {
        val height = constraints.maxHeight.toFloat()
        val dock = with(density) { dockSpace().toPx() }
        val topLimit = with(density) { 44.dp.toPx() }
        // While the tour scrolls an element into sight, the light waits.
        var scrolling by androidx.compose.runtime.remember(index) { mutableStateOf(false) }
        LaunchedEffect(index) {
            val target = step.target ?: return@LaunchedEffect
            // The bar at the bottom is always in sight; the buttons of the title line once the area is at the top.
            if (target.startsWith("dock_")) return@LaunchedEffect
            if (target.startsWith("action_")) {
                step.tab?.let { tour.lists[it] }?.takeIf { it.canScrollBackward }?.let {
                    scrolling = true
                    try {
                        frames(6)
                        it.scrollToItem(0)
                        frames(3)
                    } finally {
                        scrolling = false
                    }
                }
                return@LaunchedEffect
            }
            frames(18)
            fun visible(r: Rect?) = r != null && r.top >= topLimit && r.bottom <= height - dock
            if (visible(tour.rect(target))) return@LaunchedEffect
            val list = step.tab?.let { tour.lists[it] }
            if (list == null) return@LaunchedEffect
            scrolling = true
            try {
                if (tour.rect(target) == null && list.canScrollBackward) {
                    list.scrollToItem(0)
                    frames(4)
                }
                repeat(14) {
                    val r = tour.rect(target)
                    if (visible(r)) return@LaunchedEffect
                    val delta =
                        when {
                            r == null -> height * 0.45f
                            r.bottom > height - dock -> minOf(r.bottom - (height - dock) + 40f, maxOf(r.top - topLimit - 20f, 0f))
                            else -> r.top - topLimit - 40f
                        }
                    if (kotlin.math.abs(delta) < 1f || (delta > 0 && !list.canScrollForward) || (delta < 0 && !list.canScrollBackward)) return@LaunchedEffect
                    list.scrollBy(delta)
                    frames(3)
                }
            } finally {
                scrolling = false
            }
        }
        @Suppress("UNUSED_VARIABLE")
        val redraw = tour.tick
        val rect = step.target?.takeIf { !scrolling }?.let { tour.rect(it) }
        val pad = with(density) { 8.dp.toPx() }
        val hole = rect?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
        val alpha by animateFloatAsState(if (scrolling) 0.6f else 1f, tween(220), label = "tourDim")
        val ring = accent
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.62f * alpha))
            if (hole != null) {
                val corner = CornerRadius(22.dp.toPx())
                drawRoundRect(Color.Transparent, Offset(hole.left, hole.top), Size(hole.width, hole.height), corner, blendMode = BlendMode.Clear)
                drawRoundRect(ring, Offset(hole.left, hole.top), Size(hole.width, hole.height), corner, style = Stroke(width = 3.dp.toPx()))
            }
        }
        val below = hole != null && hole.center.y < height / 2
        val bubble: @Composable () -> Unit = { TourBubble(vm, index, step) }
        when {
            hole == null ->
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { bubble() }
            below ->
                Box(
                    Modifier.fillMaxWidth().padding(top = with(density) { (hole.bottom + 14.dp.toPx()).toDp() }, start = 18.dp, end = 18.dp),
                    contentAlignment = Alignment.TopCenter,
                ) { bubble() }
            else ->
                Box(
                    Modifier.fillMaxWidth().height(with(density) { (hole.top - 14.dp.toPx()).coerceAtLeast(0f).toDp() }).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) { bubble() }
        }
    }
}

@Composable
private fun TourBubble(vm: AppViewModel, index: Int, step: TourStep) {
    val last = index == TourSteps.all.lastIndex
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 12.dp,
        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().testTag("tour_bubble"),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(step.icon), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(step.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f))
                Text("${index + 1}/${TourSteps.all.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f))
            }
            Spacer(Modifier.height(10.dp))
            Text(step.text, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!last) {
                    TextButton(onClick = { vm.endTour() }, modifier = Modifier.testTag("tour_skip")) {
                        Text("Überspringen", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (index > 0 && !last) {
                    TextButton(onClick = { vm.tourBack() }, modifier = Modifier.testTag("tour_back")) {
                        Text("Zurück", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                if (last && vm.app.store.sessions.value.none { it.kind == de.abicoach.data.SessionKind.PLACEMENT }) {
                    TextButton(onClick = { vm.endTour(); vm.nav.push(Screen.PlacementIntro) }, modifier = Modifier.testTag("tour_placement")) {
                        Text("Zur Einstufung", color = accent, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent,
                    modifier = Modifier.clip(RoundedCornerShape(50)).testTag(if (last) "tour_done" else "tour_next"),
                    onClick = { if (last) vm.endTour() else vm.tourNext() },
                ) {
                    Text(
                        if (last) "Fertig" else "Weiter",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** Waits [n] frames (follows the screen's own clock, also in the app test). */
private suspend fun frames(n: Int) = repeat(n) { androidx.compose.runtime.withFrameNanos { } }
