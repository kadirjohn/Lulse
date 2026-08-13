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
    // --- Pulse lock tracker debug (kullanıcı mimarisi: SEARCHING/ACQUIRING/LOCKED) ---
    /** Lock state — SEARCHING/ACQUIRING/LOCKED. */
    val lockState: String,
    /** Locked BPM (null = aranıyoı). */
    val lockedBpm: Int?,
    /** Lock yaşı (tick sayısı). */
    val lockAgeTicks: Int,
    /** State değişim sebebi (debug). */
    val switchReason: String,
    /** Raw candidate BPM (en güçlü ACF peak). */
    val rawCandidateBpm: Int?,
    /** Half candidate (raw/2). */
    val halfCandidateBpm: Int?,
    /** Double candidate (raw×2). */
    val doubleCandidateBpm: Int?,
    /** Raw ACF gücü. */
    val rawAcfStrength: Float?,
    /** Half ACF gücü. */
    val halfAcfStrength: Float?,
    /** Double ACF gücü. */
    val doubleAcfStrength: Float?,
    /** Seçilen hypothesis etiketi. */
    val selectedHypothesis: String,
    /** Watch referans BPM snapshot (sadece debug CSV, runtime kararı değil). */
    val watchBpm: Int?,
    // --- BeatEventGate debug (flash dedup doğrulaması için) ---
    /** Ekranda gösterilen BPM (locked, raw değil). */
    val displayBpm: Int?,
    /** Beat event ID — her accepted beat'te artar. */
    val beatEventId: Long,
    /** Bu tick'te candidate beat zamanı (raw SignalProcessor'dan). */
    val beatCandidateNanos: Long?,
    /** Bu tick'te beat kabul edildi mi? */
    val beatAccepted: Boolean,
    /** Kabul edilen beat zamanı (sadece accepted ise non-null). */
    val acceptedBeatNanos: Long?,
    /** Red sebebi (duplicate/refractory/no_lock). */
    val beatRejectionReason: String,
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
    // Pulse lock tracker debug alanları.
    "lock_state",
    "locked_bpm",
    "lock_age_ticks",
    "switch_reason",
    "raw_candidate_bpm",
    "half_candidate_bpm",
    "double_candidate_bpm",
    "raw_acf_strength",
    "half_acf_strength",
    "double_acf_strength",
    "selected_hypothesis",
    "watch_bpm",
    // BeatEventGate debug.
    "display_bpm",
    "beat_event_id",
    "beat_candidate_nanos",
    "beat_accepted",
    "accepted_beat_nanos",
    "beat_rejection_reason",
)