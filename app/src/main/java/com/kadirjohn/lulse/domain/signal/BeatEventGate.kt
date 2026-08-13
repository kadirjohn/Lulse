package com.kadirjohn.lulse.domain.signal

import kotlin.math.abs

/**
 * Accepted-beat katmanı — SignalProcessor peak'lerini UI flash için doğrular (diğer AI önerisi).
 *
 * Sorun: SignalProcessor her ~200ms'de envelope peak'lerini yeniden hesaplar. Aynı cardiac
 * cycle'da S1/S2 peak'leri farklı tick'lerde son peak olabilir → `lastBeatNanos` değişir →
 * aynı fizyolojik beat için 2-3 flash. SCG'de bir cycle'da birden fazla mekanik event var.
 *
 * BeatEventGate, LOCKED BPM'ten türetilen **refractory period** ile dedup yapar:
 *  - candidate ≤ last accepted → duplicate reject
 *  - candidate çok yakın (< refractory) → same-cycle reject
 *  - candidate expected period'e yakın → accept, beatEventId++
 *
 * Sadece LOCKED'ta aktif — ACQUIRING/SEARCHING'de beat yok. UI `beatEventId` değişince
 * tek flash atar; aynı peak tekrar bulunursa event ID artmaz → flash yok.
 *
 * Refractory: expected period'ın ~%55'i (locked BPM'ten dinamik). Veriyle test edilebilir.
 */
class BeatEventGate(
    /** Refractory oranı — expected period'ın yüzdesi. 0.55 = period'ın %55'i. */
    private val refractoryPct: Float = 0.55f,
    /** Min refractory (ms) — çok düşük BPM'de aşırı kısa olmasın. */
    private val minRefractoryMs: Long = 250L,
) {
    /** Son kabul edilen beat zamanı (nanos). */
    private var lastAcceptedNanos: Long = 0L
    /** Beat event ID — her accepted beat'te artar. UI bu ID'yi izler. */
    private var beatEventId: Long = 0L
    /** Son rejection sebebi (debug/CSV). */
    private var lastRejectionReason: String = ""

    data class GateResult(
        val accepted: Boolean,
        val beatEventId: Long,
        val acceptedNanos: Long?,
        val rejectionReason: String,
    )

    /**
     * Candidate beat'i değerlendir. [candidateNanos] aday beat zamanı, [lockedBpm]
     * mevcut locked BPM (refractory hesabı için). null ise (LOCKED değil) reject.
     */
    fun evaluate(candidateNanos: Long?, lockedBpm: Int?): GateResult {
        if (candidateNanos == null || lockedBpm == null || lockedBpm <= 0) {
            lastRejectionReason = "no_lock"
            return GateResult(false, beatEventId, null, lastRejectionReason)
        }

        // Duplicate: aynı veya daha eski beat.
        if (candidateNanos <= lastAcceptedNanos) {
            lastRejectionReason = "duplicate"
            return GateResult(false, beatEventId, null, lastRejectionReason)
        }

        // Refractory: son accepted'ten çok yakın mı?
        val elapsedMs = (candidateNanos - lastAcceptedNanos) / 1_000_000
        val expectedPeriodMs = 60_000.0 / lockedBpm
        val refractoryMs = maxOf((expectedPeriodMs * refractoryPct).toLong(), minRefractoryMs)
        if (elapsedMs < refractoryMs) {
            lastRejectionReason = "refractory_${elapsedMs}ms<${refractoryMs}ms"
            return GateResult(false, beatEventId, null, lastRejectionReason)
        }

        // Accept — yeni beat event.
        lastAcceptedNanos = candidateNanos
        beatEventId++
        lastRejectionReason = ""
        return GateResult(true, beatEventId, candidateNanos, lastRejectionReason)
    }

    /** Son accepted beat event ID — UI LaunchedEffect(beatEventId) ile flash atar. */
    fun currentBeatEventId(): Long = beatEventId

    /** Reset — yeni recording/session. */
    fun reset() {
        lastAcceptedNanos = 0L
        beatEventId = 0L
        lastRejectionReason = ""
    }
}