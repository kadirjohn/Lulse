package com.kadirjohn.lulse.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.kadirjohn.lulse.wear.data.health.StubHealthSensorSource
import com.kadirjohn.lulse.wear.data.transport.TransportHolder
import com.kadirjohn.lulse.wear.domain.WatchHeartRateEvent
import com.kadirjohn.lulse.wear.domain.WatchTrackingState
import com.kadirjohn.lulse.wear.domain.uiLabel
import com.kadirjohn.lulse.wear.ui.LulseYellow
import com.kadirjohn.lulse.wear.ui.PulseGlow
import kotlinx.coroutines.flow.collect

/**
 * Lulse Wear ana aktivite — round OLED black, warm yellow accent (docs 05 Phase 4).
 *
 * Görsel: LULSE / büyük BPM / Reference / IBI.
 * IBI cadance görselleştirmesi [PulseGlow] ile — bkz oradaki uyarı: bu flash bir
 * Samsung per-beat callback'i DEĞİLDİR; IBI cadance'ından scheduled bir tempo.
 *
 * Veri [TransportHolder] singleton'undan (process scope) gelir — Activity ve
 * [WearMessageListenerService] aynı instance'ı paylaşır.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transport'ı başlat (capability advertise + health source).
        TransportHolder.get(this)
        setContent { WearApp() }
    }
}

@Composable
fun WearApp() {
    val context = LocalContext.current
    val transport = remember { TransportHolder.get(context) }
    val trackingState by remember { mutableStateOf(transport.healthTrackingState()) }
    var bpm by remember { mutableStateOf<Int?>(null) }
    var lastIbi by remember { mutableStateOf<Int?>(null) }

    // HR olaylarını dinle — transport health source events'ini expose eder.
    LaunchedEffect(Unit) {
        transport.healthEvents().collect { event ->
            bpm = event.heartRateBpm
            lastIbi = event.validIbiValuesMs.lastOrNull()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PulseGlow(
            ibiMs = lastIbi,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "LULSE",
                color = LulseYellow.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
            )
            val bpmText = if (trackingState == WatchTrackingState.TRACKING && bpm != null) "$bpm" else "--"
            Text(
                text = bpmText,
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "BPM",
                color = LulseYellow.copy(alpha = 0.8f),
                fontSize = 14.sp,
            )
            val subText = if (trackingState == WatchTrackingState.TRACKING && lastIbi != null) {
                "IBI $lastIbi ms"
            } else if (trackingState == WatchTrackingState.TRACKING) {
                "Reference"
            } else {
                trackingState.uiLabel()
            }
            Text(
                text = subText,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}