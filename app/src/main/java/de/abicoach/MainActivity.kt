package de.abicoach

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import de.abicoach.ui.AbiTheme
import de.abicoach.ui.AnalyzingScreen
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.DebriefScreen
import de.abicoach.ui.HistoryScreen
import de.abicoach.ui.HomeScreen
import de.abicoach.ui.LiveScreen
import de.abicoach.ui.PrepScreen
import de.abicoach.ui.ProfileScreen
import de.abicoach.ui.Screen
import de.abicoach.ui.SettingsScreen
import de.abicoach.ui.SimSetupScreen
import de.abicoach.ui.TrainingSetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AbiTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }

    val micPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingMicAction
            pendingMicAction = null
            if (granted) {
                action?.invoke()
            } else {
                vm.errorMessage = "Ohne Mikrofon-Erlaubnis kann die App dich nicht hören."
            }
        }

    // Runs the action right away if the microphone is allowed, otherwise asks first.
    val withMic: (() -> Unit) -> Unit = { action ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingMicAction = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler(enabled = vm.nav.stack.size > 1) {
        if (vm.nav.current == Screen.Live) {
            confirmCancel = true
        } else {
            vm.nav.pop()
        }
    }

    when (val screen = vm.nav.current) {
        Screen.Home -> HomeScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        Screen.SimSetup -> SimSetupScreen(vm)
        Screen.TrainingSetup -> TrainingSetupScreen(vm, withMic)
        Screen.Prep -> PrepScreen(vm, withMic)
        Screen.Live -> LiveScreen(vm, onCancel = { confirmCancel = true })
        Screen.Analyzing -> AnalyzingScreen(vm)
        is Screen.Debrief -> DebriefScreen(vm, screen.id)
        Screen.History -> HistoryScreen(vm)
        Screen.Profile -> ProfileScreen(vm, withMic)
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Sitzung abbrechen?") },
            text = { Text("Die Sitzung wird beendet und nicht ausgewertet.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    vm.cancelLive()
                }) { Text("Abbrechen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Weiter üben") }
            },
        )
    }
}
