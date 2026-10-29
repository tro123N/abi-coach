package de.abicoach.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.abicoach.ui.components.AbiTopBar
import de.abicoach.ui.components.BottomBar
import de.abicoach.ui.components.Tab
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

/** Shows the current screen; the bottom bar belongs to the four main areas. */
@Composable
fun AppRoot(vm: AppViewModel) {
    val context = LocalContext.current
    BackHandler {
        if (!vm.back()) (context as? Activity)?.moveTaskToBack(true)
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = vm.nav.current,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "screen",
        ) { screen ->
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
            }
        }
    }
}

/** A main area: top bar, scrolling list of cards, bottom bar. */
@Composable
fun TabScaffold(
    vm: AppViewModel,
    tab: Tab,
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = { AbiTopBar(title = title, actions = actions) },
        bottomBar = {
            BottomBar(current = tab, onSelect = {
                vm.tap()
                vm.nav.openTab(it)
            }, badge = { t -> t == Tab.KURS && vm.app.memory.docs.value.any { d -> d.status == de.abicoach.memory.DocStatus.FAILED } })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("list_${tab.name.lowercase()}"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = {
                content()
                item { Spacer(Modifier.height(24.dp)) }
            },
        )
    }
}

/** A screen on top of an area: top bar with back arrow and scrolling content. */
@Composable
fun DetailScaffold(
    title: String,
    onBack: (() -> Unit)?,
    backIcon: Int = de.abicoach.R.drawable.arrow_back_24dp,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: @Composable () -> Unit = {},
    tag: String = "detail",
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = { AbiTopBar(title = title, onBack = onBack, backIcon = backIcon, actions = actions) },
        bottomBar = bottom,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).testTag(tag).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = {
                content()
                Spacer(Modifier.height(24.dp))
            },
        )
    }
}
