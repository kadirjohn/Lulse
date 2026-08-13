package com.kadirjohn.lulse.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
 *  - Balonu **serbestçe sürükle** (her yöne) — balon **parmağı takip eder** (anlık,
 *    gecikmesiz), ekran içinde kalır.
 *  - Bırakınca en yakın **sol/sağ kenara mıknatıs gibi yapışır** (yumuşak spring).
 *  - Balona **tıkla** (hareketsiz dokunuş) → debug panelini tekrar tam aç.
 *  - Debug panelini **kapat** (çarpı) → balon da kaybolur.
 *
 * Tık ve sürükle ayrı pointerInput bloklarında — çakışmazlar. Sürükleme sırasında
 * konum `Animatable.snapTo` ile anlık güncellenir (parmağı takip eder); bırakınca
 * `animateTo` ile kenara yapışır ve ViewModel'e kaydedilir.
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
    val marginPx = with(density) { 10.dp.toPx() }
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(density) }

    var screenWidth by remember { mutableStateOf(0f) }
    var screenHeight by remember { mutableStateOf(0f) }

    // Animatable konum — sürüklemede anlık (parmağı takip), bırakınca spring ile kenara yapış.
    val animX = remember { Animatable(offsetX) }
    val animY = remember { Animatable(offsetY) }
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

        val minX = marginPx
        val maxX = (screenWidth - bubblePx - marginPx).coerceAtLeast(marginPx)
        val minY = (statusBarTop + marginPx).coerceAtLeast(marginPx)
        val maxY = (screenHeight - bubblePx - marginPx).coerceAtLeast(minY)

        // İlk gösterim: konum (0,0) ise sağ-üst köşeye yerleştir.
        LaunchedEffect(screenWidth, screenHeight) {
            if (screenWidth > 0 && screenHeight > 0 && animX.value == 0f && animY.value == 0f) {
                val initX = (screenWidth - bubblePx - marginPx)
                val initY = (statusBarTop + marginPx).coerceAtLeast(marginPx)
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
                // Tık (hareketsiz dokunuş) → paneli aç. Ayrı pointerInput —
                // drag ile çakışmaz; Compose sırasıyla iki detectoru da dener.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                }
                // Sürükleme → parmağı anlık takip et, bırakınca kenara mıknatıs yapış.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Anlık konum — parmağı takip eder (spring değil, gecikmesiz).
                            scope.launch {
                                animX.snapTo((animX.value + dragAmount.x).coerceIn(minX, maxX))
                                animY.snapTo((animY.value + dragAmount.y).coerceIn(minY, maxY))
                            }
                        },
                        onDragEnd = {
                            // Bırakınca en yakın sol/sağ kenara mıknatıs yapış.
                            val targetX = if (animX.value < (screenWidth / 2f)) minX else maxX
                            scope.launch {
                                animX.animateTo(
                                    targetValue = targetX,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                                // Yapışma bitince ViewModel'e kaydet.
                                onDragEnd(animX.value, animY.value)
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