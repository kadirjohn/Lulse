package com.kadirjohn.lulse.shared

/**
 * Telefon ↔ Watch clock sync tek bir ping/pong değişimi (docs 03).
 *
 * NTP-style:
 *  - t0: telefon ping gönderir
 *  - t1: watch ping alır
 *  - t2: watch pong gönderir
 *  - t3: telefon pong alır
 *
 * Offset (watch zamanını phone zamanına maplemek):
 *   watchMinusPhoneOffset = ((t1 - t0) + (t2 - t3)) / 2
 * RTT:
 *   rtt = (t3 - t0) - (t2 - t1)
 *
 * Tüm değerler monotonic elapsed nanos. Ham t0/t1/t2/t3 ASLA atılmaz —
 * CSV'ye [accepted] true/false ile birlikte yazılır (outlier'lar dahil).
 *
 * Sign convention testi: bkz [com.kadirjohn.lulse.ClockSyncTest].
 */
data class ClockSyncFrame(
    val sessionId: String,
    val syncId: Long,
    val t0PhoneNanos: Long,
    val t1WatchNanos: Long,
    val t2WatchNanos: Long,
    val t3PhoneNanos: Long,
    val rttNanos: Long,
    val watchMinusPhoneOffsetNanos: Long,
    val accepted: Boolean,
    val reason: String?,
)

/**
 * Clock sync hesaplamaları — pure, test edilebilir (docs 03).
 * Telefon ve watch aynı formülü kullanmalı; watch tarafı sadece t1/t2'yi ekler.
 */
object ClockSync {
    /** RTT (round-trip time) nanos: (t3 - t0) - (t2 - t1). */
    fun rttNanos(t0: Long, t1: Long, t2: Long, t3: Long): Long =
        (t3 - t0) - (t2 - t1)

    /** Watch→Phone offset nanos: ((t1 - t0) + (t2 - t3)) / 2.
     *  Watch zamanını phone zamanına maplemek için: phoneTime = watchTime - offset. */
    fun offsetNanos(t0: Long, t1: Long, t2: Long, t3: Long): Long =
        ((t1 - t0) + (t2 - t3)) / 2

    /** Watch elapsed nanos'u phone elapsed nanos'ya maple: phoneTime = watchTime - offset. */
    fun mapWatchToPhone(watchNanos: Long, watchMinusPhoneOffsetNanos: Long): Long =
        watchNanos - watchMinusPhoneOffsetNanos

    /** Bir sync değişiminiClockSyncFrame'e dönüştür (accepted + reason outlier filtresi ile). */
    fun toFrame(
        sessionId: String,
        syncId: Long,
        t0: Long, t1: Long, t2: Long, t3: Long,
        rttThresholdNanos: Long = 300_000_000L, // 300ms üstü outlier
    ): ClockSyncFrame {
        val rtt = rttNanos(t0, t1, t2, t3)
        val offset = offsetNanos(t0, t1, t2, t3)
        val accepted = rtt in 0..rttThresholdNanos
        val reason = when {
            rtt < 0 -> "negative_rtt"
            rtt > rttThresholdNanos -> "high_rtt"
            else -> null
        }
        return ClockSyncFrame(sessionId, syncId, t0, t1, t2, t3, rtt, offset, accepted, reason)
    }
}