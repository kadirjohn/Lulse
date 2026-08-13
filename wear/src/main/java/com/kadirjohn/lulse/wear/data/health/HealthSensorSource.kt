package com.kadirjohn.lulse.wear.data.health

import com.kadirjohn.lulse.wear.domain.WatchHeartRateEvent
import com.kadirjohn.lulse.wear.domain.WatchTrackingState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Watch'ta HR/IBI kaynağı soyutlaması (docs 02).
 *
 * İki implementasyon bulunur:
 *  - [StubHealthSensorSource]: Samsung AAR yokken 1 Hz'de sahte 72 BPM + 833ms IBI
 *    üretir. Transport (Phase A) tam test edilir, wear APK AAR'siz derlenir.
 *  - [SamsungHealthSensorSource]: AAR varsa `HealthTrackingService`'e bağlanıp
 *    `HEART_RATE_CONTINUOUS` tracker'ı kurar (docs 02 service lifecycle).
 *
 * Üretim binding'i [SamsungHealthSensorSource] kullanır. [StubHealthSensorSource]
 * yalnızca transport'ı sensörsüz test etmek için kaynakta tutulur.
 *
 * Önemli (docs 02): 1 callback = 1 beat değildir (~1 Hz, 0–4 IBI paket).
 */
interface HealthSensorSource {
    /** Gerçek Samsung SDK'sının uygulamaya bağlı olup olmadığı. */
    val sdkReady: Boolean

    /** Bağlı saatin sürekli HR tracker'ını destekleyip desteklemediği. */
    val heartRateTrackerSupported: Boolean

    /** Watch tracking durumu — UI ve transport'a yansır. */
    val state: StateFlow<WatchTrackingState>

    /** HR/IBI olay akışı — transport bunu telefona yollar. */
    val events: SharedFlow<WatchHeartRateEvent>

    /** Durum sorgusu için son ham Samsung HR status kodu. */
    val lastHeartRateStatus: Int?

    /** Durum sorgusu için son geçerli IBI değeri. */
    val lastValidIbiMs: Int?

    /** Tracker'ı başlat (session start / connect sonrası). */
    fun startTracking(sessionId: String?)

    /** Tracker'ı durdur (session stop). */
    fun stopTracking()

    /** Kaynak serbest bırakma. */
    fun release()
}
