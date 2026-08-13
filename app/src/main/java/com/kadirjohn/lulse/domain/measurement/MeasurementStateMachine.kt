package com.kadirjohn.lulse.domain.measurement

import com.kadirjohn.lulse.domain.motion.MotionState
import com.kadirjohn.lulse.domain.signal.SignalProcessor

/**
 * [MotionState] + [SignalProcessor.SignalResult]'dan [MeasurementState] türeten
 * state machine (spec §10, Faz 5).
 *
 * Mantık:
 *  - HIGH_MOTION / SETTLING → WAITING_FOR_STILLNESS (pulse aranmaz).
 *  - STILL → SEARCHING_PULSE; SignalProcessor beat üretirse:
 *      * confidence yüksek → PULSE_DETECTED
 *      * confidence orta   → LOW_CONFIDENCE
 *  - STILL + [searchTimeoutMs] geçti + beat yok → NO_PULSE.
 *  - Pulse bulunduktan sonra hareket başlarsa tekrar WAITING'e düşer.
 *
 * Stateful; [update] her analiz tick'inde çağrılır.
 *
 * TODO (Faz 6): confidence score daha sofistike (motion stability, kanal uyumu).
 */
class MeasurementStateMachine(
    /** STILL olduktan sonra NO_PULSE'a düşmeden önce beklenecek süre. */
    private val searchTimeoutMs: Long = 12_000L,
    /** PULSE_DETECTED için min confidence eşiği. 0.55 — doğru BPM (conf 0.6) gösterildiğinde
     * "tespit edildi" densin. 0.75 çok yüksekti: 292-tick kayıtta 1 tane PULSE_DETECTED. */
    private val highConfidenceThreshold: Float = 0.55f,
) {

    var state: MeasurementState = MeasurementState.IDLE
        private set

    /** En son üretilen pulse sonucu (BPM, confidence, beat zamanları) — UI için. */
    var lastPulse: SignalProcessor.SignalResult? = null
        private set

    private var stableSinceMs: Long? = null

    /**
     * @param motionState Güncel hareket durumu.
     * @param pulse SignalProcessor çıktısı (STILL iken); null ise beat yok.
     * @param nowMs Şu anki wall-clock ms.
     * @return (yeni state, değişti mi)
     */
    fun update(
        motionState: MotionState,
        pulse: SignalProcessor.SignalResult?,
        nowMs: Long,
    ): Pair<MeasurementState, Boolean> {
        val prev = state
        when (motionState) {
            MotionState.HIGH_MOTION, MotionState.SETTLING -> {
                stableSinceMs = null
                lastPulse = null
                state = MeasurementState.WAITING_FOR_STILLNESS
            }
            MotionState.STILL -> {
                if (stableSinceMs == null) stableSinceMs = nowMs
                val stableFor = nowMs - (stableSinceMs ?: nowMs)
                state = when {
                    pulse != null -> {
                        lastPulse = pulse
                        if (pulse.confidence >= highConfidenceThreshold) {
                            MeasurementState.PULSE_DETECTED
                        } else {
                            MeasurementState.LOW_CONFIDENCE
                        }
                    }
                    stableFor >= searchTimeoutMs -> MeasurementState.NO_PULSE
                    else -> MeasurementState.SEARCHING_PULSE
                }
            }
        }
        return state to (state != prev)
    }

    fun reset() {
        state = MeasurementState.IDLE
        stableSinceMs = null
        lastPulse = null
    }
}