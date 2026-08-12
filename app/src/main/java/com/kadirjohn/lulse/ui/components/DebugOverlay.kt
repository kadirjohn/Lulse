package com.kadirjohn.lulse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kadirjohn.lulse.ui.screen.DebugUiState

/**
 * Gizli debug overlay (spec §7). Ana UI'yi kirletmez — yalnızca
 * [visible] ise üstte yarı-saydam panel olarak görünür.
 *
 * Debug verisi debugViewModel state'inden gelir; burada yalnızca sunum var.
 */
@Composable
fun DebugOverlay(
    state: DebugUiState,
    visible: Boolean,
    recording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Debug",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.textButtonColors(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                ) { Text("×", color = MaterialTheme.colorScheme.onSurface) }
            }
            Spacer(Modifier.height(2.dp))

            DebugSection("Sensörler") {
                state.sensorAvailability.forEach { (name, avail) ->
                    DebugRow(name, if (avail) "var" else "YOK")
                }
                state.sensorInfo.forEach { (name, info) ->
                    if (info != null) DebugRowSmall("$name info", info)
                }
            }
            DebugSection("Sample rate (Hz)") {
                state.sampleRateHz.forEach { (name, hz) ->
                    DebugRow(name, "%.1f".format(hz))
                }
            }
            DebugSection("Motion") {
                DebugRow("score", "%.2f".format(state.motionScore))
                DebugRow("accelVar", "%.3f".format(state.accelVariance))
                DebugRow("gyroEnergy", "%.4f".format(state.gyroEnergy))
                DebugRow("jerk", "%.3f".format(state.jerk))
                DebugRow("motionState", state.motionState.name)
                DebugRow("measurementState", state.measurementState.name)
            }
            DebugSection("Buffer") {
                DebugRow("size", state.bufferSize.toString())
                DebugRow("dropped", state.bufferDropped.toString())
            }
            DebugSection("Kayıt") {
                DebugRow("recording", if (recording) "EVET" else "hayır")
                DebugRow("count", state.recordedCount.toString())
                if (state.lastExportPath != null) {
                    DebugRowSmall("export", state.lastExportPath)
                }
                Spacer(Modifier.height(4.dp))
                if (recording) {
                    Button(onClick = onStopRecording, modifier = Modifier.fillMaxWidth()) {
                        Text("Kaydı durdur")
                    }
                } else {
                    Button(onClick = onStartRecording, modifier = Modifier.fillMaxWidth()) {
                        Text("Kaydı başlat")
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.SemiBold,
    )
    content()
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun DebugRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DebugRowSmall(k: String, v: String) {
    Column {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
    }
}