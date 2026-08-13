package com.kadirjohn.lulse.wear.data.health

import com.kadirjohn.lulse.wear.domain.WatchHeartRateEvent
import com.kadirjohn.lulse.wear.domain.WatchTrackingState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Watch'ta HR/IBI kaynağı soyutlaması (docs 02).
 *
 * İki implementasyon:
 *  - [StubHealthSensorSource]: Samsung AAR yokken 1 Hz'de sahte 72 BPM + 833ms IBI
 *    üretir. Transport (Phase A) tam test edilir, wear APK AAR'siz derlenir.
 *  - [SamsungHealthSensorSource]: AAR varsa `HealthTrackingService`'e bağlanıp
 *    `HEART_RATE_CONTINUOUS` tracker'ı kurar (docs 02 service lifecycle).
 *
 * AAR `wear/libs/samsung-health-sensor-api.aar` yoksa stub kullanılır (build.gradle.kts
 * koşullu dependency). AAR geldiğinde sadece binding değişir — interface aynı.
 *
 * Önemli (docs 02): 1 callback = 1 beat değildir (~1 Hz, 0–4 IBI paket).
 */
interface HealthSensorSource {
    /** Watch tracking durumu — UI ve transport'a yansır. */
    val state: StateFlow<WatchTrackingState>

    /** HR/IBI olay akışı — transport bunu telefona yollar. */
    val events: SharedFlow<WatchHeartRateEvent>

    /** Tracker'ı başlat (session start / connect sonrası). */
    fun startTracking(sessionId: String?)

    /** Tracker'ı durdur (session stop). */
    fun stopTracking()

    /** Kaynak serbest bırakma. */
    fun release()
}