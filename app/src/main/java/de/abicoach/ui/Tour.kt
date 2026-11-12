package de.abicoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import de.abicoach.ui.components.squish
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
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
                "Jeden Tag macht die App dir einen Plan: erst wiederholen, dann dein teuerster Fehler, dann die Übung mit den meisten Punkten. Mach die Schritte von oben nach unten – jeder wird abgehakt und zeigt, was er gebracht hat.",
            ),
            TourStep(
                Tab.WEG, "dock_TRAINING", R.drawable.mic_24dp, "Üben",
                "Hier unten wechselst du zwischen den vier Bereichen. Unter „Üben“ findest du alles, wofür du sprichst.",
            ),
            TourStep(
                Tab.TRAINING, "train_views", R.drawable.terrain_24dp, "Frei üben oder Abi-Weg",
                "Ganz oben steht immer der nächste Schritt deines Plans. „Frei üben“: du wählst selbst – tippe auf eine Überschrift, um die Übungen aufzuklappen. „Abi-Weg“: Level für Level bis zur Generalprobe.",
            ),
            TourStep(
                Tab.TRAINING, "train_exam", R.drawable.school_24dp, "Die ganze Prüfung",
                "Die Simulation läuft wie im Abitur: neuer Text, Vorbereitungszeit, dein Vortrag (Teil 1) und das Prüfungsgespräch (Teil 2). Danach bewertet die KI nach dem NRW-Raster.",
            ),
            TourStep(
                Tab.TRAINING, "train_part2", R.drawable.forum_24dp, "Teil 2 einzeln üben",
                "Nur das Prüfungsgespräch, ein freies Training oder ein Gespräch zu deinen Schwachstellen. Beim Einrichten wählst du, wie der Prüfer führt und wie streng er ist.",
            ),
            TourStep(
                Tab.TRAINING, "train_target", R.drawable.psychology_24dp, "Gezielt besser werden",
                "Fehler-Werkstatt: deine typischen Fehler Schritt für Schritt beheben. Kompetenzen: jede der 11 Kompetenzen einzeln üben. Dazu Sprech-Training, Wortschatz und Aussprache.",
            ),
            TourStep(
                Tab.KURS, "course_add", R.drawable.menu_book_24dp, "Dein Kurs",
                "Ein Knopf für alles: Schulbuch, Klausuren, Arbeitsblätter – als PDF, Foto, Word oder PowerPoint. Die KI liest alles und baut Prüfungstexte, Fragen und den Prüfer auf deinen Unterricht. Echte Prüfungsaufgaben lädst du darunter getrennt hoch.",
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
                "Das Zahnrad ist auf allen vier Seiten immer oben rechts. Dort wählst du deine KI und trägst den Schlüssel ein, stellst den Prüfungstermin ein und sicherst deinen Lernstand als Datei.",
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

/**
 * The dimmed screen with a light around one element and a card that explains it. Everything moves softly: the
 * light glides from element to element, the page scrolls instead of jumping, and the card keeps its place at the
 * bottom of the screen whenever it can – so "Weiter" stays under the thumb and nothing hops around.
 */
@Composable
fun TourOverlay(vm: AppViewModel) {
    val tour = vm.tour
    val live = tour.step
    // The last step stays on screen while the tour fades out.
    var lastIndex by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.SideEffect { if (live != null) lastIndex = live }
    val appear by animateFloatAsState(if (live != null) 1f else 0f, tween(if (live != null) 320 else 220), label = "tourAppear")
    if (live == null && appear <= 0.01f) return
    val index = live ?: lastIndex
    val step = TourSteps.all.getOrNull(index) ?: return
    val density = LocalDensity.current
    if (live != null) BackHandler { vm.endTour() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear }
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("tour"),
    ) {
        val height = constraints.maxHeight.toFloat()
        val dock = with(density) { dockSpace().toPx() }
        val topLimit = with(density) { 44.dp.toPx() }
        // Room the card needs at the bottom: an element above this line is not covered by it.
        val low = height - dock - with(density) { 150.dp.toPx() }
        // While the tour scrolls an element into sight, the light waits.
        var scrolling by remember(index) { mutableStateOf(false) }
        LaunchedEffect(index, live != null) {
            if (live == null) return@LaunchedEffect
            val target = step.target ?: return@LaunchedEffect
            // The bar at the bottom and the settings button are always in sight.
            if (target.startsWith("dock_") || target.startsWith("action_")) return@LaunchedEffect
            frames(18)
            fun visible(r: Rect?) = r != null && r.top >= topLimit && r.bottom <= low
            if (visible(tour.rect(target))) return@LaunchedEffect
            val list = step.tab?.let { tour.lists[it] }
            if (list == null) return@LaunchedEffect
            scrolling = true
            try {
                if (tour.rect(target) == null && list.canScrollBackward) {
                    list.animateScrollToItem(0)
                    frames(4)
                }
                repeat(8) {
                    val r = tour.rect(target)
                    if (visible(r)) return@LaunchedEffect
                    // The element glides to the top of the screen, so the card below it never has to move.
                    val delta = if (r == null) height * 0.45f else r.top - topLimit - 24f
                    if (kotlin.math.abs(delta) < 2f || (delta > 0 && !list.canScrollForward) || (delta < 0 && !list.canScrollBackward)) return@LaunchedEffect
                    list.animateScrollBy(delta, tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                    frames(3)
                }
            } catch (e: Exception) {
                // Scrolling is only a help: if it fails, the card simply explains without the light.
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                scrolling = false
            }
        }
        @Suppress("UNUSED_VARIABLE")
        val redraw = tour.tick
        val pad = with(density) { 8.dp.toPx() }
        val goal = step.target?.let { tour.rect(it) }?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
        val lit = goal != null && !scrolling
        val light by animateFloatAsState(if (lit) 1f else 0f, tween(if (lit) 300 else 160), label = "tourLight")
        // The light glides from one element to the next instead of jumping.
        val hole = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
        var hasHole by remember { mutableStateOf(false) }
        LaunchedEffect(goal, lit) {
            if (goal == null || !lit) return@LaunchedEffect
            if (!hasHole || light < 0.05f) {
                hole.snapTo(goal)
                hasHole = true
            } else {
                hole.animateTo(goal, spring(dampingRatio = 0.86f, stiffness = 260f))
            }
        }
        val pulse by rememberInfiniteTransition(label = "tourPulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1700), RepeatMode.Restart), label = "tourPulseValue")
        val ring = accent
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.64f))
            if (hasHole && light > 0f) {
                val h = hole.value
                val corner = CornerRadius(22.dp.toPx())
                drawRoundRect(Color.Black.copy(alpha = light), Offset(h.left, h.top), Size(h.width, h.height), corner, blendMode = BlendMode.DstOut)
                drawRoundRect(ring.copy(alpha = light), Offset(h.left, h.top), Size(h.width, h.height), corner, style = Stroke(width = 3.dp.toPx()))
                // A soft wave runs outwards from the light, so the eye finds it at once.
                val grow = 12.dp.toPx() * pulse
                drawRoundRect(
                    ring.copy(alpha = light * 0.55f * (1f - pulse)),
                    Offset(h.left - grow, h.top - grow),
                    Size(h.width + 2 * grow, h.height + 2 * grow),
                    CornerRadius(22.dp.toPx() + grow),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        // The card is held by its lower edge. It stays at the bottom of the screen whenever the element is not
        // covered there; only for elements low on the screen (the bar at the bottom) it sits right above them.
        val insets = WindowInsets.systemBars
        val topInset = insets.getTop(density).toFloat()
        val bottomInset = insets.getBottom(density).toFloat()
        val margin = with(density) { 10.dp.toPx() }
        val gap = with(density) { 14.dp.toPx() }
        var bubbleHeight by remember { mutableIntStateOf(0) }
        val bottomEdge = height - bottomInset - margin
        var lastEdge by remember { mutableStateOf(Float.NaN) }
        val edgeGoal =
            when {
                bubbleHeight == 0 -> Float.NaN
                step.target == null -> (height + bubbleHeight) / 2f
                goal == null || !lit -> if (lastEdge.isNaN()) bottomEdge else lastEdge
                goal.bottom + gap <= bottomEdge - bubbleHeight -> bottomEdge
                goal.top - gap - bubbleHeight >= topInset + margin -> goal.top - gap
                goal.top > height - goal.bottom -> topInset + margin + bubbleHeight
                else -> bottomEdge
            }
        val edge = remember { Animatable(0f) }
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(edgeGoal) {
            if (edgeGoal.isNaN()) return@LaunchedEffect
            lastEdge = edgeGoal
            if (!placed) {
                edge.snapTo(edgeGoal)
                placed = true
            } else {
                edge.animateTo(edgeGoal, spring(dampingRatio = 0.84f, stiffness = 230f))
            }
        }
        androidx.compose.ui.layout.Layout(
            content = {
                Box(Modifier.onSizeChanged { bubbleHeight = it.height }.graphicsLayer { alpha = if (placed) 1f else 0f }) {
                    TourBubble(vm, index)
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val side = 16.dp.roundToPx()
            val width = (constraints.maxWidth - 2 * side).coerceAtLeast(0)
            val bubble = measurables.first().measure(androidx.compose.ui.unit.Constraints(minWidth = 0, maxWidth = width, minHeight = 0, maxHeight = constraints.maxHeight))
            val minY = topInset + margin
            val maxY = (bottomEdge - bubble.height).coerceAtLeast(minY)
            layout(constraints.maxWidth, constraints.maxHeight) {
                bubble.place((constraints.maxWidth - bubble.width) / 2, (edge.value - bubble.height).coerceIn(minY, maxY).toInt())
            }
        }
    }
}

/** The card of the tour: progress, title and text glide over to the next step; the buttons stay where they are. */
@Composable
private fun TourBubble(vm: AppViewModel, index: Int) {
    val flow = de.abicoach.ui.theme.AbiTheme.flow
    val count = TourSteps.all.size
    val last = index == count - 1
    val shape = RoundedCornerShape(26.dp)
    val progress by animateFloatAsState((index + 1f) / count, spring(dampingRatio = 0.9f, stiffness = 200f), label = "tourProgress")
    Column(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .shadow(18.dp, shape, clip = false)
            .clip(shape)
            .background(flow.solid)
            .border(1.dp, Brush.verticalGradient(listOf(flow.edgeTop, flow.edgeBottom)), shape)
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 380f))
            .padding(18.dp)
            .testTag("tour_bubble"),
    ) {
        // How far the tour is.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(50)).background(flow.text.copy(alpha = 0.10f))) {
                Box(Modifier.fillMaxWidth(progress).height(5.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(flow.accent, flow.accent2))))
            }
            Spacer(Modifier.width(10.dp))
            Text("${index + 1} von $count", fontSize = 12.sp, color = flow.subtle)
        }
        Spacer(Modifier.height(14.dp))
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val dir = if (targetState >= initialState) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 320f)) { dir * it / 5 } + fadeIn(tween(240, delayMillis = 60))) togetherWith
                    (slideOutHorizontally(tween(180)) { -dir * it / 8 } + fadeOut(tween(140)))
            },
            label = "tourStep",
        ) { i ->
            val step = TourSteps.all.getOrNull(i) ?: TourSteps.all.first()
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(flow.hero1, flow.hero2))), contentAlignment = Alignment.Center) {
                        Icon(painterResource(step.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(step.title, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = flow.text, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(step.text, fontSize = 15.sp, lineHeight = 21.sp, color = flow.text)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!last) {
                TextButton(onClick = { vm.endTour() }, modifier = Modifier.testTag("tour_skip")) {
                    Text("Überspringen", color = flow.subtle)
                }
            }
            Spacer(Modifier.weight(1f))
            if (index > 0 && !last) {
                TextButton(onClick = { vm.tourBack() }, modifier = Modifier.testTag("tour_back")) {
                    Text("Zurück", color = flow.text)
                }
            }
            if (last && vm.app.store.sessions.value.none { it.kind == de.abicoach.data.SessionKind.PLACEMENT }) {
                TextButton(onClick = { vm.endTour(); vm.nav.push(Screen.PlacementIntro) }, modifier = Modifier.testTag("tour_placement")) {
                    Text("Zur Einstufung", color = de.abicoach.ui.theme.readable(flow.accent), fontWeight = FontWeight.Bold)
                }
            }
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .squish(interaction, 0.94f)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(flow.hero1, flow.hero2)))
                    .clickable(interactionSource = interaction, indication = null) { if (last) vm.endTour() else vm.tourNext() }
                    .testTag(if (last) "tour_done" else "tour_next"),
            ) {
                Text(
                    if (last) "Fertig" else "Weiter",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
                )
            }
        }
    }
}

/** Waits [n] frames (follows the screen's own clock, also in the app test). */
private suspend fun frames(n: Int) = repeat(n) { androidx.compose.runtime.withFrameNanos { } }
