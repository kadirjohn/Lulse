package com.kadirjohn.lulse.domain.measurement

import com.kadirjohn.lulse.domain.motion.MotionState

/**
 * [MotionState] + zaman bilgisinden [MeasurementState] türeten state machine (spec §10).
 *
 * Mantık (V1, mock kapalı):
 *  - HIGH_MOTION → WAITING_FOR_STILLNESS
 *  - SETTLING    → WAITING_FOR_STILLNESS
 *  - STILL       → SEARCHING_PULSE; [searchTimeoutMs] içinde gerçek algılama olmadığı için
 *                  NO_PULSE (gerçek DSP Faz 5'te gelecek; o zamana kadar dürüst davranır).
 *
 * TODO (Faz 5): STILL + geçerli beat pattern → PULSE_DETECTED / LOW_CONFIDENCE.
 * TODO (Faz 6): confidence score'a göre LOW_CONFIDENCE / PULSE_DETECTED ayrımı.
 *
 * Stateful; [update] her analiz tick'inde çağrılır. Zaman, [elapsedMsSinceStable]
 * parametresiyle dışarıdan verilir (test edilebilirlik için clock enjekte edilir).
 */
class MeasurementStateMachine(
    /** STILL olduktan sonra NO_PULSE'a düşmeden önce beklenecek süre. */
    private val searchTimeoutMs: Long = 12_000L,
) {

    var state: MeasurementState = MeasurementState.IDLE
        private set

    private var stableSinceMs: Long? = null

    /**
     * @param motionState Güncel hareket durumu.
     * @param nowMs Şu anki wall-clock ms.
     * @return (yeni state, değişti mi)
     */
    fun update(motionState: MotionState, nowMs: Long): Pair<MeasurementState, Boolean> {
        val prev = state
        when (motionState) {
            MotionState.HIGH_MOTION, MotionState.SETTLING -> {
                stableSinceMs = null
                state = MeasurementState.WAITING_FOR_STILLNESS
            }
            MotionState.STILL -> {
                if (stableSinceMs == null) stableSinceMs = nowMs
                val stableFor = nowMs - (stableSinceMs ?: nowMs)
                state = when {
                    // Faz 5 gelene kadar gerçek pulse yok: sabitken arar, timeout'ta dürüstçe "yok" der.
                    stableFor >= searchTimeoutMs -> MeasurementState.NO_PULSE
                    else -> MeasurementState.SEARCHING_PULSE
                }
            }
        }
        return state to (state != prev)
    }

    /**
     * TODO (Faz 5): gerçek [com.kadirjohn.lulse.domain.signal.SignalProcessor]
     * çıktısı (BPM + confidence) burada işlenip PULSE_DETECTED / LOW_CONFIDENCE
     * üretilecek. Şimdilik stub.
     */
    fun reportPulse(bpm: Int, confidence: Float) {
        // Placeholder — Faz 5'te doldurulacak.
    }

    fun reset() {
        state = MeasurementState.IDLE
        stableSinceMs = null
    }
}