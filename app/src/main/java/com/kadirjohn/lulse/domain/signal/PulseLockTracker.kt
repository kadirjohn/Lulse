package com.kadirjohn.lulse.domain.signal

import kotlin.math.abs

/**
 * SEARCHING → ACQUIRING → LOCKED state machine (kullanıcı üç katmanlı mimari önerisi).
 *
 * Cold-start harmonic lock problemini çözer: algoritma başlangıçta yanlış harmoniğe
 * (örn 160 veya 48) kilitleniyordu. Bu tracker naif temporal persistence yerine
 * aşamalı bir yaklaşım kullanır:
 *
 *  - **SEARCHING**: veri yok / motion → UI "Nabız aranıyor", BPM gösterme.
 *  - **ACQUIRING**: candidate var ama hemen kilitlenme. Son N tick hypothesis
 *    history'sini cluster'la (±%10); en sık cluster + yeterli ACF gücü → lock adayı.
 *    ACQUIRING sırasında harmonic ambiguity: raw'ın 2×'ı da güçlüyse raw muhtemelen
 *    0.5× harmonic, gerçek fundamental 2× raw → 2×'ı lock al (halving çözümü).
 *  - **LOCKED**: locked BPM göster. Temporal hysteresis devrede:
 *    - candidate locked±%12 içinde → kabul (hafif smoothing).
 *    - candidate ≈ locked×2 → "2× harmonic" reddi, locked koru.
 *    - candidate ≈ locked×0.5 → "0.5× harmonic" reddi, locked koru.
 *    - uzak candidate 3 ardışık tick → ACQUIRING'e düş (signal drift).
 *
 * "160 BPM göstermektense birkaç saniye 'Nabız aranıyor' demek daha iyi."
 * Lock debug alanları (lockState, lockedBpm, lockAge, switchReason) CSV'ye yazılır.
 */
