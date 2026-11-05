package de.abicoach.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.abicoach.ui.components.AbiTopBar
import de.abicoach.ui.components.GlassDock
import de.abicoach.ui.components.LargeHeader
import de.abicoach.ui.components.LivingBackground
import de.abicoach.ui.components.Tab
import de.abicoach.ui.components.fadeTopEdge
import de.abicoach.ui.components.dockSource
import de.abicoach.ui.components.dockSpace
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
    CompositionLocalProvider(LocalContentColor provides flow.text) {
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
                        when (vm.nav.direction) {
                            1 ->
                                (slideInHorizontally(slide) { it / 4 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(slide) { -it / 10 } + fadeOut(tween(160)))
                            -1 ->
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
                            Screen.Tutorial -> de.abicoach.ui.screens.TutorialScreen(vm)
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
        }
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
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = dockSpace()
    // Cards fade out under the status bar instead of being cut off; the bar at the bottom floats above them.
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().fadeTopEdge(hiddenUntil = top - 6.dp, visibleFrom = top + 8.dp).testTag("list_${tab.name.lowercase()}"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 10.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = {
            item(key = "header") { LargeHeader(title, caption, actions = actions) }
            content()
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val flow = AbiTheme.flow
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
                        .verticalScroll(rememberScrollState())
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
