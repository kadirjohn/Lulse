package com.kadirjohn.lulse.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

/**
 * Lulse animasyon yardımcıları — sakin, premium, yumuşak geçişler (spec §5/§17).
 *
 * Felsefe: abartısız, "breathing" hissi. Hızlı/snappy değil; yavaş ease-in-out.
 */

/** Sakin, sinematik ease. */
val CalmEasing: Easing = CubicBezierEasing(0.42f, 0.0f, 0.25f, 1.0f)

/** Daha yavaş, nefes-alır gibi ease (still/hazır durumları). */
val BreathEasing: Easing = CubicBezierEasing(0.37f, 0.0f, 0.63f, 1.0f)

/** Renk/blur geçişleri için standart süre (ms). */
const val COLOR_TRANSITION_MS = 900
const val CONTENT_TRANSITION_MS = 600

/**
 * 0..1 arasında "sıcaklık" değerini yumuşakça animasyonla.
 * HIGH_MOTION → 1 (kırmızı), STILL → 0 (siyah).
 */
@Composable
fun animateHeat(target: Float): State<Float> =
    animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = COLOR_TRANSITION_MS, easing = CalmEasing),
        label = "heat",
    )

/**
 * Sürekli nefes-alma animasyonu (gradient flow / glow pulse için).
 * @param durationMs bir nefes döngüsünün süresi.
 * @param intensity 0..1 — durum ne kadar sakinse döngü o kadar yavaş.
 */
@Composable
fun rememberBreathCycle(durationMs: Int, intensity: Float): State<Float> {
    val transition = rememberInfiniteTransition(label = "breath")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = BreathEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathPhase",
    )
    // intensity düşükse değer aralığını daralt → sakinleşince hareket azalsın.
    return object : State<Float> {
        override val value: Float = phase * (0.5f + 0.5f * intensity)
    }
}

/**
 * Kalp pulse genliği — ölçüm durumuna göre.
 */
@Composable
fun animateHeartPulseScale(pulse: Boolean, bpm: Int?): State<Float> {
    val target = when {
        bpm != null -> 1f
        pulse -> 0.5f
        else -> 0.2f
    }
    return animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = CONTENT_TRANSITION_MS, easing = FastOutSlowInEasing),
        label = "heartPulse",
    )
}