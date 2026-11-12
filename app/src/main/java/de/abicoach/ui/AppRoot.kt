package de.abicoach.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.abicoach.ui.components.AbiTopBar
import de.abicoach.ui.components.GlassDock
import de.abicoach.ui.components.GlassSurface
import de.abicoach.ui.components.LargeHeader
import de.abicoach.ui.components.LivingBackground
import de.abicoach.ui.components.Tab
import de.abicoach.ui.components.dockSource
import de.abicoach.ui.components.dockSpace
import de.abicoach.ui.components.fadeTopEdge
import de.abicoach.ui.components.rememberDockBlur
import de.abicoach.ui.screens.AbiWegScreen
import de.abicoach.ui.screens.AnalyzingScreen
import de.abicoach.ui.screens.AskScreen
import de.abicoach.ui.screens.CourseScreen
import de.abicoach.ui.screens.DebriefScreen
import de.abicoach.ui.screens.DocScreen
import de.abicoach.ui.screens.GeneratingScreen
import de.abicoach.ui.screens.LiveScreen
import de.abicoach.ui.screens.PlacementIntroScreen
import de.abicoach.ui.screens.PrepScreen
import de.abicoach.ui.screens.SessionsScreen
import de.abicoach.ui.screens.SettingsScreen
import de.abicoach.ui.screens.SetupScreen
import de.abicoach.ui.screens.StatsScreen
import de.abicoach.ui.screens.TrainingScreen
import de.abicoach.ui.theme.AbiTheme

/** Which arrangement of the background light a page gets. */
private fun moodOf(screen: Screen): Int =
    when (screen) {
        is Screen.Root -> screen.tab.ordinal
        else -> 4
    }

/**
 * Shows the current screen over the flowing background. The floating glass bar belongs to the four main
 * areas; pages on top of an area slide in from the side, areas fade into each other.
 */
