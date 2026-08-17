package com.kadirjohn.lulse.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadirjohn.lulse.ui.screen.DebugUiState
import kotlinx.coroutines.launch

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
                // Minimize butonu — paneli balona indir.
                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier.size(40.dp),
                ) {
                    Text(
                        "–",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                // Kapat butonu (büyütülmüş çarpı).
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(44.dp),
                ) {
                    Text(
                        "×",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))

            DebugSection("Sensörler") {
                state.sensorAvailability.forEach { (name, avail) ->
                    DebugRow(name, if (avail) "var" else "YOK")
                }
                state.sensorInfo.forEach { (name, info) ->
                    if (info != null) DebugRowSmall("$name info", info)
                }
                Spacer(Modifier.height(4.dp))
                // Design preview toggle — sadece tasarım önizleme; gerçek ölçümü bastırır,
                // 4 ekranı slider ile manuel simüle eder (nabız hesaplanmaz).
                if (designPreview) {
                    Button(
                        onClick = onStopDesignPreview,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(),
                    ) { Text("Design preview: açık — kapat") }
                } else {
                    Button(
                        onClick = onStartDesignPreview,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(),
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
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onConnectWatch,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(),
                ) { Text("Watch'a bağlan") }
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
                    // Design preview'da gerçek nabız hesabı yapılmaz → kayıt başlatma kapalı.
                    Button(
                        onClick = onStartRecording,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !designPreview,
                    ) {
                        Text(if (designPreview) "Kayıt (preview'da kapalı)" else "Kaydı başlat")
                    }
                }
            }
        }
    }

    // Design preview slider DebugOverlay'da DEĞİL, HomeScreen seviyesinde
    // render edilir — böylece debug paneli minimize edilse bile görünür kalır.
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

// --- Design preview slider (debug panelinin üstü, 5 segment) ---

private val DesignPreviewLabels = listOf("Hareketli", "Sabit dur", "Nabız aranıyor", "BPM", "Nabız yok")

/**
 * Design preview için debug panelinin üstünde açılan 5 segmentli kaydırma slider'ı.
 *
 * - Soldan sağa natural kaydırma + her segment eşiği geçildiğinde anında ekran
 *   değişimi (release beklenmez). Böylece ileri/geri tüm ekranlara serbest geçilir.
 * - Tutamaç (o) offset'e göre yatayda konumlanır, | işaretleri sabit:
 *      o---|---|---|---|  (0. segment)  →  |---|---o---|---|  (2. segment)
 * - Segmentler: 0=Hareketli, 1=Sabit dur, 2=Nabız aranıyor, 3=BPM, 4=Nabız yok.
 * - Sadece manuel seçim — otomatik ekran geçişi yok.
 *
 * Debug panelinin üstünde (TopCenter) konumlanır; panel en üstte (en önde) görünür.
 */
@Composable
fun DesignPreviewSlider(
    index: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val segmentCount = DesignPreviewLabels.size
    val maxIndex = (segmentCount - 1).toFloat()

    // Tutamacın pozisyonu — SENKRON state. Drag sırasında hemen güncellenir, böylece
    // eşik kontrolü her frame'de doğru segmenti görür (async Animatable lag'i olmadan).
    var dragOffset by remember { mutableStateOf(index.toFloat()) }
    // Snap animasyonu yalnızca drag bittiğinde kullanılır (görsel hizalama).
    val snapAnim = remember { Animatable(index.toFloat()) }
    // Drag aktif mi? Drag sırasında dragOffset, değilse snapAnim değeri gösterilir.
    var isDragging by remember { mutableStateOf(false) }
    val displayOffset = if (isDragging) dragOffset else snapAnim.value

    // Dışarıdan index değişirse (slider'a ilk ulaşımda / programatik seçim)
    // tutamacı yeni segmente yumuşakça taşı. Drag sırasında bu tetiklenmez (isDragging).
    LaunchedEffect(index) {
        if (!isDragging && snapAnim.value.toInt() != index) {
            snapAnim.snapTo(dragOffset)
            snapAnim.animateTo(index.toFloat(), tween(260))
            dragOffset = index.toFloat()
        }
    }

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

            // index ve onSelect'i her recomposition'da güncel tut — pointerInput
            // closure'ı drag bitene kadar eski değeri capture etmesin (geriye dönüş
            // yönünde onSelect'in hiç çağrılmaması bug'ını kökünden çözer).
            val currentIndex by rememberUpdatedState(index)
            val currentOnSelect by rememberUpdatedState(onSelect)

            // Kaydırma bölgesi — tutamacı yatayda sürükle. Her segment eşiği geçilince
            // anında onSelect çağrılır (release beklemeden ekran değişir).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pointerInput(segmentCount) {
                        val trackWidthPx = size.width.toFloat()
                        val segmentPx = trackWidthPx / segmentCount
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                // Bırakınca tutamacı en yakın segmente snap (görsel hizalama).
                                isDragging = false
                                val snapped = dragOffset.roundToInt().coerceIn(0, segmentCount - 1)
                                scope.launch {
                                    snapAnim.snapTo(dragOffset)
                                    snapAnim.animateTo(snapped.toFloat(), tween(180))
                                    dragOffset = snapped.toFloat()
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // Drag offset'i senkron güncelle — eşik kontrolü hemen doğru çalışır.
                                dragOffset = (dragOffset + dragAmount.x / segmentPx).coerceIn(0f, maxIndex)
                                // Eşiği geçen yeni segmenti anında uygula — release gerekmez.
                                // İleri ve geri yön için simetrik çalışır (currentIndex her zaman güncel).
                                val current = dragOffset.roundToInt().coerceIn(0, segmentCount - 1)
                                if (current != currentIndex) currentOnSelect(current)
                            },
                        )
                    },
            ) {
                SliderTrack(segmentCount = segmentCount, offset = displayOffset)
            }
        }
    }
}

/**
 * Slider'ın görsel izi: segment çizgileri + kayan tutamaç (o).
 * o---|---|---|  → tutamaç offset'e göre yatayda konumlanır, | işaretleri sabit.
 * Renkler composable context'te toplanır, DrawScope'a parametre geçilir.
 */
@Composable
private fun SliderTrack(segmentCount: Int, offset: Float) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val segmentPx = w / segmentCount

            // Yatay bağlantı çizgisi.
            drawLine(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f),
                start = Offset(segmentPx * 0.5f, h * 0.5f),
                end = Offset(w - segmentPx * 0.5f, h * 0.5f),
                strokeWidth = 2.dp.toPx(),
            )

            // Segment işaretleri (|) — her segment merkezi.
            for (i in 0 until segmentCount) {
                val x = segmentPx * (i + 0.5f)
                drawLine(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                    start = Offset(x, h * 0.3f),
                    end = Offset(x, h * 0.7f),
                    strokeWidth = 2.dp.toPx(),
                )
            }

            // Tutamaç (o) — offset'e göre konumlanır.
            val handleX = segmentPx * (offset + 0.5f)
            drawCircle(
                color = tertiary,
                radius = 12.dp.toPx(),
                center = Offset(handleX, h * 0.5f),
            )
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                radius = 6.dp.toPx(),
                center = Offset(handleX, h * 0.5f),
            )
        }
    }
}

private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()