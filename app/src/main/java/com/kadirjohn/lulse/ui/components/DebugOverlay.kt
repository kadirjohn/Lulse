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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    designPreview: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onConnectWatch: () -> Unit,
    onStartDesignPreview: () -> Unit,
    onStopDesignPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(12.dp),
    ) {
        // Material 3 Surface — tonalElevation ile gerçek M3 tonal yüzey rengi,
        // shadowElevation ile hafif kabarık his. Düz yarı-saydam Box yerine.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Başlık satısı — Material 3 top app bar hissi.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Debug",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    // Minimize butonu — paneli balona indir.
                    IconButton(onClick = onMinimize) {
                        Text(
                            "–",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    // Kapat butonu.
                    IconButton(onClick = onClose) {
                        Text(
                            "×",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Light,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            DebugSection("Sensörler") {
                state.sensorAvailability.forEach { (name, avail) ->
                    DebugRow(name, if (avail) "var" else "YOK")
                }
                state.sensorInfo.forEach { (name, info) ->
                    if (info != null) DebugRowSmall("$name info", info)
                }
                Spacer(Modifier.height(8.dp))
                // Design preview toggle — sadece tasarım önizleme; gerçek ölçümü bastırır,
                // 5 ekranı slider ile manuel simüle eder (nabız hesaplanmaz).
                if (designPreview) {
                    OutlinedButton(
                        onClick = onStopDesignPreview,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Design preview: açık — kapat") }
                } else {
                    OutlinedButton(
                        onClick = onStartDesignPreview,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Design preview aç") }
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
                DebugRow("orientation", state.orientation.name)
                DebugRow("motionState", state.motionState.name)
                DebugRow("measurementState", state.measurementState.name)
                DebugRow("bpm", state.bpm?.toString() ?: "-")
                DebugRow("confidence", state.confidence?.let { "%.2f".format(it) } ?: "-")
            }
            DebugSection("Buffer") {
                DebugRow("size", state.bufferSize.toString())
                DebugRow("dropped", state.bufferDropped.toString())
            }
            DebugSection("WATCH REFERENCE") {
                DebugRow("status", state.watchState.ifEmpty { "—" })
                DebugRow("connected", if (state.watchConnected) "EVET" else "hayır")
                DebugRow("bpm", state.watchReferenceBpm?.toString() ?: "—")
                DebugRow("hr status", state.watchHrStatus?.toString() ?: "—")
                DebugRow("ibi", state.watchLastValidIbiMs?.let { "$it ms" } ?: "—")
                DebugRow("age", state.watchReferenceAgeMs?.let { "$it ms" } ?: "—")
                DebugRow("sequence", state.watchSequence.toString())
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onConnectWatch,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Watch'a bağlan") }
            }
            DebugSection("Kayıt", showDivider = false) {
                DebugRow("recording", if (recording) "EVET" else "hayır")
                DebugRow("count", state.recordedCount.toString())
                if (state.lastExportPath != null) {
                    DebugRowSmall("export", state.lastExportPath)
                }
                Spacer(Modifier.height(8.dp))
                if (recording) {
                    // Durdur — vurgulu filled (tehlikeli/aktif eylem).
                    Button(
                        onClick = onStopRecording,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Kaydı durdur") }
                } else {
                    // Design preview'da gerçek nabız hesabı yapılmaz → kayıt başlatma kapalı.
                    Button(
                        onClick = onStartRecording,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !designPreview,
                    ) {
                        Text(if (designPreview) "Kayıt (preview'da kapalı)" else "Kaydı başlat")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        } // Surface trailing lambda kapanışı
    }

    // Design preview slider DebugOverlay'da DEĞİL, HomeScreen seviyesinde
    // render edilir — böylece debug paneli minimize edilse bile görünür kalır.
}

@Composable
private fun DebugSection(
    title: String,
    showDivider: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        // Material 3 bölüm başlığı — küçük, harf aralıklı, vurgu rengi.
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        )
        content()
    }
    // Bölüm sonu ayraç — Material 3 list-divider hissi (son bölümde kapalı).
    if (showDivider) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun DebugRow(k: String, v: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Anahtar — soluk etiket.
        Text(
            k,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Değer — vurgulu, sağa yaslı.
        Text(
            v,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DebugRowSmall(k: String, v: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            k,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            v,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
    }
}

// --- Design preview slider (ekran altı, 5 segment) ---

private val DesignPreviewLabels = listOf("Hareketli", "Sabit dur", "Nabız aranıyor", "BPM", "Nabız yok")

/**
 * Design preview için ekranın altında açılan 5 segmentli kaydırma slider'ı.
 *
 * Gerçek Material3 [Slider] (discrete steps) — platform Material You çizimi:
 * ince track, stop çentikleri, pressed thumb büyüme + ripple. [onValueChange]
 * her stop eşiğinde anında [onSelect] çağırır → release beklemeden ekran değişir,
 * ileri/geri serbest geçiş.
 *
 * Segmentler: 0=Hareketli, 1=Sabit dur, 2=Nabız aranıyor, 3=BPM, 4=Nabız yok.
 * Sadece manuel seçim — otomatik ekran geçişi yok.
 */
@Composable
fun DesignPreviewSlider(
    index: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segmentCount = DesignPreviewLabels.size
    val maxIndex = (segmentCount - 1).toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 20.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Design preview",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                DesignPreviewLabels[index.coerceIn(0, segmentCount - 1)],
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
            )

            // Gerçek Material3 Slider — discrete steps (5 segment → 4 stop aralığı).
            Slider(
                value = index.toFloat(),
                onValueChange = { value ->
                    val current = value.toIntForStep().coerceIn(0, segmentCount - 1)
                    if (current != index) onSelect(current)
                },
                valueRange = 0f..maxIndex,
                steps = segmentCount - 2, // N segment → N-1 aralık → N-2 ara stop
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.tertiary,
                    activeTrackColor = MaterialTheme.colorScheme.tertiary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeTickColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Slider value → en yakın segment index'i (yuvarlama). */
private fun Float.toIntForStep(): Int = kotlin.math.round(this).toInt()