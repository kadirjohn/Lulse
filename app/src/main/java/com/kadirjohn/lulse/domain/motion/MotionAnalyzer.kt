package com.kadirjohn.lulse.domain.motion

import com.kadirjohn.lulse.data.sensor.SensorRingBuffer
import com.kadirjohn.lulse.data.sensor.SensorSample
import com.kadirjohn.lulse.data.sensor.SensorType

/**
 * Son sensör penceresinden bir [MotionScore] ve [MotionState] türetir (spec §9.A).
 *
 * Hareket enerjisini birkaç kaynaktan birleştirir:
 *  - accelerometer varyansı (baskın sinyal — büyük gövde hareketi)
 *  - gyroscope enerjisi (dönme/orientasyon değişimi)
 *  - jerk (ani ivmelenme/spike)
 *
 * Ek olarak **orientation gating**: telefon dik tutulursa (yerçekimi y ekseninde
 * baskınsa) ölçüm uygun değil — [classify] bu durumda HIGH_MOTION döndürür ki
 * UI "Yatar pozisyona geçin / Telefonu kalbinizin üzerine koyun" yönlendirmesinde
 * kalsın. Sadece hareket enerjisi düşük + telefon yatay ise STILL'e geçilir.
 *
 * Eşikler başlangıç değerleri; gerçek cihaz verisi toplandıktan sonra
 * (Faz 4) kalibre edilecektir. TODO (Faz 4): adaptif eşik.
 */
