package com.kadirjohn.lulse.domain.motion

/**
 * Telefonun hareket durumu (spec §10 / first_prompt.md Phase 2).
 *
 * UI doğrudan bu state'e göre arka plan ve yönlendirme metnini seçer.
 */
enum class MotionState {
    /** Çok fazla hareket — ölçüm yapılmaz, kullanıcı yönlendirilir. */
    HIGH_MOTION,

    /** Hareket azalıyor — kırmızıdan siyaha geçiş. */
    SETTLING,

    /** Telefon yeterince sabit — ölçüm/arama moduna geçilebilir. */
    STILL,
}