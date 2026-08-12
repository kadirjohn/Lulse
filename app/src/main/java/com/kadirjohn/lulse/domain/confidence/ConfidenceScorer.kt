package com.kadirjohn.lulse.domain.confidence

/**
 * Ölçüm güven skoru (spec §9.G, §6.F).
 *
 * V1'de **implement edilmedi** — sadece iskelet ve TODO marker.
 * Faz 6'da doldurulacak.
 *
 * Planlanan girdiler:
 *  - motion stability (son pencere hareket skoru)
 *  - beat interval consistency (IBI varyasyonu)
 *  - channel agreement (accel vs gyro beat uyumu)
 *  - signal-to-noise estimate
 *  - envelope clarity
 *
 * Çıktı: [Confidence] (yüksek/orta/düşük kategorisi + ham 0..1 skor).
 * UI, düşük güvende "Ölçüm kararsız" gösterip kullanıcıyı yönlendirecek.
 *
 * TODO (Faz 6): implement et.
 */
class ConfidenceScorer {

    enum class Level { HIGH, MEDIUM, LOW }

    data class Confidence(val score: Float, val level: Level)

    /**
     * @suppress V1 stub — her zaman null döner.
     * Gerçek implementasyon Faz 6'da.
     */
    fun score(/* girdiler Faz 6'da tanımlanacak */): Confidence? = null
}