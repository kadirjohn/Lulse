package com.kadirjohn.lulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kadirjohn.lulse.ui.animation.rememberBreathCycle
import com.kadirjohn.lulse.ui.theme.Amber
import com.kadirjohn.lulse.ui.theme.GlowWhite
import com.kadirjohn.lulse.ui.theme.NeutralGray

/**
 * Merkezde yumuşak nefes-alan glow halkası (spec §6.B/C).
 *
 * Settling durumunda küçülerek stabilize olur; still'de sakin soluma.
 * @param color glow rengi (duruma göre).
 * @param intensity 0..1 — ne kadar belirgin.
 */
@Composable
fun PulsingGlow(
    color: GlowTone,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    val phase by rememberBreathCycle(durationMs = 4200, intensity = intensity)
    val baseColor = when (color) {
        GlowTone.READY -> GlowWhite
        GlowTone.NEUTRAL -> NeutralGray
        GlowTone.AMBER -> Amber
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val maxR = minOf(w, h) / 2f
        val breath = phase // 0..1 salınım
        val r = maxR * (0.72f + 0.08f * breath)
        val a = (0.05f + 0.05f * breath) * intensity.coerceIn(0f, 1f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(baseColor.copy(alpha = a), Color.Transparent),
                center = Offset(cx, cy),
                radius = r,
            ),
        )
    }
}

enum class GlowTone { READY, NEUTRAL, AMBER }