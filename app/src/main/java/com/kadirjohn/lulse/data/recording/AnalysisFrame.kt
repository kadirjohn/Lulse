package com.kadirjohn.lulse.data.recording

import com.kadirjohn.lulse.domain.measurement.MeasurementState
import com.kadirjohn.lulse.domain.motion.MotionState
import com.kadirjohn.lulse.domain.motion.Orientation

/**
 * Tek bir analiz tick'inin (~200ms) debug snapshot'ı.
 *
 * [MainViewModel.analyze] her tick'te üretir; aktif kayıtta [RecordingManager]
 * tarafından toplanır ve CSV'ye sensör verisinden sonra ikinci bir tablo olarak
 * yazılır. Böylece offline analizde algoritmanın canlıda ne ürettiği
 * (BPM, confidence, hangi verdict, neden reddetti) satır satır incelenebilir.
 *
 * [timestampNanos] tick'inin sensör event zamanı (UI/sensör zaman ekseniyle
 * uyumlu — wall-clock değil).
 */
data class AnalysisFrame(
    val timestampNanos: Long,
    /** SignalProcessor çıktısı — BPM (null = beat bulunamadı/reddedildi). */
    val bpm: Int?,
    /** Confidence 0–1 (null = pulse yok). */
    val confidence: Float?,
    /** SignalProcessor verdict'ı ("primary-acf", "harmonic->fundamental", ...). */
    val verdict: String?,
    /** Hareket durumu. */
    val motionState: MotionState,
    /** Ölçüm durumu (STILL iken pulse arama sonucu). */
    val measurementState: MeasurementState,
    /** Birleşik hareket skoru (0=sabit, yüksek=haraketli). */
    val motionScoreTotal: Float,
    /** Accelerometer pencere varyansı. */
    val accelVariance: Float,
    /** Gyroscope enerjisi. */
    val gyroEnergy: Float,
    /** Jerk büyüklüğü. */
    val jerk: Float,
    /** Telefon duruşu. */
    val orientation: Orientation,
    /** Telefon dik tutuluyor mu? (orientation gating) */
    val phoneUpright: Boolean,
    /** Ring buffer'daki toplam sample sayısı (tüm sensörler). */
    val bufferSize: Int,
    /** Buffer'dan şimdiye kadar düşürülen sample (kapasite aşımı). */
    val bufferDropped: Int,
    /** Her sensör tipi için ölçülen sample rate (Hz). */
    val sampleRatesHz: Map<String, Float>,
)

/** CSV'de analiz tablosu için sabit başlık sırası. */
internal val ANALYSIS_HEADER = listOf(
    "timestamp_nanos",
    "bpm",
    "confidence",
    "verdict",
    "motion_state",
    "measurement_state",
    "motion_score_total",
    "accel_variance",
    "gyro_energy",
    "jerk",
    "orientation",
    "phone_upright",
    "buffer_size",
    "buffer_dropped",
    "sample_rate_accel_hz",
    "sample_rate_gyro_hz",
    "sample_rate_linear_hz",
)