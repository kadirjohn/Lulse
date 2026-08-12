package com.kadirjohn.lulse.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kadirjohn.lulse.R
import com.kadirjohn.lulse.domain.measurement.MeasurementState
import com.kadirjohn.lulse.domain.motion.MotionState
import com.kadirjohn.lulse.ui.animation.CalmEasing
import com.kadirjohn.lulse.ui.animation.animateHeat
import com.kadirjohn.lulse.ui.components.Accent
import com.kadirjohn.lulse.ui.components.AnimatedGradientBackground
import com.kadirjohn.lulse.ui.components.DebugOverlay
import com.kadirjohn.lulse.ui.components.HeartIcon
import com.kadirjohn.lulse.ui.components.HeartMode
import com.kadirjohn.lulse.ui.components.PulsingGlow
import com.kadirjohn.lulse.ui.components.GlowTone

/**
 * Lulse'un tek ekranı (spec §6). State'e göre arka plan, merkez içerik ve metin
 * yumuşakça değişir. Debug overlay gizli (uzun basma ile açılır).
 *
 * @param onLongPressToggleDebug Debug overlay'ı aç/kapat.
 * @param debugControls Kayıt başlat/durdur + debug kapat callback'leri.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onLongPressToggleDebug: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCloseDebug: () -> Unit,
    onDismissIntro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // heat: HIGH_MOTION=1, STILL=0
    val heatTarget = when (state.motionState) {
        MotionState.HIGH_MOTION -> 1f
        MotionState.SETTLING -> 0.5f
        MotionState.STILL -> 0f
    }
    val heat by animateHeat(heatTarget)

    val accent = when (state.measurementState) {
        MeasurementState.PULSE_DETECTED -> Accent.PULSE
        MeasurementState.LOW_CONFIDENCE -> Accent.AMBER
        MeasurementState.NO_PULSE -> Accent.NEUTRAL
        else -> if (state.motionState == MotionState.STILL) Accent.READY else Accent.NEUTRAL
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPressToggleDebug() },
                    onTap = { if (state.showIntro) onDismissIntro() },
                )
            },
    ) {
        AnimatedGradientBackground(heat = heat, accent = accent)

        // Merkez içerik — state'e göre değişir.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CenterContent(state)
        }

        // İlk açılış onboarding katmanı (spec §11) — hafif, dismiss ile kaybolur.
        if (state.showIntro) {
            IntroOverlay(state)
        }

        // Debug overlay en üstte.
        DebugOverlay(
            state = state.debug,
            visible = state.debugVisible,
            recording = state.debug.recording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onClose = onCloseDebug,
        )
    }
}

@Composable
private fun CenterContent(state: HomeUiState) {
    AnimatedContent(
        targetState = centerKey(state),
        transitionSpec = {
            (fadeIn(tween(600, easing = CalmEasing)) togetherWith fadeOut(tween(400, easing = CalmEasing)))
        },
        label = "center",
    ) { key ->
        when (key) {
            CenterKey.HIGH_MOTION -> GuidanceColumn(
                title = stringRes(R.string.motion_high_title),
                subtitle = stringRes(R.string.motion_high_subtitle),
                hint = stringRes(R.string.motion_high_hint),
            )
            CenterKey.SETTLING -> GuidanceColumn(
                title = stringRes(R.string.motion_settling_title),
                subtitle = stringRes(R.string.motion_settling_subtitle),
            )
            CenterKey.SEARCHING -> SearchingContent()
            CenterKey.PULSE_DETECTED -> PulseContent(state.bpm, state.confidencePct, state.signalQuality)
            CenterKey.NO_PULSE -> NoPulseContent()
            CenterKey.LOW_CONFIDENCE -> LowConfidenceContent(state.bpm)
        }
    }
}

private fun centerKey(state: HomeUiState): CenterKey = when (state.measurementState) {
    MeasurementState.PULSE_DETECTED -> CenterKey.PULSE_DETECTED
    MeasurementState.LOW_CONFIDENCE -> CenterKey.LOW_CONFIDENCE
    MeasurementState.NO_PULSE -> CenterKey.NO_PULSE
    MeasurementState.SEARCHING_PULSE -> CenterKey.SEARCHING
    else -> when (state.motionState) {
        MotionState.HIGH_MOTION -> CenterKey.HIGH_MOTION
        MotionState.SETTLING -> CenterKey.SETTLING
        MotionState.STILL -> CenterKey.SEARCHING
    }
}

private enum class CenterKey { HIGH_MOTION, SETTLING, SEARCHING, PULSE_DETECTED, NO_PULSE, LOW_CONFIDENCE }

@Composable
private fun GuidanceColumn(title: String, subtitle: String, hint: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(18.dp))
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SearchingContent() {
    Box(contentAlignment = Alignment.Center) {
        PulsingGlow(
            color = GlowTone.READY,
            intensity = 1f,
            modifier = Modifier.size(280.dp),
        )
        HeartIcon(
            mode = HeartMode.SEARCHING,
            bpm = null,
            modifier = Modifier.size(120.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 160.dp),
        ) {
            Text(stringRes(R.string.ready_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(stringRes(R.string.ready_subtitle), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PulseContent(bpm: Int?, confidencePct: Int?, quality: SignalQuality) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeartIcon(
            mode = HeartMode.PULSE_DETECTED,
            bpm = bpm,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "${bpm ?: "—"}",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringRes(R.string.bpm_unit),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(stringRes(R.string.pulse_detected_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(qualityText(quality), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (confidencePct != null) {
            Text(stringResArgs(R.string.confidence_label, confidencePct), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoPulseContent() {
    Box(contentAlignment = Alignment.Center) {
        PulsingGlow(color = GlowTone.NEUTRAL, intensity = 0.6f, modifier = Modifier.size(260.dp))
        HeartIcon(mode = HeartMode.NO_PULSE, bpm = null, modifier = Modifier.size(110.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 150.dp),
        ) {
            Text(stringRes(R.string.no_pulse_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text(stringRes(R.string.no_pulse_hint_reposition), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(stringRes(R.string.no_pulse_hint_still), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun LowConfidenceContent(bpm: Int?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeartIcon(mode = HeartMode.LOW_CONFIDENCE, bpm = bpm, modifier = Modifier.size(70.dp))
        Spacer(Modifier.height(10.dp))
        Text("${bpm ?: "—"} ${stringRes(R.string.bpm_unit)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(stringRes(R.string.low_confidence_tag), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(14.dp))
        Text(stringRes(R.string.low_confidence_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(stringRes(R.string.low_confidence_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
private fun IntroOverlay(state: HomeUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 60.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Text(stringRes(R.string.intro_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringRes(R.string.intro_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(stringRes(R.string.intro_disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        }
    }
}

// --- String yardımcıları (Compose stringResource sarmalayıcı) ---

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringResArgs(id: Int, vararg args: Any): String = androidx.compose.ui.res.stringResource(id, *args)

private fun qualityText(q: SignalQuality): String = when (q) {
    SignalQuality.HIGH -> "Sinyal kalitesi: Yüksek"
    SignalQuality.MEDIUM -> "Sinyal kalitesi: Orta"
    SignalQuality.LOW -> "Sinyal kalitesi: Düşük"
    SignalQuality.UNKNOWN -> ""
}