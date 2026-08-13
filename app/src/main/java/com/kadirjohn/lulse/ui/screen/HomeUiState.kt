package com.kadirjohn.lulse.ui.screen

import com.kadirjohn.lulse.domain.measurement.MeasurementState
import com.kadirjohn.lulse.domain.motion.MotionScore
import com.kadirjohn.lulse.domain.motion.MotionState

/**
 * Tek ekrana giden tek state (MVVM — ViewModel üretir, UI tüketir).
 *
 * [HomeScreen] bu state'e göre arka plan, merkez içerik ve metni seçer.
 * Debug alanı ana içerikten bağımsız [debug] içinde tutulur.
 */
data class HomeUiState(
    val motionState: MotionState = MotionState.HIGH_MOTION,
    val measurementState: MeasurementState = MeasurementState.IDLE,
    val motionScore: MotionScore = MotionScore.EMPTY,
    // Pulse (Faz 5).
    val bpm: Int? = null,
    val confidencePct: Int? = null,
    val signalQuality: SignalQuality = SignalQuality.UNKNOWN,
    /** En son beat anı (event timestamp nanos) — UI glow pulse sync için. */
    val lastBeatNanos: Long? = null,
    /** Son beat zamanları — UI'ın tık-tık glow'u için. */
    val recentBeatNanos: List<Long> = emptyList(),
    // Telefon dik mi tutuluyor? (orientation gating) — UI yönlendirme için.
    val phoneUpright: Boolean = false,
    // İlk açılış onboarding katmanı.
    val showIntro: Boolean = true,
    // Debug overlay.
    val debugVisible: Boolean = false,
    val debug: DebugUiState = DebugUiState(),
    // Debug panel minimize edildiğinde sürüklenebilir balon modu.
    // debugVisible=true → panel tam açık; debugMinimized=true → balon göster.
    // İkisi de false → hiçbir debug UI yok (tamamen kapalı).
    val debugMinimized: Boolean = false,
    /** Balon'un ekran konumu (px offset, sağ-üst köşeden). Sürükleme sırasında güncellenir. */
    val bubbleOffsetX: Float = 0f,
    val bubbleOffsetY: Float = 0f,
)

enum class SignalQuality { UNKNOWN, HIGH, MEDIUM, LOW }

/**
 * Debug overlay içeriği (spec §7). Ana UI'yi kirletmez.
 */
data class DebugUiState(
    val sensorAvailability: Map<String, Boolean> = emptyMap(),
    val sensorInfo: Map<String, String?> = emptyMap(),
    val sampleRateHz: Map<String, Float> = emptyMap(),
    val motionScore: Float = 0f,
    val accelVariance: Float = 0f,
    val gyroEnergy: Float = 0f,
    val jerk: Float = 0f,
    val motionState: MotionState = MotionState.HIGH_MOTION,
    val measurementState: MeasurementState = MeasurementState.IDLE,
    val orientation: com.kadirjohn.lulse.domain.motion.Orientation =
        com.kadirjohn.lulse.domain.motion.Orientation.UNKNOWN,
    val bpm: Int? = null,
    val confidence: Float? = null,
    val recording: Boolean = false,
    val recordedCount: Int = 0,
    val lastExportPath: String? = null,
    val bufferDropped: Int = 0,
    val bufferSize: Int = 0,
)