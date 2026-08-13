package com.kadirjohn.lulse.wear.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.max

/** Lulse warm yellow accent (docs 05 visual direction). */
val LulseYellow = Color(0xFFFFD23F)
val LulseYellowDim = Color(0xFF6B5318)

/**
 * IBI cadance görselleştirmesi — sarı radial glow (docs 03, 05 Phase 4).
 *
 * **ÖNEMLİ (docs 03, 05):** Bu flash bir Samsung per-beat callback'i DEĞİLDİR.
 * Samsung processed HR ~1 Hz ve 0–4 IBI paketler; tek bir "beat anı" callback'i
 * yoktur. Bu animasyon, son geçerli IBI'lerin medyanından türetilen bir
 * **cadance (tempo) görselleştirmesidir** — fizyolojik beat zamanı ground truth
 * değildir. Gerçek per-beat zamanı Samsung'tan gelmez (docs 03).
 *
 * [ibiMs] null veya stale ise animasyon durur (docs 03: "stop pulse animation
 * when data is stale").
 *
 * @param ibiMs cadence periyodu (ms). null → ölçüm animasyonu.
 */
@Composable
fun PulseGlow(
    ibiMs: Int?,
    modifier: Modifier = Modifier,
) {
    val active = ibiMs != null && ibiMs > 0
    val periodMs = max(ibiMs ?: 1000, 300)
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Attack ~120ms hızlı, decay periyodun kalanı — soft.
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (active) {
                drawPulseGlow(pulse)
            } else {
                drawMeasuringGlow()
            }
        }
    }
}

/** Sarı radial glow — pulse alpha'sı ile salınım. Fast attack + soft decay.
 *  OLED-friendly: tam ekran parlak değil. */
private fun DrawScope.drawPulseGlow(pulse: Float) {
    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f
    // Attack fazlı: 0→0.15 hızlı yükselir, 0.15→1 yumuşak düşer.
    val alpha = when {
        pulse < 0.15f -> (pulse / 0.15f) * 0.9f
        else -> 0.9f * (1f - (pulse - 0.15f) / 0.85f)
    }.coerceIn(0f, 0.9f)
    val brush = Brush.radialGradient(
        colors = listOf(
            LulseYellow.copy(alpha = alpha),
            LulseYellowDim.copy(alpha = alpha * 0.3f),
            Color.Black,
        ),
        center = center,
        radius = radius,
    )
    drawRect(brush)
}

/** Hafif dim glow — ölçüm bekleniyor (docs 03: "transition to generic measuring animation"). */
private fun DrawScope.drawMeasuringGlow() {
    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f
    val brush = Brush.radialGradient(
        colors = listOf(LulseYellowDim.copy(alpha = 0.15f), Color.Black),
        center = center,
        radius = radius,
    )
    drawRect(brush)
}