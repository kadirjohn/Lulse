package com.kadirjohn.lulse.domain.signal

import com.kadirjohn.lulse.data.sensor.SensorSample
import com.kadirjohn.lulse.data.sensor.SensorType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Canlı BPM tahmini için sinyal işleme pipeline'ı (spec §9, Faz 5).
 *
 * Faz 4 (offline Python analizi) ile doğrulanan parametreler:
 *  - Birincil kanal: ACCELEROMETER.z (en güçlü kardiyak sinyal, env_std en yüksek).
 *  - Doğrulama kanalı: GYROSCOPE.x (çok düşük gürültü, aynı BPM).
 *  - Bant: 5–30 Hz Butterworth band-pass (kalp mekanik titreşim bandı).
 *  - Envelope: Hilbert yaklaşıklığı yerine **rectify + moving-RMS** (cihaz üstünde
 *    ucuz, Hilbert kadar kaynak tüketmez; offline testte yakın sonuç verdi).
 *  - Beat: envelope peak detection, min interval 0.33s (≤180 bpm).
 *  - BPM: 60 / median(IBI), son 3–6 beat'in robust ortalaması.
 *
 * Not: Cihaz üstünde gerçek zamanlı çalıştığı için pencere tabanlı, stateful.
 * [process] her analiz tick'inde (yaklaşık 200ms) çağrılır ve ring buffer'ın
 * son penceresini alır.
 *
 * TODO (Faz 6): confidence score (motion stability, beat consistency, kanal uyumu).
 */
