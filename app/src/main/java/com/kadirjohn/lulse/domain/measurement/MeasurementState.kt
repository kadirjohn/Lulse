package com.kadirjohn.lulse.domain.measurement

/**
 * Ölçüm/pulse-arama durumu (spec §10 / first_prompt.md Phase 2).
 *
 * [MotionState][com.kadirjohn.lulse.domain.motion.MotionState] ile birlikte
 * UI'nın tek ekranını şekillendirir.
 *
 * V1'de [PULSE_DETECTED] ve [LOW_CONFIDENCE] gerçek sinyal işleme
 * (Faz 5) olmadan aktif değildir; yapı bu state'leri taşımaya hazırdır.
 */
enum class MeasurementState {
    /** Başlangıç / sensörler devrede değil. */
    IDLE,

    /** Hareket fazla — sabitlenmesi bekleniyor. */
    WAITING_FOR_STILLNESS,

    /** Telefon sabit — pulse aranıyor (henüz sonuç yok). */
    SEARCHING_PULSE,

    /** Güvenilir pulse bulundu — BPM göster. (V1: placeholder) */
    PULSE_DETECTED,

    /** Sabit ama yeterli süre içinde pattern çıkmadı. */
    NO_PULSE,

    /** Pulse var ama güven düşük — BPM küçük etiketle göster. (V1: placeholder) */
    LOW_CONFIDENCE,
}