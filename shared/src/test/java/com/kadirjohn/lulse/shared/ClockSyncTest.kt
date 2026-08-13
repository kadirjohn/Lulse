package com.kadirjohn.lulse.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clock sync formüllerinin sign convention doğrulaması (docs 03: "Unit-test the
 * sign convention"). Telefon ve watch farklı clock'lara sahip — offset ile watch
 * zamanı phone zamanına maplenmeli. Bu test bilinen senaryolarda offset/RTT ve
 * maplemenin doğru yönü verdiğini kanıtlar.
 */
class ClockSyncTest {

    @Test
    fun zeroOffset_symmetricPath_returnsZeroOffset() {
        // Simetrik tek yönlü gecikme 50ms, offset 0.
        // t0=0 (phone gönder), t1=50ms (watch al), t2=50ms (watch anında gönder), t3=100ms (phone al)
        val t0 = 0L; val t1 = 50_000_000L; val t2 = 50_000_000L; val t3 = 100_000_000L
        val offset = ClockSync.offsetNanos(t0, t1, t2, t3)
        val rtt = ClockSync.rttNanos(t0, t1, t2, t3)
        assertEquals("simetrik yolda offset 0 olmalı", 0L, offset)
        assertEquals("RTT 100ms olmalı", 100_000_000L, rtt)
    }

    @Test
    fun watchAhead_positiveOffset_mapsWatchForward() {
        // Watch saati phone'dan 200ms ileride. Tek yönlü 50ms.
        // t0=0, t1=50+200=250 (watch ileride), t2=250, t3=100 (phone al)
        val t0 = 0L; val t1 = 250_000_000L; val t2 = 250_000_000L; val t3 = 100_000_000L
        val offset = ClockSync.offsetNanos(t0, t1, t2, t3)
        assertEquals("watch 200ms ileriyse offset +200ms olmalı", 200_000_000L, offset)
        // mapleme: watch=250ms → phone = 250 - 200 = 50ms (watch'ın alım anı phone'da)
        val mapped = ClockSync.mapWatchToPhone(t1, offset)
        assertEquals("watch 250ms → phone 50ms", 50_000_000L, mapped)
    }

    @Test
    fun watchBehind_negativeOffset_mapsWatchBackward() {
        // Watch saati phone'dan 150ms geride. Tek yönlü 50ms.
        // t0=0, t1=50-150=-100 (watch geride), t2=-100, t3=100
        val t0 = 0L; val t1 = -100_000_000L; val t2 = -100_000_000L; val t3 = 100_000_000L
        val offset = ClockSync.offsetNanos(t0, t1, t2, t3)
        assertEquals("watch 150ms geriyse offset -150ms olmalı", -150_000_000L, offset)
        val mapped = ClockSync.mapWatchToPhone(t1, offset)
        assertEquals("watch -100ms → phone 50ms", 50_000_000L, mapped)
    }

    @Test
    fun rtt_excludesWatchProcessingTime() {
        // Watch 30ms düşünür sonra pong gönderir. Tek yönlü 50ms.
        // t0=0, t1=50, t2=80, t3=130
        val t0 = 0L; val t1 = 50_000_000L; val t2 = 80_000_000L; val t3 = 130_000_000L
        val rtt = ClockSync.rttNanos(t0, t1, t2, t3)
        // RTT = (130-0) - (80-50) = 130 - 30 = 100ms (iki yönlü yol, processing çıkar)
        assertEquals("RTT processing'i çıkarmalı", 100_000_000L, rtt)
    }

    @Test
    fun toFrame_acceptsLowRtt_rejectsHighRtt() {
        val sessionId = "test"
        // düşük RTT — kabul
        val good = ClockSync.toFrame(sessionId, 1L, 0L, 50_000_000L, 50_000_000L, 100_000_000L)
        assertTrue("100ms RTT kabul edilmeli", good.accepted)
        // yüksek RTT — reddet
        val bad = ClockSync.toFrame(sessionId, 2L, 0L, 50_000_000L, 50_000_000L, 500_000_000L)
        assertFalse("500ms RTT outlier olarak reddedilmeli", bad.accepted)
        assertEquals("high_rtt reason", "high_rtt", bad.reason)
        // negatif RTT — reddet (clock hatası)
        val neg = ClockSync.toFrame(sessionId, 3L, 100L, 50L, 50L, 60L)
        assertFalse("negatif RTT reddedilmeli", neg.accepted)
        assertEquals("negative_rtt", "negative_rtt", neg.reason)
    }

    @Test
    fun roundTrip_offsetAndRtt_consistentAcrossSymmetricDelay() {
        // Farklı tek yönlü gecikmelerde offset simetrik kaldıkça 0 kalmalı
        for (delayMs in 20..60 step 10) {
            val d = delayMs * 1_000_000L
            val offset = ClockSync.offsetNanos(0L, d, d, 2 * d)
            assertEquals("simetrik $delayMs ms'de offset 0", 0L, offset)
        }
    }
}