class SignalProcessor(
    /** Analiz penceresi uzunluğu (saniye) — uzun pencere daha stabil ama yavaş. */
    private val windowSeconds: Float = 6f,
    /** Beklenen sample rate (Hz). ±%30 sapma tolere edilir. */
    private val expectedSampleRateHz: Float = 199f,
    // Beat eşikleri.
    private val minBeatIntervalSec: Float = 0.33f, // ≤180 bpm
    private val maxBeatIntervalSec: Float = 1.5f,  // ≥40 bpm
    /** BPM için kullanılacak son IBI sayısı (robust median). */
    private val recentIbiCount: Int = 5,
) {

    data class SignalResult(
        val bpm: Int,
        val ibisMs: List<Long>,
        val channel: SensorType,
        val confidence: Float,
        /** En son beat anı (event timestamp nanos) — UI pulse sync için. */
        val lastBeatNanos: Long?,
        /** Son envelope peak'lerinin zaman damgaları (UI pulse animasyonu için). */
        val recentBeatNanos: List<Long>,
    )

    /**
     * Pencereyi işle ve BPM üret. Yeterli veri/düzen yoksa null (dürüst "yok").
     *
     * Sıralama:
     *  1. Acc.z penceresini al; eksikse gyro.x'e düş.
     *  2. Band-pass 5–30 Hz.
     *  3. Rectify + moving-RMS envelope.
     *  4. Peak detection (adaptive threshold + min interval).
     *  5. IBI → median → BPM; düzen (CV) kontrolü.
     */
    fun process(window: List<SensorSample>): SignalResult? {
        // 1. Kanal seç.
        val accZ = window.filter { it.sensorType == SensorType.ACCELEROMETER }
        val primary = accZ.map { it.z }.toFloatArray()
        val channel = SensorType.ACCELEROMETER

        if (primary.size < 20) return null

        // Tahmini fs: event timestamp'lerinden.
        val sorted = accZ.sortedBy { it.timestampNanos }
        val fs = estimateFs(sorted)
        if (fs < 60f) return null // Nyquist 30Hz altı — kardiyak bandı göremez.

        // 2. Band-pass 5–30 Hz (butterworth biquad cascade).
        val filtered = bandPass(primary, fs, loHz = 5f, hiHz = 30f)

        // 3. Envelope: rectify + moving RMS (~0.1s pencere).
        val envWindow = (fs * 0.1f).toInt().coerceAtLeast(1)
        val envelope = movingRms(rectify(filtered), envWindow)

        // 4. Peak detection.
        val threshold = envelope.mean() + envelope.std() * 0.6f
        val minDist = (fs * minBeatIntervalSec).toInt()
        val peakIdx = findPeaks(envelope, threshold, minDist)
        if (peakIdx.size < 3) return null

        // Beat zamanları (nanos).
        val beatNanos = peakIdx.map { sorted[it].timestampNanos }
        val ibis = beatNanos.zipWithNext { a, b -> b - a }
            .filter { it in (minBeatIntervalSec * 1e9).toLong()..(maxBeatIntervalSec * 1e9).toLong() }
        if (ibis.size < 2) return null

        // 5. BPM = 60 / median(IBI).
        val ibiSec = median(ibis) / 1e9f
        if (ibiSec <= 0f) return null
        val bpm = (60f / ibiSec).toInt()

        // Düzen kontrolü — IBI cv yüksekse düşük güven / null.
        val cv = ibis.std() / ibis.mean()
        val confidence = when {
            cv < 0.12f -> 0.9f
            cv < 0.25f -> 0.6f
            else -> return null // çok düzensiz — dürüstçe "yok" de.
        }
        if (bpm !in 40..180) return null

        return SignalResult(
            bpm = bpm,
            ibisMs = ibis.map { it / 1_000_000 },
            channel = channel,
            confidence = confidence,
            lastBeatNanos = beatNanos.last(),
            recentBeatNanos = beatNanos.takeLast(8),
        )
    }

    // --- Yardımcılar ---

    private fun estimateFs(samples: List<SensorSample>): Float {
        if (samples.size < 2) return expectedSampleRateHz
        val dt = (samples.last().timestampNanos - samples.first().timestampNanos).toFloat() / 1e9f
        return (samples.size - 1) / dt
    }

    /** Band-pass via cascade low-pass + high-pass biquad (Butterworth 2. derece each). */
    private fun bandPass(x: FloatArray, fs: Float, loHz: Float, hiHz: Float): FloatArray {
        val lp = biquadLowPass(x, fs, hiHz)
        val hp = biquadHighPass(lp, fs, loHz)
        return hp
    }

    private fun biquadLowPass(x: FloatArray, fs: Float, cutoffHz: Float): FloatArray {
        val w0 = 2 * PI * cutoffHz / fs
        val cosW = cos(w0).toFloat()
        val sinW = kotlin.math.sin(w0).toFloat()
        val alpha = sinW / sqrt(2f) // Butterworth Q
        val b0 = ((1 - cosW) / 2).toFloat()
        val b1 = (1 - cosW).toFloat()
        val b2 = b0
        val a0 = (1 + alpha)
        val a1 = (-2 * cosW).toFloat()
        val a2 = (1 - alpha)
        return biquad(x, b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun biquadHighPass(x: FloatArray, fs: Float, cutoffHz: Float): FloatArray {
        val w0 = 2 * PI * cutoffHz / fs
        val cosW = cos(w0).toFloat()
        val sinW = kotlin.math.sin(w0).toFloat()
        val alpha = sinW / sqrt(2f)
        val b0 = ((1 + cosW) / 2).toFloat()
        val b1 = (-(1 + cosW)).toFloat()
        val b2 = b0
        val a0 = (1 + alpha)
        val a1 = (-2 * cosW).toFloat()
        val a2 = (1 - alpha)
        return biquad(x, b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun biquad(x: FloatArray, b0: Float, b1: Float, b2: Float, a1: Float, a2: Float): FloatArray {
        val y = FloatArray(x.size)
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        for (i in x.indices) {
            val xn = x[i]
            val yn = b0 * xn + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            y[i] = yn
            x2 = x1; x1 = xn
            y2 = y1; y1 = yn
        }
        return y
    }

    private fun rectify(x: FloatArray): FloatArray = FloatArray(x.size) { i -> if (x[i] < 0) -x[i] else x[i] }

    private fun movingRms(x: FloatArray, window: Int): FloatArray {
        val out = FloatArray(x.size)
        val w = window.coerceAtLeast(1)
        var sum = 0.0
        for (i in x.indices) {
            sum += x[i] * x[i]
            if (i >= w) sum -= x[i - w] * x[i - w]
            val n = minOf(i + 1, w)
            out[i] = sqrt((sum / n).toFloat())
        }
        return out
    }

    private fun findPeaks(env: FloatArray, threshold: Float, minDist: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        var lastPeak = -minDist - 1
        for (i in 1 until env.size - 1) {
            if (env[i] > threshold && env[i] >= env[i - 1] && env[i] > env[i + 1]) {
                if (i - lastPeak >= minDist) {
                    peaks.add(i)
                    lastPeak = i
                }
            }
        }
        return peaks
    }

    private fun median(values: List<Long>): Long {
        val s = values.sorted()
        return s[s.size / 2]
    }

    private fun FloatArray.mean(): Float = if (isEmpty()) 0f else sum() / size
    private fun FloatArray.std(): Float {
        if (size == 0) return 0f
        val m = mean()
        var s = 0f
        for (v in this) s += (v - m) * (v - m)
        return sqrt(s / size)
    }
    private fun List<Long>.mean(): Float = if (isEmpty()) 0f else sum().toFloat() / size
    private fun List<Long>.std(): Float {
        if (size == 0) return 0f
        val m = mean()
        var s = 0f
        for (v in this) s += (v - m) * (v - m)
        return sqrt(s / size)
    }
}