class PulseLockTracker(
    /** ACQUIRING'de history uzunluğu (tick sayısı). */
    private val historyN: Int = 5,
    /** Cluster eşleştirme eşiği (oran, ±). */
    private val clusterPct: Float = 0.10f,
    /** LOCKED kabul bandı (oran, locked±). */
    private val lockPct: Float = 0.12f,
    /** Harmonik reddi eşiği (oran, 2×/0.5× için). */
    private val harmonicPct: Float = 0.15f,
    /** Harmonik alternatif güç eşiği (oran, raw'a göre). */
    private val harmonicAltStrengthPct: Float = 0.7f,
    /** ACF gücü lock için min. */
    private val minLockStrength: Float = 0.20f,
    /** ACQUIRING cluster tutarlılık oranı (history'nin). */
    private val clusterConsensusPct: Float = 0.60f,
    /** LOCKED outlier → ACQUIRING eşiği. */
    private val outlierLimit: Int = 3,
) {

    enum class LockState { SEARCHING, ACQUIRING, LOCKED }

    data class Hypotheses(
        val rawBpm: Float,
        val halfBpm: Float?,
        val doubleBpm: Float?,
        val rawStrength: Float,
        val halfStrength: Float,
        val doubleStrength: Float,
        val confidence: Float,
    )

    data class LockResult(
        val state: LockState,
        /** UI'da gösterilecek BPM — SEARCHING/ACQUIRING'de null. */
        val bpm: Int?,
        val lockedBpm: Int?,
        val lockAgeTicks: Int,
        val switchReason: String,
        val rawCandidateBpm: Int?,
        val selectedHypothesis: String,
    )

    private var state = LockState.SEARCHING
    private var lockedBpm: Float? = null
    private val history = ArrayDeque<Hypotheses>()
    private var lockAge = 0
    private var switchReason = ""
    private var outlierCount = 0

    /** Her tick'te çağrılır. hyp null ise (veri yok/motion) → SEARCHING. */
    fun update(hyp: Hypotheses?): LockResult {
        switchReason = ""
        if (hyp == null) {
            if (state != LockState.SEARCHING) switchReason = "no_signal"
            state = LockState.SEARCHING
            history.clear()
            lockedBpm = null
            lockAge = 0
            return result(null, "none")
        }

        // History'ye ekle (cap historyN).
        if (history.size >= historyN) history.removeFirst()
        history.addLast(hyp)

        if (state == LockState.SEARCHING) {
            state = LockState.ACQUIRING
            switchReason = "candidate_appeared"
        }

        if (state == LockState.ACQUIRING) {
            if (history.size >= historyN) {
                val raws = history.map { it.rawBpm }
                val med = median(raws)
                val cluster = raws.count { abs(it - med) / med < clusterPct }
                if (cluster >= historyN * clusterConsensusPct) {
                    val clusterHyps = history.filter { abs(it.rawBpm - med) / med < clusterPct }
                    val meanStr = clusterHyps.map { it.rawStrength }.average().toFloat()
                    // Harmonic ambiguity: raw'ın 2×'ı güçlü mü? (halving çözümü)
                    val last = history.last()
                    val double2x = last.rawBpm * 2f
                    val double2xStr = if (last.doubleBpm != null && abs(last.doubleBpm - double2x) / double2x < 0.1f)
                        last.doubleStrength else 0f
                    if (double2x <= 180f && double2xStr > meanStr * harmonicAltStrengthPct) {
                        // 2× daha güçlü → raw 0.5× harmonic, fundamental 2× raw.
                        lockedBpm = double2x
                        state = LockState.LOCKED
                        lockAge = 0
                        switchReason = "locked_${double2x.toInt()}_from_halving"
                    } else if (meanStr > minLockStrength) {
                        lockedBpm = med
                        state = LockState.LOCKED
                        lockAge = 0
                        switchReason = "locked_${med.toInt()}"
                    } else {
                        switchReason = "low_acf"
                    }
                } else {
                    switchReason = "no_cluster"
                }
            }
            return result(if (state == LockState.LOCKED) lockedBpm else null, "raw")
        }

        // LOCKED
        if (state == LockState.LOCKED) {
            lockAge++
            val locked = lockedBpm ?: return result(null, "raw")
            val raw = hyp.rawBpm
            // locked±band içinde → kabul + smoothing.
            if (abs(raw - locked) / locked < lockPct) {
                lockedBpm = 0.8f * locked + 0.2f * raw
                outlierCount = 0
                return result(lockedBpm, "raw")
            }
            // 2× harmonic?
            if (abs(raw - 2f * locked) / (2f * locked) < harmonicPct) {
                switchReason = "2x_harmonic_rejected"
                return result(locked, "raw")
            }
            // 0.5× harmonic?
            if (abs(raw - 0.5f * locked) / (0.5f * locked) < harmonicPct) {
                switchReason = "0.5x_harmonic_rejected"
                return result(locked, "raw")
            }
            // Uzak candidate — outlier sayacı.
            outlierCount++
            if (outlierCount >= outlierLimit) {
                state = LockState.ACQUIRING
                lockedBpm = null
                history.clear()
                outlierCount = 0
                switchReason = "signal_drift"
            } else {
                switchReason = "outlier_$outlierCount"
            }
            return result(locked, "raw")
        }

        return result(null, "raw")
    }

    private fun result(bpm: Float?, selected: String): LockResult {
        val rawCand = history.lastOrNull()?.rawBpm?.toInt()
        return LockResult(
            state = state,
            bpm = bpm?.let { (it + 0.5f).toInt() },
            lockedBpm = lockedBpm?.let { (it + 0.5f).toInt() },
            lockAgeTicks = lockAge,
            switchReason = switchReason,
            rawCandidateBpm = rawCand,
            selectedHypothesis = selected,
        )
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val s = values.sorted()
        return s[s.size / 2]
    }

    fun reset() {
        state = LockState.SEARCHING
        lockedBpm = null
        history.clear()
        lockAge = 0
        outlierCount = 0
    }
}