@Composable
fun AppRoot(vm: AppViewModel) {
    val context = LocalContext.current
    BackHandler {
        if (!vm.back()) (context as? Activity)?.moveTaskToBack(true)
    }
    val flow = AbiTheme.flow
    val blur = rememberDockBlur()
    val docs by vm.app.memory.docs.collectAsState()
    CompositionLocalProvider(LocalContentColor provides flow.text, LocalTour provides vm.tour) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().dockSource(blur)) {
                LivingBackground(mood = moodOf(vm.nav.current))
                // Every screen keeps its scroll position: going back shows the list exactly where you left it.
                val saved = rememberSaveableStateHolder()
                // A screen that left the back stack starts at the top the next time it is opened; the four main areas always
                // keep their position.
                val keys = vm.nav.stack.filter { it !is Screen.Root }.map { it.toString() }.toSet()
                val known = remember { mutableSetOf<String>() }
                LaunchedEffect(keys) {
                    (known - keys).forEach { saved.removeState(it) }
                    known.retainAll(keys)
                    known.addAll(keys)
                }
                AnimatedContent(
                    targetState = vm.nav.current,
                    transitionSpec = {
                        val slide = spring(dampingRatio = 0.9f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)
                        val from = (initialState as? Screen.Root)?.tab
                        val to = (targetState as? Screen.Root)?.tab
                        when {
                            // Between the four areas the page glides the way the bar at the bottom moves.
                            from != null && to != null && from != to -> {
                                val dir = if (to.ordinal > from.ordinal) 1 else -1
                                (slideInHorizontally(slide) { dir * it / 5 } + fadeIn(tween(240))) togetherWith
                                    (slideOutHorizontally(slide) { -dir * it / 5 } + fadeOut(tween(150)))
                            }
                            vm.nav.direction == 1 ->
                                (slideInHorizontally(slide) { it / 4 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(slide) { -it / 10 } + fadeOut(tween(160)))
                            vm.nav.direction == -1 ->
                                (slideInHorizontally(slide) { -it / 10 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(slide) { it / 4 } + fadeOut(tween(160)))
                            else ->
                                (fadeIn(tween(240)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 380f), initialScale = 0.97f)) togetherWith
                                    fadeOut(tween(140))
                        }
                    },
                    label = "screen",
                ) { screen ->
                    saved.SaveableStateProvider(screen.toString()) {
                        when (screen) {
                            is Screen.Root ->
                                when (screen.tab) {
                                    Tab.WEG -> AbiWegScreen(vm)
                                    Tab.TRAINING -> TrainingScreen(vm)
                                    Tab.KURS -> CourseScreen(vm)
                                    Tab.STATS -> StatsScreen(vm)
                                }
                            Screen.Settings -> SettingsScreen(vm)
                            is Screen.Setup -> SetupScreen(vm, screen.setup)
                            Screen.Generating -> GeneratingScreen(vm)
                            Screen.Prep -> PrepScreen(vm)
                            Screen.Live -> LiveScreen(vm)
                            Screen.Analyzing -> AnalyzingScreen(vm)
                            is Screen.Debrief -> DebriefScreen(vm, screen.id)
                            is Screen.Doc -> DocScreen(vm, screen.id)
                            Screen.Ask -> AskScreen(vm)
                            Screen.Sessions -> SessionsScreen(vm)
                            Screen.PlacementIntro -> PlacementIntroScreen(vm)
                            is Screen.Level -> de.abicoach.ui.screens.LevelScreen(vm, screen.id)
                            Screen.Vocab -> de.abicoach.ui.screens.VocabScreen(vm)
                            Screen.Pronunciation -> de.abicoach.ui.screens.PronunciationScreen(vm)
                            is Screen.Corrections -> de.abicoach.ui.screens.CorrectionsScreen(vm, screen.id)
                            Screen.Drills -> de.abicoach.ui.screens.DrillsScreen(vm)
                            Screen.Errors -> de.abicoach.ui.screens.ErrorsScreen(vm)
                            is Screen.Dossier -> de.abicoach.ui.screens.DossierScreen(vm, screen.topic)
                            Screen.RealTasks -> de.abicoach.ui.screens.RealTasksScreen(vm)
                            Screen.Workshop -> de.abicoach.ui.screens.WorkshopScreen(vm)
                            Screen.Skills -> de.abicoach.ui.screens.SkillsScreen(vm)
                            Screen.HalfYears -> de.abicoach.ui.screens.HalfYearsScreen(vm)
                            Screen.Onboarding -> de.abicoach.ui.screens.OnboardingScreen(vm)
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = vm.nav.current is Screen.Root,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
            ) {
                GlassDock(
                    current = vm.nav.tab,
                    onSelect = {
                        vm.tap()
                        vm.nav.openTab(it)
                    },
                    blur = blur,
                    badge = { t -> t == Tab.KURS && docs.any { d -> d.status == de.abicoach.memory.DocStatus.FAILED } },
                )
            }
            // Settings are always one tap away: the button floats at the top right of all four areas.
            AnimatedVisibility(
                visible = vm.nav.current is Screen.Root,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 420f), initialScale = 0.7f),
                exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.7f),
            ) {
                val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                Box(Modifier.padding(top = top + SettingsTop, end = 20.dp).tourTarget("action_settings")) {
                    GlassSurface(
                        modifier = Modifier.size(SettingsSize).testTag("action_settings"),
                        radius = SettingsSize / 2,
                        fill = flow.solid.copy(alpha = if (flow.dark) 0.86f else 0.9f),
                        onClick = { vm.tap(); vm.nav.push(Screen.Settings) },
                    ) {
                        Icon(
                            painterResource(de.abicoach.R.drawable.settings_24dp),
                            contentDescription = "Einstellungen",
                            tint = flow.text,
                            modifier = Modifier.align(Alignment.Center).size(SettingsSize * 0.46f),
                        )
                    }
                }
            }
            // The guided tour lies over everything while it runs.
            TourOverlay(vm)
        }
    }
}

/** Size and place of the floating settings button; the title line of each area keeps this room free. */
val SettingsSize = 44.dp
private val SettingsTop = 30.dp

