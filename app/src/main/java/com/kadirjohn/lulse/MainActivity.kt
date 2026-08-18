package com.kadirjohn.lulse

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.kadirjohn.lulse.ui.screen.HomeScreen
import com.kadirjohn.lulse.ui.theme.LulseTheme

/**
 * Lulse'un tek Activity'si — tek ekranı host eder (spec §4).
 *
 * - edge-to-edge (siyah sistem barları — Activity recreate'de griye dönmeyi engeller)
 * - ekran sürekli açık (ölçüm sırasında kapanmasın)
 * - her zaman karanlık tema
 * - lifecycle pause/resume: background→foreground dönüşte pipeline'ı dondurup
 *   stale sensör verisiyle lock'u bozmasını engeller (gri/soluk ekran fix).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lifecycle gözlemcisi: onStop pipeline'ı dondur, onStart fresh grace aç.
        // viewModelScope onStop'da ölmediği için pipeline arka planda çalışmaya devam
        // ederdi ve throttled sensör verisiyle lock'u bozardı. Bu kök nedeni çözüyor.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { viewModel.onPause() }
            override fun onStart(owner: LifecycleOwner) { viewModel.onResume() }
        })
        // Sistem barlarını tam şeffaf + siyah scrims ile edge-to-edge.
        // dark scrim: sistem barlar siyah arka plana otomatik uyum sağlar;
        // Activity recreate / aç-kapa sırasında gri windowBackground sızıntısını engeller.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Ekran ölçüm sırasında kapanmasın.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            LulseTheme {
                val state by viewModel.uiState.collectAsState()
                HomeScreen(
                    state = state,
                    onLongPressToggleDebug = viewModel::toggleDebug,
                    onStartRecording = viewModel::startRecording,
                    onStopRecording = viewModel::stopRecording,
                    onCloseDebug = viewModel::closeDebug,
                    onMinimizeDebug = viewModel::minimizeDebug,
                    onRestoreDebug = viewModel::restoreDebug,
                    onUpdateBubbleOffset = viewModel::updateBubbleOffset,
                    onConnectWatch = viewModel::connectWatch,
                    onStartDesignPreview = viewModel::startDesignPreview,
                    onStopDesignPreview = viewModel::stopDesignPreview,
                    onSetDesignPreviewIndex = viewModel::setDesignPreviewIndex,
                )
            }
        }
    }
}