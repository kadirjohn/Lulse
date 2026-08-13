package com.kadirjohn.lulse.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Minimize edilmiş debug paneli — ekranda serbestçe sürüklenen yuvarlak balon.
 *
 * Android'in chat bubble / picture-in-picture mantığı gibi:
 *  - Balonu **serbestçe sürükle** (her yöne, akıcı) — balon ekran içinde kalır,
 *    bırakınca en yakın **kenara mıknatıs gibi yapışır** (yumuşak spring animasyonu).
 *  - Balona **tıkla** (hareketsiz dokunuş) → debug panelini tekrar tam aç.
 *  - Debug panelini **kapat** (çarpı) → balon da kaybolur.
 *
 * Sürükleme sırasında konum anlık güncellenir; bırakınca kenara yapışır ve
 * ViewModel'e kaydedilir. Bu, ölçümü engellemeyen, oyunlaştırılmış bir debug
 * erişim noktasıdır.
 *
 * @param visible Balon görünürlüğü (debugMinimized state'i).
 * @param offsetX, offsetY Balonun kaydedilmiş konumu (px, ekran sol-üst köşesinden).
 * @param onTap Balona tıklayınca paneli aç.
 * @param onDragEnd Sürükleme + kenara-yapışma bitince konumu ViewModel'e kaydet (px).
 */
@Composable
fun DebugBubble(
    visible: Boolean,
    offsetX: Float,
    offsetY: Float,
    onTap: () -> Unit,
    onDragEnd: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val bubbleSize = 52.dp
    val bubblePx = with(density) { bubbleSize.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    // Status bar yüksekliği — balon bunun altında kalsın.
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(density) }

    // Balonun ekran genişliği — balon ekranı dolduran bir overlay içinde serbest dolaşır.
    // Ekran boyutunu overlay layout'undan öğren.
    var screenWidth by remember { mutableStateOf(0f) }
    var screenHeight by remember { mutableStateOf(0f) }

    // Animatable konumlar — sürükleme sırasında anlık, bırakınca spring ile kenara yapışır.
    val animX = remember { Animatable(offsetX) }
    val animY = remember { Animatable(offsetY) }
    // Dışarıdan gelen konum değişimi (ViewModel reset vb.) → anim'i güncelle.
    LaunchedEffect(offsetX, offsetY) {
        if (offsetX != animX.value || offsetY != animY.value) {
            animX.snapTo(offsetX)
            animY.snapTo(offsetY)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { coords ->
                screenWidth = coords.width.toFloat()
                screenHeight = coords.height.toFloat()
            },
    ) {
        if (screenWidth == 0f || screenHeight == 0f) return@Box

        // İzin verilen hareket aralığı — balon ekran içinde + margin kalır.
        val minX = marginPx
        val maxX = (screenWidth - bubblePx - marginPx).coerceAtLeast(marginPx)
        val minY = (statusBarTop + marginPx).coerceAtLeast(marginPx)
        val maxY = (screenHeight - bubblePx - marginPx).coerceAtLeast(minY)

        // İlk gösterim: konum (0,0) ise sağ-üst köşeye yerleştir.
        LaunchedEffect(screenWidth, screenHeight) {
            if (screenWidth > 0 && screenHeight > 0 && animX.value == 0f && animY.value == 0f) {
                val initX = (screenWidth - bubblePx - marginPx)
                val initY = (statusBarTop + marginPx)
                animX.snapTo(initX)
                animY.snapTo(initY)
                onDragEnd(initX, initY)
            }
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        animX.value.roundToInt(),
                        animY.value.roundToInt(),
                    )
                }
                .size(bubbleSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f))
                .pointerInput(screenWidth, screenHeight) {
                    // Tek pointerInput: sürükleme + tık. Hareket eşiği altında = tık.
                    var totalDrag = 0f
                    detectDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += kotlin.math.abs(dragAmount.x) + kotlin.math.abs(dragAmount.y)
                            // Anlık konum güncelle (ekran içine klip).
                            scope.launch {
                                animX.snapTo(
                                    (animX.value + dragAmount.x).coerceIn(minX, maxX),
                                )
                                animY.snapTo(
                                    (animY.value + dragAmount.y).coerceIn(minY, maxY),
                                )
                            }
                        },
                        onDragEnd = {
                            if (totalDrag < 10f) {
                                // Hareketsiz dokunuş → tık: paneli aç.
                                onTap()
                            } else {
                                // Sürükleme bitti → en yakın sol/sağ kenara mıknatıs yapış.
                                val targetX = if (animX.value < (screenWidth / 2f)) minX else maxX
                                scope.launch {
                                    animX.animateTo(
                                        targetValue = targetX,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                    )
                                    // Yapışma bitince ViewModel'e kaydet.
                                    onDragEnd(animX.value, animY.value)
                                }
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "D",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = with(density) { 18.dp.toSp() },
            )
        }
    }
}