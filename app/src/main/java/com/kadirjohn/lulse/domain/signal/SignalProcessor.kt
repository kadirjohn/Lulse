package com.kadirjohn.lulse.domain.signal

import com.kadirjohn.lulse.data.sensor.SensorSample
import com.kadirjohn.lulse.data.sensor.SensorType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Canlı BPM tahmini için sinyal işleme pipeline'ı — **autocorrelation-birincil**
 * mimari (Faz 5, v2).
 *
 * Gerçek cihaz verisi (lulsedata/, 5 CSV) + kullanıcının bağımsız analizi ile
 * doğrulanmıştır. Mimari:
 *
 *  ```
 *  ACCELEROMETER.z (kendi fs'i, timestamp'ten)
 *        ↓
 *  5–30 Hz band-pass (Nyquist-safe adaptif: 50Hz'de 5–20)
 *        ↓
 *  rectify (abs) → envelope + smoothing (~0.08s)
 *        ↓
 *  AUTOCORRELATION (BİRİNCİL) — 40–180 bpm lag aralığında ACF spektrumu
 *        ↓
 *  candidate periods + ACF güçleri
 *        ↓
 *  harmonic disambiguation (hipotez testi):
 *    high candidate (>110) ve yarısı physiological+güçlü → fundamental seç
 *        ↓
 *  BPM + confidence (ACF gücüne göre)
 *  beat zamanları: ACF periyoduyla doğrulanmış peak'ler (UI pulse için)
 *  ```
 *
 * Neden ACF birincil? "Kaç peak gördüm" sorusu SCG'de yanıltıcı — tek kalp
 * atışı birden fazla güçlü mekanik event üretir (S1/S2), peak-counting bunları
 * ayrı beat sanıp **harmonik doubling** yapar (80 → 160 BPM). ACF ise
 * "bu kompleks patern ne sıklıkla tekrar ediyor?" sorusunu sorar — bu, gerçek
 * beat periyodudur. 160 BPM hatası bu mimaride fundamental-hipotez testiyle
 * yakalanır (high candidate'in yarısı güçlü fundamental ise onu seçer).
 *
 * **fs her kanal için ayrı** — Pixel 9'da ACCELEROMETER/GYROSCOPE ~199Hz ama
 * LINEAR_ACCELERATION ~50Hz çalışabilir. Aynı session-wide fs kullanmak filtre
 * frekans karşılığını bozar. [process] her kanalın fs'ini kendi timestamp'inden
 * ölçer; heartbeat kaynağı olarak LINEAR_ACCELERATION kullanılmaz.
 */
class SignalProcessor(
    /** Analiz penceresi uzunluğu (saniye) — uzun pencere daha stabil ACF. */
    private val windowSeconds: Float = 6f,
    /** Beklenen sample rate (Hz). ±%30 sapma tolere edilir. */
    private val expectedSampleRateHz: Float = 199f,
    // Band-pass.
    private val loHz: Float = 5f,
    private val hiHzMax: Float = 30f,
    // Beat aralığı (BPM).
    private val minBpm: Int = 40,
    private val maxBpm: Int = 180,
    /** Harmonik hipotez eşiği — bu BPM üstündeki candidate'lerin yarısı test edilir. */
    private val harmonicTestThreshold: Float = 110f,
    /** ACF candidate için min güç (normalize 0–1). 0.15 — borderline peak'leri eler. */
    private val minAcCandidateStrength: Float = 0.15f,
    /** Harmonik fundamental için min ACF gücü. 0.30 — zayıf fundamental'ı harmonik sanma. */
    private val minFundamentalStrength: Float = 0.30f,
    /** Min sample = fs * bu çarpan, en az 60. */
    private val minSamplesMultiplier: Int = 3,
    /** Beat lokalizasyonu için min peak interval (saniye). */
    private val minBeatIntervalSec: Float = 0.35f,
) {

    data class SignalResult(
        val bpm: Int,
        val ibisMs: List<Long>,
        val channel: SensorType,
        val confidence: Float,
        /** En son beat anı (event timestamp nanos) — UI pulse sync için. */
        val lastBeatNanos: Long?,
        /** Son beat zaman damgaları (UI pulse animasyonu için). */
        val recentBeatNanos: List<Long>,
        /** Tahminin nasıl üretildiği — debug için. */
        val verdict: String,
    )

    /**
     * Pencereyi işle ve BPM üret. Yeterli veri/düzen yoksa null (dürüst "yok").
     *
     * Birincil kanal: ACCELEROMETER.z. İkincil doğrulama: GYROSCOPE.x (aynı fs,
     * düşük gürültü). İkisi de işlenip uyuşma control edilir.
     */
    fun process(window: List<SensorSample>): SignalResult? {
        // --- Birincil kanal: ACC.z, kendi fs'iyle ---
        val accZ = window.filter { it.sensorType == SensorType.ACCELEROMETER }
            .sortedBy { it.timestampNanos }
        if (accZ.size < 2) return null
        val primary = estimate(accZ, accZ.map { it.z })
            ?: return null

        // --- İkincil doğrulama: GYROSCOPE.x (aynı fs, düşük gürültü) ---
        val gyroX = window.filter { it.sensorType == SensorType.GYROSCOPE }
            .sortedBy { it.timestampNanos }
        val secondary = if (gyroX.size >= 2) estimate(gyroX, gyroX.map { it.x }) else null

        // İki kanal varsa uyuşma ile confidence boost / çelişki kontrolü.
        // v2: confidence ACF gücüne map'lenir (birincil), iki-kanal cezası azaltıldı —
        // v1'de ACF 0.89 iken confidence 0.32 çıkıyordu (aşırı cezalandırma).
        val consensus: Triple<Float, Float, String> = if (secondary != null) {
            val dPrimary = primary.bpm.toFloat()
            val dSecondary = secondary.bpm.toFloat()
            val agree = abs(dPrimary - dSecondary) / max(dPrimary, dSecondary) < 0.12f
            when {
                agree -> {
                    // İki bağımsız kanal uyuşuyor — en sağlam. Confidence boost.
                    Triple((dPrimary + dSecondary) / 2f, min(primary.conf * 1.1f, 0.95f),
                        "${primary.verdict}+gyro-agree")
                }
                dSecondary in (dPrimary * 0.45f)..(dPrimary * 0.7f) -> {
                    // GYRO, ACC'nin ~yarısı → ACC'de doubling şüphesi; GYRO fundamental.
                    Triple(dSecondary, secondary.conf, "${primary.verdict}->gyro-fund")
                }
                dPrimary in (dSecondary * 0.45f)..(dSecondary * 0.7f) -> {
                    // ACC, GYRO'nun ~yarısı → GYRO'da doubling; ACC fundamental.
                    Triple(dPrimary, primary.conf, "${secondary.verdict}->acc-fund")
                }
                else -> {
                    // Çelişki — ama v2: güveni çok düşürme (0.7×→0.85×).
                    // Birincil (ACC) ACF gücü hâlâ ana sinyal; gyro sadece doğrulama.
                    Triple(dPrimary, primary.conf * 0.85f, "${primary.verdict}+gyro-disagree")
                }
            }
        } else {
            Triple(primary.bpm.toFloat(), primary.conf, primary.verdict)
        }
        val bpmFinal = consensus.first
        val confFinal = consensus.second
        val verdictFinal = consensus.third

        val bpmInt = (bpmFinal + 0.5f).toInt()
        if (bpmInt !in minBpm..maxBpm) return null

        // --- Beat lokalizasyonu: ACF periyoduyla doğrulanmış peak'ler (UI için) ---
        val beatNanos = primary.beatNanos

        val ibisMs = if (beatNanos.size >= 2) {
            beatNanos.zipWithNext { a, b -> b - a }
                .filter { it in (minBeatIntervalSec * 1e9).toLong()..(maxBeatIntervalSecFor(maxBpm) * 1e9).toLong() }
                .map { it / 1_000_000 }
        } else emptyList()

        return SignalResult(
            bpm = bpmInt,
            ibisMs = ibisMs,
            channel = SensorType.ACCELEROMETER,
            confidence = confFinal.coerceIn(0f, 1f),
            lastBeatNanos = beatNanos.lastOrNull(),
            recentBeatNanos = beatNanos.takeLast(8),
            verdict = verdictFinal,
        )
    }

    private fun maxBeatIntervalSecFor(maxBpm: Int): Float = 60f / minBpm

    /**
     * Tek kanal ACF-birincil tahmini: bandpass → envelope → ACF → harmonic
     * disambiguation. [beatNanos] ACF periyoduyla doğrulanmış envelope peak'leri.
     */
    private fun estimate(
        samples: List<SensorSample>,
        values: List<Float>,
    ): ChannelEstimate? {
        val ts = samples.map { it.timestampNanos }
        val x = values.toFloatArray()
        val fs = estimateFs(samples)
        if (fs < 40f) return null
        val minSamples = max((fs * minSamplesMultiplier).toInt(), 60)
        if (x.size < minSamples) return null

        // DC kaldır.
        val mean = x.fold(0f) { a, v -> a + v } / x.size
        val ac = FloatArray(x.size) { i -> x[i] - mean }

        // Band-pass — adaptif üst bant, Nyquist-safe.
        val hiHz = min(hiHzMax, fs * 0.40f)
        val effHi = if (hiHz <= loHz + 1f) loHz + 2f else hiHz
        val filtered = bandPass(ac, fs, loHz, effHi)

        // Envelope: rectify + smoothing (~0.08s).
        val smW = max((fs * 0.08f).toInt(), 1)
        val env = smooth(rectify(filtered), smW)
        if (!env.allFinite()) return null

        // ACF spektrumu (birincil) — candidate period'lar.
        val spec = acfSpectrum(env, fs) ?: return null
        val cands = acfCandidates(spec)

        // Harmonik disambiguation — proper hipotez testi.
        val disamb = harmonicDisambiguate(cands, fs)
        val (bpm, conf, verdict, usedLagSamples) = disamb ?: return null
        if (bpm < minBpm || bpm > maxBpm) return null

        // Beat lokalizasyonu: envelope peak'leri, ACF periyodu (usedLag) ile doğrula.
        // Peak'ler usedLag aralığında olmalı; doubling peak'lerini eler.
        val beatNanos = localizeBeats(env, ts, fs, usedLagSamples)

        return ChannelEstimate(
            bpm = (bpm + 0.5f).toInt(),
            conf = conf,
            verdict = verdict,
            beatNanos = beatNanos,
        )
    }

    // --- ACF birincil hesaplamalar ---

    /** Band-pass via cascade low-pass + high-pass biquad (Butterworth 2. derece each). */
    private fun bandPass(x: FloatArray, fs: Float, loHz: Float, hiHz: Float): FloatArray {
        val lp = biquadLowPass(x, fs, hiHz)
        val hp = biquadHighPass(lp, fs, loHz)
        return hp
    }

    private fun biquadLowPass(x: FloatArray, fs: Float, cutoffHz: Float): FloatArray {
        val cutoff = min(cutoffHz, fs * 0.45f)
        val w0 = (2 * PI * cutoff / fs).toFloat()
        val cosW = cos(w0); val sinW = sin(w0)
        val alpha = sinW / sqrt(2f)
        val b0 = (1 - cosW) / 2; val b1 = 1 - cosW; val b2 = b0
        val a0 = 1 + alpha; val a1 = -2 * cosW; val a2 = 1 - alpha
        return biquad(x, b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun biquadHighPass(x: FloatArray, fs: Float, cutoffHz: Float): FloatArray {
        val cutoff = min(cutoffHz, fs * 0.45f)
        val w0 = (2 * PI * cutoff / fs).toFloat()
        val cosW = cos(w0); val sinW = sin(w0)
        val alpha = sinW / sqrt(2f)
        val b0 = (1 + cosW) / 2; val b1 = -(1 + cosW); val b2 = b0
        val a0 = 1 + alpha; val a1 = -2 * cosW; val a2 = 1 - alpha
        return biquad(x, b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    /** Biquad direkt form I — NaN/Inf sanitize (düşük fs'te kararsızlığa karşı). */
    private fun biquad(x: FloatArray, b0: Float, b1: Float, b2: Float, a1: Float, a2: Float): FloatArray {
        val y = FloatArray(x.size)
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        for (i in x.indices) {
            val xn = x[i]
            var yn = b0 * xn + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            if (!yn.isFinite()) yn = 0f
            y[i] = yn
            x2 = x1; x1 = xn
            y2 = y1; y1 = yn
        }
        return y
    }

    private fun rectify(x: FloatArray): FloatArray = FloatArray(x.size) { i -> if (x[i] < 0) -x[i] else x[i] }

    private fun smooth(x: FloatArray, w: Int): FloatArray {
        if (w < 1) return x.copyOf()
        val out = FloatArray(x.size)
        var sum = 0f
        for (i in x.indices) {
            sum += x[i]
            if (i >= w) sum -= x[i - w]
            val n = minOf(i + 1, w)
            out[i] = sum / n
        }
        return out
    }

    /**
     * Normalize ACF, 40–180 bpm lag aralığında.
     * Returns (lag_samples_array, acf_values_array) veya null.
     */
    private fun acfSpectrum(env: FloatArray, fs: Float): AcfSpec? {
        val n = env.size
        val mean = env.fold(0f) { a, v -> a + v } / n
        val e = FloatArray(n) { i -> env[i] - mean }
        val lagLo = (fs * 60f / maxBpm).toInt().coerceAtLeast(1)
        val lagHi = (fs * 60f / minBpm).toInt().coerceAtMost(n - 1)
        if (lagHi <= lagLo) return null
        var ac0 = 0f
        for (v in e) ac0 += v * v
        if (ac0 <= 0f) return null
        val lags = IntArray(lagHi - lagLo + 1) { lagLo + it }
        val ac = FloatArray(lags.size)
        for (i in lags.indices) {
            val k = lags[i]
            var s = 0f
            for (j in 0 until n - k) s += e[j] * e[j + k]
            ac[i] = s / ac0
        }
        return AcfSpec(lags, ac)
    }

    /** ACF spektrumundan candidate period peak'leri (parabolik interp'li), güçe göre sıralı. */
    private fun acfCandidates(spec: AcfSpec): List<AcCandidate> {
        val lags = spec.lags; val ac = spec.ac
        if (ac.size < 3) return emptyList()
        val peaks = mutableListOf<Int>()
        for (i in 1 until ac.size - 1) {
            if (ac[i] >= minAcCandidateStrength && ac[i] >= ac[i - 1] && ac[i] > ac[i + 1]) {
                peaks.add(i)
            }
        }
        if (peaks.isEmpty()) {
            // Hiç local max yoksa en yüksek değeri al (monotonik sinyal).
            peaks.add((0 until ac.size).maxByOrNull { ac[it] }!!)
        }
        val cands = peaks.map { i ->
            // Parabolik interpolasyon — sub-sample lag.
            var lagF = lags[i].toFloat()
            var valF = ac[i]
            if (i in 1 until lags.size - 1) {
                val a = ac[i - 1]; val b = ac[i]; val c = ac[i + 1]
                val denom = a - 2 * b + c
                if (denom != 0f) {
                    val off = 0.5f * (a - c) / denom
                    lagF = lags[i] + off
                    valF = b - 0.25f * (a - c) * off
                }
            }
            AcCandidate(lagF, valF.coerceAtLeast(0f))
        }
        return cands.sortedByDescending { it.strength }
    }

    /**
     * Harmonik hipotez testi — candidate'lerden fundamental BPM üret.
     *
     * **Sıkılaştırılmış (v2):** high candidate (>110 bpm) SADECE zayıfsa böl.
     * High güçlü ve fundamental da güçlüyse YÜKSEK olanı seç — çünkü SCG'de
     * tek kalp atışı S1/S2 gibi birden fazla güçlü mekanik event üretir; 2×
     * peak gerçektir, her zaman harmonik hat değildir. Eski (v1) kural yarı güçlü
     * her candidate'i harmonik sanıp aşırı bölüyordu (93→46→51 cascade).
     *
     * [fs] sample rate — lag sample cinsinden, BPM = 60*fs/lag.
     *
     * Returns (bpm, confidence, verdict, usedLagSamples) veya null.
     */
    private fun harmonicDisambiguate(cands: List<AcCandidate>, fs: Float): DisambResult? {
        if (cands.isEmpty()) return null
        val best = cands.first()
        val bestLag = best.lag
        if (bestLag <= 0f) return null
        val bestBpm = 60f * fs / bestLag
        val bestStr = best.strength

        // Tüm candidate BPM'ler (lag sample → bpm = 60*fs/lag).
        val allBpms = cands.map { c ->
            Triple(60f * fs / c.lag, c.strength, c.lag)
        }.filter { it.first > 0f }

        // Harmonik hipotezi: high candidate'lerin yarısı güçlü fundamental mı?
        for ((b, s, l) in allBpms) {
            if (b > harmonicTestThreshold) {
                val half = b / 2f
                if (half in minBpm.toFloat()..harmonicTestThreshold) {
                    // half'a yakın candidate ara (güç eşiği yok — var mı diye bak).
                    val fundCand = allBpms.firstOrNull { (b2, _, _) ->
                        abs(b2 - half) / half < 0.10f
                    }
                    if (fundCand != null) {
                        val (_, fundStr, fundLag) = fundCand
                        // v2: high SADECE zayıfsa böl.
                        // high'ın gücü en güçlü candidate'den belirgin düşükse (0.55×)
                        // VE fundamental yeterince güçlüyse → high muhtemelen harmonik.
                        // High güçlüyse → 2× mekanik event gerçek beat, olduğu gibi bırak.
                        if (s < bestStr * 0.55f && fundStr > minFundamentalStrength) {
                            val conf = min(0.95f, fundStr + 0.05f)
                            return DisambResult(half, conf, "harmonic->fundamental", fundLag)
                        }
                    }
                }
            }
        }

        // Harmonik yok — en güçlü candidate (primary ACF).
        val conf = min(0.9f, bestStr)
        return DisambResult(bestBpm, conf, "primary-acf", bestLag)
    }

    /**
     * Beat lokalizasyonu: envelope peak'lerini bul, ama ACF periyodu (usedLag)
     * ile doğrula — doubling peak'lerini (usedLag/2 aralıkta olanları) eler.
     * Beat zamanlarını (nanos) döndürür.
     */
    private fun localizeBeats(env: FloatArray, ts: List<Long>, fs: Float, usedLagSamples: Float): List<Long> {
        if (env.size < 3 || ts.size != env.size) return emptyList()
        val threshold = env.mean() + env.std() * 0.5f
        val minDist = (usedLagSamples * 0.7f).toInt().coerceAtLeast((fs * minBeatIntervalSec).toInt())
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
        return peaks.map { ts[it] }
    }

    // --- Yardımcılar ---

    private fun estimateFs(samples: List<SensorSample>): Float {
        if (samples.size < 2) return expectedSampleRateHz
        val dt = (samples.last().timestampNanos - samples.first().timestampNanos).toFloat() / 1e9f
        return if (dt > 0f) (samples.size - 1) / dt else expectedSampleRateHz
    }

    private fun FloatArray.mean(): Float = if (isEmpty()) 0f else sum() / size
    private fun FloatArray.std(): Float {
        if (size == 0) return 0f
        val m = mean()
        var s = 0f
        for (v in this) s += (v - m) * (v - m)
        return sqrt(s / size)
    }
    private fun FloatArray.allFinite(): Boolean {
        for (v in this) if (!v.isFinite()) return false
        return true
    }

    // --- Dahili veri yapıları ---

    private data class ChannelEstimate(
        val bpm: Int,
        val conf: Float,
        val verdict: String,
        val beatNanos: List<Long>,
    )

    private data class AcfSpec(val lags: IntArray, val ac: FloatArray)

    private data class AcCandidate(val lag: Float, val strength: Float)

    private data class DisambResult(
        val bpm: Float,
        val confidence: Float,
        val verdict: String,
        val usedLagSamples: Float,
    )
}