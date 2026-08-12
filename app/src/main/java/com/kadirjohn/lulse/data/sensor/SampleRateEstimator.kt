package com.kadirjohn.lulse.data.sensor

/**
 * Bir sensör için gerçek (gözlemlenen) sample rate tahmini.
 *
 * Cihazlar nominal game/fast sampling modunu farklı hızlarda karşılar; gerçek Hz,
 * ardışık event timestamp'leri arasındaki ortalama aralığın tersidir.
 * Thread-safe değildir — consumer coroutine içinden güncellenir.
 */
class SampleRateEstimator {

    private var lastNanos: Long = 0L
    private var intervalsNanos: Long = 0L
    private var count: Int = 0
    private var minIntervalNanos: Long = Long.MAX_VALUE
    private var maxIntervalNanos: Long = 0L

    /** Yeni bir event geldiğinde çağrılır. */
    fun update(timestampNanos: Long) {
        if (lastNanos != 0L) {
            val dt = timestampNanos - lastNanos
            if (dt in 1..5_000_000_000L) { // makul aralık (1ns–5sn), sıçrama/overflow filtrele
                intervalsNanos += dt
                count++
                if (dt < minIntervalNanos) minIntervalNanos = dt
                if (dt > maxIntervalNanos) maxIntervalNanos = dt
            }
        }
        lastNanos = timestampNanos
    }

    /** Tahmini Hz; yeterli veri yoksa 0. */
    fun hz(): Float =
        if (count == 0) 0f
        else 1_000_000_000f * count / intervalsNanos

    /** Ortalama eventler-arası ms. */
    fun avgIntervalMs(): Float = if (count == 0) 0f else intervalsNanos.toFloat() / count / 1_000_000f

    fun minIntervalMs(): Float = if (count == 0) 0f else minIntervalNanos / 1_000_000f
    fun maxIntervalMs(): Float = if (count == 0) 0f else maxIntervalNanos / 1_000_000f
    fun sampleCount(): Int = count

    fun reset() {
        lastNanos = 0L
        intervalsNanos = 0L
        count = 0
        minIntervalNanos = Long.MAX_VALUE
        maxIntervalNanos = 0L
    }
}