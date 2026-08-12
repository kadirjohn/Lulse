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
 */
data class MotionScore(
    val total: Float,
    val accelVariance: Float,
    val gyroEnergy: Float,
    val jerkMagnitude: Float,
    val samples: Int,
) {
    companion object {
        val EMPTY = MotionScore(0f, 0f, 0f, 0f, 0)
    }
}