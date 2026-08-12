package com.kadirjohn.lulse.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update

/**
 * IMU sensörlerine tek erişim noktası.
 *
 * Üç sensörü ([SensorType]) aynı anda dinler ve her event'i hafif bir callback
 * üzerinden bir [Flow]'a yayar. Ağır iş (analiz, kayıt) bu flow'u tüketen
 * coroutine'lerde yapılır; callback sadece sample'ı paketler.
 *
 * Her sensör için mevcudiyet ve gözlemlenen sample-rate bilgisini [status]
 * üzerinden expose eder; UI/debug bunu okur.
 */
class SensorRepository(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val sensors: Map<SensorType, Sensor?> = SensorType.entries.associateWith {
        sensorManager.getDefaultSensor(it.androidType)
    }

    private val _status = MutableStateFlow(SensorStatus())
    val status: StateFlow<SensorStatus> = _status.asStateFlow()

    /** Hangi sensörlerin cihazda mevcut olduğu. */
    val availability: Map<SensorType, Boolean>
        get() = sensors.mapValues { it.value != null }

    /**
     * Tüm sensörlerin event'lerini tek bir cold flow olarak yayar.
     * Flow toplanırken listener kaydedilir, toplama durunca kaydı silinir —
     * yani lifecycle-aware, otomatik temizlik.
     *
     * Sampling: [SensorManager.SENSOR_DELAY_GAME] makul bir başlangıç (~50ms).
     * İleride mikrosaniye-level kontrol için `samplingPeriodUs` parametre eklenebilir.
     */
    fun samples(): Flow<SensorSample> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val type = SensorType.fromAndroidType(event.sensor.type) ?: return
                val nowMs = System.currentTimeMillis()
                val sample = SensorSample(
                    sensorType = type,
                    timestampNanos = event.timestamp,
                    eventTimeMs = nowMs,
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                )
                // Hafif: sadece kanala push et, iş yapma.
                trySend(sample)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // V1'de kullanılmıyor; ileride accuracy state'e yansıtılabilir.
            }
        }

        var registered = 0
        sensors.forEach { (type, sensor) ->
            if (sensor != null) {
                sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
                registered++
            }
        }
        _status.update { it.copy(active = registered > 0) }

        awaitClose {
            sensorManager.unregisterListener(listener)
            _status.update { it.copy(active = false) }
        }
    }

    /** Verilen sensör tipi için [Sensor] var mı? */
    fun hasSensor(type: SensorType): Boolean = sensors[type] != null

    /** Debug için mevcut sensörlerin isim/üretici bilgisi. */
    fun sensorInfo(): Map<SensorType, String?> =
        sensors.mapValues { (type, s) ->
            when (s) {
                null -> null
                else -> "${s.name} (${s.vendor})"
            }
        }
}

/**
 * Sensör katmanının anlık durumu (debug + ViewModel için).
 */
data class SensorStatus(
    val active: Boolean = false,
)