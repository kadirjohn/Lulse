package com.kadirjohn.lulse.domain.signal

import com.kadirjohn.lulse.data.sensor.SensorSample
import com.kadirjohn.lulse.data.sensor.SensorType

/**
 * Canlı BPM tahmini için sinyal işleme pipeline'ı (spec §9).
 *
 * Bu sınıf V1'de **implement edilmedi** — sadece iskelet ve TODO marker.
 * Faz 4 (offline Python analizi) ile kanal/bant seçimi doğrulandıktan sonra
 * Faz 5'te bu yapı doldurulacak.
 *
 * Planlanan pipeline:
 *  1. Motion gating (zaten [com.kadirjohn.lulse.domain.motion.MotionAnalyzer]).
 *  2. Cardiac band filtreleme (~5–30 Hz) — accelerometer/gyroscope ayrı ayrı.
 *  3. Rectification + moving-RMS envelope.
 *  4. Adaptive threshold ile beat adayı tespiti.
 *  5. Beat validation (min interval, fizyolojik aralık, kanal uyumu).
 *  6. IBI → robust average → BPM = 60 / IBI.
 *  7. Confidence (bkz. [com.kadirjohn.lulse.domain.confidence.ConfidenceScorer]).
 *
 * TODO (Faz 5): implement et. Girdi: ring buffer penceresi.
 * Çıktı: [SignalResult] (BPM + IBI listesi + kullanılan kanal).
 */
class SignalProcessor {

    /** Faz 5'te doldurulacak çıktı modeli. */
    data class SignalResult(
        val bpm: Int,
        val ibisMs: List<Long>,
        val channel: SensorType,
        val confidence: Float,
    )

    /**
     * @suppress V1 stub — her zaman null döner (mock kapalı politika).
     * Gerçek implementasyon Faz 5'te.
     */
    fun process(window: List<SensorSample>): SignalResult? = null
}