class MotionAnalyzer(
    /** Pencere uzunluğu (saniye). */
    private val windowSeconds: Float = 2f,
    // Eşikler — gerçek veriyle kalibre edilecek.
    private val stillTotal: Float = 0.35f,
    private val highMotionTotal: Float = 1.8f,
    private val settlingHysteresis: Float = 0.6f,
    // Orientation: yerçekiminin bir eksende ne kadar baskın olması gerekir (m/s²).
    private val gravityThreshold: Float = 7.0f,
    // Dik kabul için baskın eksenin min oranı (toplam yerçekimine göre).
    private val uprightAxisRatio: Float = 0.6f,
) {

    /**
     * Buffer'dan bir pencere çekip [MotionScore] hesaplar.
     * Accelerometer yoksa linear acceleration'a, o da yoksa gyroscope'a düşer.
     */
    fun analyze(buffer: SensorRingBuffer, nowNanos: Long): MotionScore {
        val accel = pickFirstAvailable(buffer, nowNanos, SensorType.ACCELEROMETER, SensorType.LINEAR_ACCELERATION)
        val gyro = buffer.lastSeconds(SensorType.GYROSCOPE, windowSeconds, nowNanos)

        val accelVariance = if (accel.isNotEmpty()) accelVariance(accel) else 0f
        val gyroEnergy = if (gyro.isNotEmpty()) gyroEnergy(gyro) else 0f
        val jerk = if (accel.size >= 2) jerkMagnitude(accel) else 0f

        // Orientation — sadece gerçek accelerometer (yerçekimi içerir) anlamlı.
        val orientation = if (accel.isNotEmpty() && accel.first().sensorType == SensorType.ACCELEROMETER) {
            orientationFromAccel(accel)
        } else Orientation.UNKNOWN

        val total = combine(accelVariance, gyroEnergy, jerk)
        return MotionScore(
            total = total,
            accelVariance = accelVariance,
            gyroEnergy = gyroEnergy,
            jerkMagnitude = jerk,
            samples = accel.size + gyro.size,
            phoneUpright = orientation == Orientation.UPRIGHT,
            orientation = orientation,
        )
    }

    /**
     * [MotionScore]'dan [MotionState]'e geçiş — histeresisli + orientation gating.
     * Mevcut state verilirse, eşik bandı içinde salınım önlenir.
     *
     * Telefon dik tutuluyorsa ([MotionScore.phoneUpright]) asla STILL'e geçmez —
     * kullanıcı yönlendirmede kalsın diye HIGH_MOTION döner.
     */
    fun classify(score: MotionScore, current: MotionState): MotionState {
        // Orientation gating: dik pozisyon ölçüme uygun değil -> yönlendir.
        if (score.phoneUpright) return MotionState.HIGH_MOTION

        val t = score.total
        // Yükseğe geçiş
        if (t >= highMotionTotal) return MotionState.HIGH_MOTION
        // Düşüğe (STILL) geçiş
        if (t <= stillTotal) return MotionState.STILL
        // Arada: mevcut state varsa orada kal (histeresis), yoksa SETTLING
        return when (current) {
            MotionState.HIGH_MOTION -> if (t <= highMotionTotal - settlingHysteresis) MotionState.SETTLING else MotionState.HIGH_MOTION
            MotionState.STILL -> if (t >= stillTotal + settlingHysteresis) MotionState.SETTLING else MotionState.STILL
            MotionState.SETTLING -> MotionState.SETTLING
        }
    }

    fun analyzeAndClassify(buffer: SensorRingBuffer, nowNanos: Long, current: MotionState): Pair<MotionScore, MotionState> {
        val score = analyze(buffer, nowNanos)
        val state = classify(score, current)
        return score to state
    }

    // --- Saf çekirdek hesaplamalar (test edilebilir) ---

    private fun pickFirstAvailable(
        buffer: SensorRingBuffer,
        nowNanos: Long,
        vararg types: SensorType,
    ): List<SensorSample> {
        for (t in types) {
            val w = buffer.lastSeconds(t, windowSeconds, nowNanos)
            if (w.isNotEmpty()) return w
        }
        return emptyList()
    }

    /** Üç eksen vektör büyüklüğünün pencere varyansı. */
    internal fun accelVariance(samples: List<SensorSample>): Float {
        val mags = FloatArray(samples.size) { i -> magnitude(samples[i]) }
        val mean = mags.average().toFloat()
        var sum = 0f
        for (m in mags) sum += (m - mean) * (m - mean)
        return sum / mags.size
    }

    /** Üç eksen enerjisinin toplamı (dönme yoğunluğu). */
    internal fun gyroEnergy(samples: List<SensorSample>): Float {
        var e = 0f
        for (s in samples) {
            val m = magnitude(s)
            e += m * m
        }
        return e / samples.size
    }

    /** Ardışık ivme büyüklükleri arasındaki ortalama |Δ|. */
    internal fun jerkMagnitude(samples: List<SensorSample>): Float {
        var sum = 0f
        var n = 0
        for (i in 1 until samples.size) {
            val dm = kotlin.math.abs(magnitude(samples[i]) - magnitude(samples[i - 1]))
            sum += dm
            n++
        }
        return if (n == 0) 0f else sum / n
    }

    /** Bileşenleri tek bir normalize skora birleştirir (ağırlıklar empirik). */
    internal fun combine(accelVar: Float, gyro: Float, jerk: Float): Float {
        // Hızlı kaba normalize: her bileşeni makul bir arala ölçekle.
        val a = (accelVar / 2.0f).coerceIn(0f, 10f)
        val g = (gyro / 0.05f).coerceIn(0f, 10f)
        val j = (jerk / 0.3f).coerceIn(0f, 10f)
        return (a * 0.5f + g * 0.3f + j * 0.2f)
    }

    private fun magnitude(s: SensorSample): Float =
        kotlin.math.sqrt(s.x * s.x + s.y * s.y + s.z * s.z)

    /**
     * Accelerometer penceresinden telefon duruşunu çıkarır.
     * Yerçekimi (~9.81 m/s²) baskın eksende toplanır:
     *  - z baskınsa -> yatay (LYING_FLAT): telefon göğüste yatıyor, ölçüme uygun.
     *  - y baskınsa -> dik (UPRIGHT): elinde/portre modunda, ölçüme uygun değil.
     *  - belirsiz -> UNKNOWN.
     *
     * Not: cihazın ekranı yukarı bakıyorsa z, dik portrede y baskındır (tipik
     * Android koordinat). Bazı cihazlar farklı olabilir; ama "tek bir eksen
     * yerçekimini taşıyorsa" duruş bellidir.
     */
    internal fun orientationFromAccel(samples: List<SensorSample>): Orientation {
        if (samples.isEmpty()) return Orientation.UNKNOWN
        var ax = 0f; var ay = 0f; var az = 0f
        for (s in samples) { ax += s.x; ay += s.y; az += s.z }
        ax /= samples.size; ay /= samples.size; az /= samples.size
        val absX = kotlin.math.abs(ax)
        val absY = kotlin.math.abs(ay)
        val absZ = kotlin.math.abs(az)
        val total = absX + absY + absZ
        if (total < gravityThreshold) return Orientation.UNKNOWN // yerçekimi belirgin değil
        val zRatio = absZ / total
        val yRatio = absY / total
        return when {
            zRatio >= uprightAxisRatio -> Orientation.LYING_FLAT
            yRatio >= uprightAxisRatio -> Orientation.UPRIGHT
            else -> Orientation.UNKNOWN
        }
    }
}