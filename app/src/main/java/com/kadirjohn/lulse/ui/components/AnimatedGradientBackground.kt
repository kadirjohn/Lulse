package com.kadirjohn.lulse.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import com.kadirjohn.lulse.ui.animation.rememberBreathCycle
import com.kadirjohn.lulse.ui.theme.Amber
import com.kadirjohn.lulse.ui.theme.Black
import com.kadirjohn.lulse.ui.theme.LockedGreenDim
import com.kadirjohn.lulse.ui.theme.LockedGreenGlow
import com.kadirjohn.lulse.ui.theme.MotionCrimson
import com.kadirjohn.lulse.ui.theme.MotionDeepRed
import com.kadirjohn.lulse.ui.theme.MotionEmber
import com.kadirjohn.lulse.ui.theme.MotionRed
import com.kadirjohn.lulse.ui.theme.NearBlack
import com.kadirjohn.lulse.ui.theme.NeutralGray
import com.kadirjohn.lulse.ui.theme.PulseSoftRed
import com.kadirjohn.lulse.ui.theme.PulseWarmWhite
import com.kadirjohn.lulse.ui.theme.ReadyGlow
import kotlin.math.cos
import kotlin.math.sin

/**
 * State'e göre değişen hareketli radial gradient arka plan (spec §5/§6).
 *
 * [heat] 0..1: 1 = tam kırmızı/bordo (HIGH_MOTION), 0 = siyah (STILL).
 * [accent] duruma göre ek vurgu rengi (NO_PULSE nötr, LOW_CONF amber, PULSE sıcak).
 * Gradient hafif "breathing" ile akar; sakinleştikçe yavaşlar ve söner.
 *
 * **Beat glow:** [lastBeatNanos] değiştikçe (yeni beat geldikçe) merkezde beyaz
 * bir glow "tık" diye parlar ve hızla söner — nabız gerçekten atıyor mu diye
 * gözle ayırt edebilmek için. Nabız yoksa ([lastBeatNanos] == null) glow siyah.
 */
@Composable
fun AnimatedGradientBackground(
    heat: Float,
    accent: Accent,
    lastBeatNanos: Long?,
    modifier: Modifier = Modifier,
) {
    // Nefes döngüsü: hareketliyken hızlı, sakinken yavaş ve hafif.
    val breathDuration = lerp(2600, 6200, 1f - heat).toInt()
    val phase by rememberBreathCycle(durationMs = breathDuration, intensity = heat.coerceIn(0f, 1f))

    // Beat pulse: her yeni beat'te 1'e sıçra, ~220ms'de 0'a sön.
    val beatPulse = remember { Animatable(0f) }
    LaunchedEffect(lastBeatNanos) {
        if (lastBeatNanos != null) {
            beatPulse.snapTo(1f)
            beatPulse.animateTo(0f, tween(220, easing = LinearEasing))
        }
    }
    val pulseValue = beatPulse.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .drawBehind {
                val w = size.width
                val h = size.height
                // Gradient merkezleri phase'e göre yavaşça kayar (flow hissi).
                val cx1 = w * (0.35f + 0.12f * sin(phase * PI2))
                val cy1 = h * (0.32f + 0.10f * cos(phase * PI2))
                val cx2 = w * (0.68f + 0.12f * cos(phase * PI2 + 1f))
                val cy2 = h * (0.70f + 0.10f * sin(phase * PI2 + 1f))
                val breath = phase // 0..1 salınım

                val baseColor = blend(Black, NearBlack, 0.15f + 0.1f * breath)
                val hotColor = blend(MotionCrimson, MotionRed, breath)
                val deepColor = blend(MotionDeepRed, MotionEmber, breath)

                val heatC = heat.coerceIn(0f, 1f)
                val brush = Brush.radialGradient(
                    colors = listOf(
                        blend(baseColor, hotColor, heatC),
                        blend(baseColor, deepColor, heatC * 0.6f),
                        blend(baseColor, Black, heatC * 0.2f),
                        Black,
                    ),
                    center = Offset(cx1, cy1),
                    radius = maxOf(w, h) * (0.55f + 0.08f * breath),
                )
                val accentBrush = accentBrush(accent, cx2, cy2, maxOf(w, h) * 0.5f, breath)

                drawRect(brush)
                drawRect(accentBrush)

                // Beat glow — merkezde beyaz, her beat'te parlayıp sön.
                // Nabız yoksa pulseValue 0 => glow çizilmez => siyah kalır.
                if (pulseValue > 0.01f) {
                    val glowColor = if (accent == Accent.AMBER) Amber else PulseWarmWhite
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.55f * pulseValue),
                                glowColor.copy(alpha = 0.18f * pulseValue),
                                Color.Transparent,
                            ),
                            center = Offset(w * 0.5f, h * 0.5f),
                            radius = maxOf(w, h) * (0.5f + 0.15f * pulseValue),
                            tileMode = TileMode.Clamp,
                        ),
                    )
                }
            },
    )
}

/** Duruma göre ikincil vurgu rengi. LOCKED_GREEN — güven yüksek, yeşil arka plan. */
enum class Accent { NEUTRAL, READY, PULSE, AMBER, LOCKED_GREEN }

private fun accentBrush(accent: Accent, cx: Float, cy: Float, radius: Float, breath: Float): Brush {
    val (c1, c2) = when (accent) {
        Accent.READY -> ReadyGlow to NeutralGray
        Accent.PULSE -> PulseWarmWhite to PulseSoftRed
        Accent.AMBER -> Amber to NeutralGray
        Accent.LOCKED_GREEN -> LockedGreenGlow to LockedGreenDim
        Accent.NEUTRAL -> NeutralGray to Black
    }
    val alpha = (0.10f + 0.06f * breath)
    return Brush.radialGradient(
        colors = listOf(c1.copy(alpha = alpha), Color.Transparent),
        center = Offset(cx, cy),
        radius = radius,
        tileMode = TileMode.Clamp,
    )
}

// --- Renk yardımcıları ---

private fun blend(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = lerp(a.red, b.red, tt),
        green = lerp(a.green, b.green, tt),
        blue = lerp(a.blue, b.blue, tt),
        alpha = lerp(a.alpha, b.alpha, tt),
    )
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t.coerceIn(0f, 1f)).toInt()

private const val PI2 = 6.2831855f