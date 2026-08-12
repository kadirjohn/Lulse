package com.kadirjohn.lulse.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kadirjohn.lulse.ui.theme.Amber
import com.kadirjohn.lulse.ui.theme.GlowWhite
import com.kadirjohn.lulse.ui.theme.PulseSoftRed
import com.kadirjohn.lulse.ui.theme.PulseWarmWhite
import kotlin.math.absoluteValue

/**
 * Merkez kalp ikonu — duruma göre dolgu/çerçeve ve pulse animasyonu (spec §6.C/D/E).
 *
 * @param mode Görsel mod: arıyorum (outline+glow), bulundu (dolu+sıcak), yok (sönük), düşük güven (amber).
 * @param bpmVar BPM verisi varsa pulse hızı BPM'e senkronize edilir.
 */
@Composable
fun HeartIcon(
    mode: HeartMode,
    bpm: Int?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "heart")
    // Pulse hızı: BPM varsa 60/bpm saniye; yoksa sakin 1.4sn.
    val beatMs = if (bpm != null && bpm in 30..220) (60_000f / bpm).toInt() else 1400
    val beatPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = beatMs, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "beatPhase",
    )

    val (fillColor, strokeColor, glowColor, alpha) = when (mode) {
        HeartMode.SEARCHING -> Quad(GlowWhite.copy(alpha = 0.85f), GlowWhite, GlowWhite, 1f)
        HeartMode.PULSE_DETECTED -> Quad(PulseWarmWhite, PulseSoftRed, PulseWarmWhite, 1f)
        HeartMode.NO_PULSE -> Quad(Color.Transparent, GlowWhite.copy(alpha = 0.35f), NeutralGrayFaint, 0.6f)
        HeartMode.LOW_CONFIDENCE -> Quad(Color.Transparent, Amber, Amber.copy(alpha = 0.4f), 0.7f)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        // Kalp path'i — iki yay + üçgen alt.
        val heartPath = Path().apply {
            val top = h * 0.32f
            val bottom = h * 0.78f
            moveTo(cx, bottom)
            cubicTo(
                x1 = w * 0.08f, y1 = h * 0.52f,
                x2 = w * 0.18f, y2 = top - h * 0.04f,
                x3 = cx, y3 = h * 0.46f,
            )
            cubicTo(
                x1 = w * 0.82f, y1 = top - h * 0.04f,
                x2 = w * 0.92f, y2 = h * 0.52f,
                x3 = cx, y3 = bottom,
            )
            close()
        }

        // Pulse: BPM varsa her atımda büyü, yoksa hafif nefes.
        val pulseScale = if (bpm != null) {
            1f + 0.10f * (1f - (beatPhase - 0.5f).absoluteValue * 2f).coerceIn(0f, 1f)
        } else {
            1f + 0.04f * kotlin.math.sin(beatPhase * 6.283f)
        }
        val drawScale = pulseScale * alpha

        // Halo görsel efekti: dış çember radial — kalın+düşük alpha katmanlarla glow.
        for (i in 3 downTo 1) {
            drawPath(
                path = heartPath,
                color = glowColor.copy(alpha = 0.06f * i * alpha),
                style = Stroke(width = (i * 6).dp.toPx()),
            )
        }
        // Dolgu (varsa).
        if (fillColor != Color.Transparent) {
            drawPath(path = heartPath, color = fillColor.copy(alpha = 0.9f * drawScale))
        }
        // Ana çerçeve.
        drawPath(
            path = heartPath,
            color = strokeColor,
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}

enum class HeartMode { SEARCHING, PULSE_DETECTED, NO_PULSE, LOW_CONFIDENCE }

private data class Quad(val fill: Color, val stroke: Color, val glow: Color, val alpha: Float)

private val NeutralGrayFaint = Color(0xFF4A4A52)