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
    // Pulse (Faz 5'e kadar null — mock kapalı).
    val bpm: Int? = null,
    val confidencePct: Int? = null,
    val signalQuality: SignalQuality = SignalQuality.UNKNOWN,
    // İlk açılış onboarding katmanı.
    val showIntro: Boolean = true,
    // Debug overlay.
    val debugVisible: Boolean = false,
    val debug: DebugUiState = DebugUiState(),
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
    val recording: Boolean = false,
    val recordedCount: Int = 0,
    val lastExportPath: String? = null,
    val bufferDropped: Int = 0,
    val bufferSize: Int = 0,
)