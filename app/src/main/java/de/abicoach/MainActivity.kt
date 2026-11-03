package de.abicoach

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import de.abicoach.ui.AppRoot
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.LocalDarkTheme

class MainActivity : ComponentActivity() {
    val vm: AppViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** A tap on a "Auswertung fertig" notification opens that debrief. */
    private fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra(de.abicoach.session.Notifications.EXTRA_DEBRIEF) ?: return
        intent?.removeExtra(de.abicoach.session.Notifications.EXTRA_DEBRIEF)
        vm.openFromNotification(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AbiCoachApp
        enableEdgeToEdge()
        app.feedback.warmUp()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            // Reading the version redraws everything when the theme or the palette changes.
            val version = app.settings.version.collectAsState().value
            AbiTheme(themeMode = app.settings.themeMode, palette = app.settings.palette) {
                val dark = LocalDarkTheme.current
                LaunchedEffect(dark, version) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                AppRoot(vm)
            }
        }
    }
}
