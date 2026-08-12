package com.kadirjohn.lulse

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kadirjohn.lulse.ui.screen.HomeScreen
import com.kadirjohn.lulse.ui.theme.LulseTheme

/**
 * Lulse'un tek Activity'si — tek ekranı host eder (spec §4).
 *
 * - edge-to-edge
 * - ekran sürekli açık (ölçüm sırasında kapanmasın)
 * - her zaman karanlık tema
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ekran ölçüm sırasında kapanmasın.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            LulseTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.uiState.collectAsState()
                HomeScreen(
                    state = state,
                    onLongPressToggleDebug = viewModel::toggleDebug,
                    onStartRecording = viewModel::startRecording,
                    onStopRecording = viewModel::stopRecording,
                    onCloseDebug = viewModel::closeDebug,
                    onDismissIntro = viewModel::dismissIntro,
                )
            }
        }
    }
}