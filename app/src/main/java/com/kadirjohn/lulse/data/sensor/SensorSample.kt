package com.kadirjohn.lulse.data.sensor

/**
 * Tek bir sensör ölçümü.
 *
 * @param sensorType Hangi sensörden geldiği (bkz. [SensorType]).
 * @param timestampNanos Olayın nanosecond cinsinden timestamp'i ([SensorEvent.timestamp]).
 * @param eventTimeMs Olayın epoch-millis cinsinden wall-clock zamanı (kayıt/dışa aktarım için).
 * @param x X ekseni değeri (m/s² veya rad/s).
 * @param y Y ekseni değeri.
 * @param z Z ekseni değeri.
 */
data class SensorSample(
    val sensorType: SensorType,
    val timestampNanos: Long,
    val eventTimeMs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
)

/**
 * Lulse'un dinlediği IMU sensörleri.
 * V1'de tek bir kaynağa güvenmemek için hepsi aynı anda toplanır.
 */
enum class SensorType(val androidType: Int) {
    ACCELEROMETER(android.hardware.Sensor.TYPE_ACCELEROMETER),
    LINEAR_ACCELERATION(android.hardware.Sensor.TYPE_LINEAR_ACCELERATION),
    GYROSCOPE(android.hardware.Sensor.TYPE_GYROSCOPE),
    ;

    companion object {
        fun fromAndroidType(type: Int): SensorType? = entries.firstOrNull { it.androidType == type }
    }
}