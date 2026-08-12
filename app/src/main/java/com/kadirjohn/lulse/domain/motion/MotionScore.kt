package com.kadirjohn.lulse.domain.motion

/**
 * Bir pencere için hesaplanan ham hareket metriği ve onu oluşturan bileşenler.
 *
 * [MotionAnalyzer] üretir; [com.kadirjohn.lulse.domain.measurement.MeasurementStateMachine]
 * ve UI/debug bunu kullanır. Bileşenler ayrı tutulur ki ileride confidence ve
 * hata ayıklama için parçalı bilgi de olsun.
 *
 * @param total 0+ normalize edilmemiş toplam hareket skoru.
 * @param accelVariance Accelerometer pencere varyansı (m/s²)².
 * @param gyroEnergy Gyroscope pencere enerjisi (rad²/s²).
 * @param jerkMagnitude Ortalama jerk (ivme değişimi) büyüklüğü.
 * @param samples Kullanılan sample sayısı (kalite/debug için).
 * @param phoneUpright Telefon dik mi tutuluyor (yanlış pozisyon — göğüste değil).
 *   true ise UI "Hazır" dememeli, kullanıcıyı göğsünün üzerine yatırmaya yönlendirmeli.
 * @param orientation Orientation sonucu (debug için).
 */
data class MotionScore(
    val total: Float,
    val accelVariance: Float,
    val gyroEnergy: Float,
    val jerkMagnitude: Float,
    val samples: Int,
    val phoneUpright: Boolean = false,
    val orientation: Orientation = Orientation.UNKNOWN,
) {
    companion object {
        val EMPTY = MotionScore(0f, 0f, 0f, 0f, 0)
    }
}

/**
 * Telefonun yerçekimine göre duruşu (accelerometer ortalama eksenlerinden).
 * Ölçüm için [LYING_FLAT] (göğüste yatay) gerekir.
 */
enum class Orientation {
    /** Telefon yatay, ekran yukarı/yana — göğüste doğru pozisyon. */
    LYING_FLAT,

    /** Telefon dik tutuluyor — ölçüm için yanlış. */
    UPRIGHT,

    /** Belirsiz / arada — yönlendirme gerekir. */
    UNKNOWN,
}