/**
 * Gives every card of a main area its motion without the screens having to care: cards rise in softly one after
 * the other when the area opens, and glide to their new place when cards appear, disappear or change size.
 */
private class MovingScope(private val inner: LazyListScope, private val state: LazyListState, private val born: Long, private val calm: () -> Boolean) : LazyListScope by inner {
    private var next = 0

    @Composable
    private fun LazyItemScope.Moving(index: Int, content: @Composable () -> Unit) {
        // Only the cards that are there when the area opens rise in; cards that scroll into sight later just are there.
        val fresh = remember { !calm() && android.os.SystemClock.uptimeMillis() - born < 350 }
        val rise = remember { Animatable(if (fresh) 0f else 1f) }
        if (fresh) {
            LaunchedEffect(Unit) {
                val order = (index - state.firstVisibleItemIndex).coerceIn(0, 7)
                rise.animateTo(1f, tween(durationMillis = 380, delayMillis = order * 45, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
        }
        Column(
            Modifier
                .animateItem(
                    fadeInSpec = tween(260),
                    placementSpec = spring(dampingRatio = 0.86f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold),
                    fadeOutSpec = tween(160),
                )
                .graphicsLayer {
                    alpha = rise.value
                    translationY = (1f - rise.value) * 26.dp.toPx()
                },
        ) { content() }
    }

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        val index = next++
        inner.item(key, contentType) { Moving(index) { content() } }
    }

    override fun items(count: Int, key: ((index: Int) -> Any)?, contentType: (index: Int) -> Any?, itemContent: @Composable LazyItemScope.(index: Int) -> Unit) {
        val base = next
        next += count
        inner.items(count, key, contentType) { i -> Moving(base + i) { itemContent(i) } }
    }
}

/** A main area: the large title scrolls with the cards, which glide behind the floating bar at the bottom. */
@Composable
fun TabScaffold(
    vm: AppViewModel,
    tab: Tab,
    title: String,
    caption: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    TourList(tab, state)
    val born = remember { android.os.SystemClock.uptimeMillis() }
    // During the tour the cards stand still, so the light finds them exactly.
    val calm = { vm.tour.active }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = dockSpace()
    // Cards fade out under the status bar instead of being cut off; the bar at the bottom floats above them.
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().fadeTopEdge(hiddenUntil = top - 6.dp, visibleFrom = top + 8.dp).testTag("list_${tab.name.lowercase()}"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 10.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = {
            val moving = MovingScope(this, state, born, calm)
            // The floating settings button sits at the right end of the title line: that room stays free.
            moving.item(key = "header", contentType = null) { LargeHeader(title, caption, actions = { actions(); Spacer(Modifier.width(SettingsSize + 2.dp)) }) }
            moving.content()
        },
    )
}

/** A screen on top of an area: transparent bar with a round back button, scrolling content. */
@Composable
fun DetailScaffold(
    title: String,
    onBack: (() -> Unit)?,
    backIcon: Int = de.abicoach.R.drawable.arrow_back_24dp,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: @Composable () -> Unit = {},
    /** Stays in place above the scrolling content (e.g. the whisper bar while speaking). */
    pinned: @Composable () -> Unit = {},
    tag: String = "detail",
    /** The scroll position of the content, for screens that scroll by themselves (e.g. following the transcript). */
    scroll: androidx.compose.foundation.ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val flow = AbiTheme.flow
    val scrollState = scroll ?: rememberScrollState()
    Scaffold(
        topBar = { AbiTopBar(title = title, onBack = onBack, backIcon = backIcon, actions = actions) },
        bottomBar = bottom,
        containerColor = Color.Transparent,
        contentColor = flow.text,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            pinned()
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .fadeTopEdge(hiddenUntil = 0.dp, visibleFrom = 12.dp)
                        .verticalScroll(scrollState)
                        .testTag(tag)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = {
                    content()
                    Spacer(Modifier.height(24.dp))
                },
            )
        }
    }
}
