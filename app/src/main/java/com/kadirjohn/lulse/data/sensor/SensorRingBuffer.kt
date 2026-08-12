package com.kadirjohn.lulse.data.sensor

import java.util.ArrayDeque

/**
 * Belirli bir süre boyunca en son [SensorSample]'ları tutan kapasitesi sınırlı ring buffer.
 *
 * UI/analiz için "son N saniye" penceresi sağlar; sınırsız büyümez.
 * Thread-safe değildir — tek bir consumer coroutine içinden kullanılmalıdır
 * (ör. [SensorRepository]'nin toplayıcı coroutine'i).
 *
 * @param maxSamples Kapasite (ör. 5 sn × ~200 Hz × 3 sensör için ~3000).
 */
class SensorRingBuffer(private val maxSamples: Int = 4_000) {

    private val deque: ArrayDeque<SensorSample> = ArrayDeque(maxSamples)
    private var dropped: Int = 0

    /** Yeni sample ekle; kapasite aşılırsa en eskisini düşür. */
    fun add(sample: SensorSample) {
        if (deque.size >= maxSamples) {
            deque.pollFirst()
            dropped++
        }
        deque.addLast(sample)
    }

    /** Buffer'daki tüm sample'ların (eklenme sırasına göre) bir kopyası. */
    fun snapshot(): List<SensorSample> = deque.toList()

    /**
     * Belirli bir [SensorType] için, [fromNanos] anından beri gelen sample'lar.
     * Pencere tabanlı analiz (motion score) için kullanılır.
     */
    fun window(sensorType: SensorType, fromNanos: Long): List<SensorSample> =
        deque.asSequence()
            .filter { it.sensorType == sensorType && it.timestampNanos >= fromNanos }
            .toList()

    /** Belirli bir sensör tipi için son [seconds] saniyedeki sample'lar. */
    fun lastSeconds(sensorType: SensorType, seconds: Float, nowNanos: Long): List<SensorSample> {
        val from = nowNanos - (seconds * 1_000_000_000L).toLong()
        return window(sensorType, from)
    }

    val size: Int get() = deque.size

    /** Şimdiye kadar kapasite yüzünden düşürülen sample sayısı (debug). */
    fun droppedCount(): Int = dropped

    fun clear() {
        deque.clear()
        dropped = 0
    }
}