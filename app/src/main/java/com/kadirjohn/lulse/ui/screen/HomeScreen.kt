package com.kadirjohn.lulse.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.kadirjohn.lulse.R
import com.kadirjohn.lulse.domain.measurement.MeasurementState
import com.kadirjohn.lulse.domain.motion.MotionState
import com.kadirjohn.lulse.ui.animation.CalmEasing
import com.kadirjohn.lulse.ui.animation.animateHeat
import com.kadirjohn.lulse.ui.components.Accent
import com.kadirjohn.lulse.ui.components.AnimatedGradientBackground
import com.kadirjohn.lulse.ui.components.DebugOverlay
import com.kadirjohn.lulse.ui.components.DebugBubble
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
    onMinimizeDebug: () -> Unit,
    onRestoreDebug: () -> Unit,
    onUpdateBubbleOffset: (Float, Float) -> Unit,
    onConnectWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // heat: HIGH_MOTION=1, STILL=0
    val heatTarget = when (state.motionState) {
        MotionState.HIGH_MOTION -> 1f
        MotionState.SETTLING -> 0.5f
        MotionState.STILL -> 0f
    }
    val heat by animateHeat(heatTarget)

    // Accent lock state'e göre — LOCKED yeşil, ACQUIRING/SEARCHING sarı/dark, hareket nötr.
    val accent = when {
        state.lockState == "LOCKED" -> Accent.LOCKED_GREEN
        state.measurementState == MeasurementState.LOW_CONFIDENCE -> Accent.AMBER
        state.measurementState == MeasurementState.NO_PULSE -> Accent.NEUTRAL
        state.measurementState == MeasurementState.PULSE_DETECTED -> Accent.PULSE
        else -> if (state.motionState == MotionState.STILL) Accent.READY else Accent.NEUTRAL
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPressToggleDebug() },
                )
            },
    ) {
        AnimatedGradientBackground(
            heat = heat,
            accent = accent,
            lastBeatNanos = state.lastBeatNanos,
        )

        // Merkez içerik — state'e göre değişir.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CenterContent(state)
        }

        // Debug overlay en üstte (tam panel).
        DebugOverlay(
            state = state.debug,
            visible = state.debugVisible,
            recording = state.debug.recording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onClose = onCloseDebug,
            onMinimize = onMinimizeDebug,
            onConnectWatch = onConnectWatch,
        )

        // Minimize edilmiş debug balonu (sürüklenebilir, tıkla → panel aç).
        DebugBubble(
            visible = state.debugMinimized,
            offsetX = state.bubbleOffsetX,
            offsetY = state.bubbleOffsetY,
            onTap = onRestoreDebug,
            onDragEnd = { x, y -> onUpdateBubbleOffset(x, y) },
        )
    }
}

@Composable
private fun CenterContent(state: HomeUiState) {
    // Açılış grace period — uygulama açılır açılmaz "hareket azalınca / dik
    // tutuyorsunuz" yazıları hemen çıkmasın. ~5 saniye sonra, ancak yalnızca
    // kullanıcı hâlâ hareketliyse (henüz ölçüm başlamadıysa) bilgilendir.
    // Böylece telefon zaten sabit duruyorsa gereksiz yazı gösterilmez.
    var gracePassed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5000L)
        gracePassed = true
    }

    AnimatedContent(
        targetState = centerKey(state),
        transitionSpec = {
            // Smooth text/content değişimi — 800ms crossFade (kullanıcı UI isteği).
            (fadeIn(tween(800, easing = CalmEasing)) togetherWith fadeOut(tween(600, easing = CalmEasing)))
        },
        label = "center",
    ) { key ->
        when (key) {
            CenterKey.HIGH_MOTION -> GuidanceColumn(
                title = stringRes(R.string.motion_high_title),
                hint = if (state.phoneUpright) stringRes(R.string.motion_upright_hint) else stringRes(R.string.motion_high_hint),
                hintVisible = gracePassed,
            )
            CenterKey.SETTLING -> GuidanceColumn(
                title = stringRes(R.string.motion_settling_title),
                subtitle = stringRes(R.string.motion_settling_subtitle),
            )
            CenterKey.SEARCHING, CenterKey.ACQUIRING -> SearchingContent()
            CenterKey.LOCKED -> PulseContent(state.bpm, state.confidencePct, state.signalQuality)
            CenterKey.NO_PULSE -> NoPulseContent()
            CenterKey.LOW_CONFIDENCE -> PulseContent(state.bpm, state.confidencePct, state.signalQuality)
        }
    }
}

private fun centerKey(state: HomeUiState): CenterKey = when {
    state.lockState == "LOCKED" -> CenterKey.LOCKED
    state.lockState == "ACQUIRING" && state.measurementState != MeasurementState.NO_PULSE -> CenterKey.ACQUIRING
    state.measurementState == MeasurementState.PULSE_DETECTED -> CenterKey.LOCKED
    state.measurementState == MeasurementState.LOW_CONFIDENCE -> CenterKey.LOW_CONFIDENCE
    state.measurementState == MeasurementState.NO_PULSE -> CenterKey.NO_PULSE
    state.measurementState == MeasurementState.SEARCHING_PULSE -> CenterKey.SEARCHING
    else -> when (state.motionState) {
        MotionState.HIGH_MOTION -> CenterKey.HIGH_MOTION
        MotionState.SETTLING -> CenterKey.SETTLING
        MotionState.STILL -> CenterKey.SEARCHING
    }
}

private enum class CenterKey { HIGH_MOTION, SETTLING, SEARCHING, ACQUIRING, LOCKED, NO_PULSE, LOW_CONFIDENCE }

/**
 * Hareket/dik-tutma yönerge sütunu — modern, sakin.
 * [title] açılışta ve hareketli durumda her zaman görünür (ör.
 * "Yatar pozisyona geçin ve telefonu kalbinizin üzerine koyun").
 * [subtitle]/[hint] alt yönerge — [hintVisible] grace period (~5sn)
 * sonrası fade ile açılır, böylece açılışta hemen "hareket azalınca /
 * dik tutuyorsunuz" yazısı çıkmaz, ama konumu sabit kalır.
 */
@Composable
private fun GuidanceColumn(
    title: String,
    subtitle: String? = null,
    hint: String? = null,
    hintVisible: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 28.dp).padding(bottom = 40.dp),
    ) {
        // Ana yönerge — açılışta her zaman görünür, biraz daha kalın (Medium).
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (hint != null) {
            AnimatedVisibility(
                visible = hintVisible,
                enter = fadeIn(tween(800, easing = CalmEasing)),
                exit = fadeOut(tween(600, easing = CalmEasing)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(14.dp))
                    // Alt yönerge (dik tutma / hareket azalınca) — biraz daha büyük, belirgin.
                    Text(
                        hint,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
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
            modifier = Modifier.padding(top = 170.dp),
        ) {
            // "Nabız aranıyor" — ACQUIRING/SEARCHING'de büyük, BPM gösterme.
            Text(stringRes(R.string.ready_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
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
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(14.dp))
        // Büyük BPM fontu — belirgin (kullanıcı UI isteği).
        Text(
            text = "${bpm ?: "—"}",
            fontSize = 88.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringRes(R.string.bpm_unit),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Gereksiz debug-ağırı etiketler kaldırıldı (sinyal kalitesi/güven UI'da değil).
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

// LowConfidenceContent kaldırıldı — LOW_CONFIDENCE artık PulseContent kullanır
// (büyük BPM, gereksiz "Ölçüm kararsız" yazısı yok, kullanıcı UI isteği).

// --- String yardımcıları (Compose stringResource sarmalayıcı) ---

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)