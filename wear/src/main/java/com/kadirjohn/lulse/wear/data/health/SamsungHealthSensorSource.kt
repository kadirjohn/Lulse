package com.kadirjohn.lulse.wear.data.health

/**
 * **Samsung Health Sensor SDK implementasyonu — AAR gerekir** (docs 02).
 *
 * Bu dosya, `wear/libs/samsung-health-sensor-api.aar` indirildiğinde doldurulacak.
 * AAR olmadan derlenmez (Samsung sınıfları eksik), bu yüzden şimdilik
 * [StubHealthSensorSource] kullanılır ve wear modülü AAR'siz derlenir.
 *
 * ## AAR geldiğinde yapılacaklar (docs 02 service lifecycle):
 *
 * 1. `wear/build.gradle.kts`: AAR otomatik `implementation(files(...))`'e bağlanır
 *    (koşullu blok zaten var).
 * 2. Bu sınıfı `HealthSensorSource` implementasyonuyla doldur:
 *    ```kotlin
 *    class SamsungHealthSensorSource(
 *        private val context: Context,
 *        private val scope: CoroutineScope,
 *    ) : HealthSensorSource {
 *        private var service: HealthTrackingService? = null
 *        private var tracker: HealthTracker? = null
 *
 *        override val state = MutableStateFlow(WatchTrackingState.UNINITIALIZED)
 *        override val events = MutableSharedFlow<WatchHeartRateEvent>(extraBufferCapacity = 16)
 *
 *        fun connect() {
 *            state.value = WatchTrackingState.CONNECTING
 *            service = HealthTrackingService(context, connectionListener)
 *            service.connectService()
 *        }
 *
 *        private val connectionListener = object : HealthTrackingService.ConnectionListener {
 *            override fun onConnectionSuccess() = checkCapability()
 *            override fun onConnectionEnded() { state.value = WatchTrackingState.UNINITIALIZED }
 *            override fun onError(error: HealthTrackingService.Error) {
 *                state.value = when (error) {
 *                    POLICY_ERROR -> WatchTrackingState.POLICY_ERROR
 *                    OLD_PLATFORM -> WatchTrackingState.OLD_PLATFORM
 *                    else -> WatchTrackingState.ERROR
 *                }
 *            }
 *        }
 *
 *        private fun checkCapability() {
 *            val cap = service.trackingCapability
 *            if (!cap.isSupported(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
 *                state.value = WatchTrackingState.TRACKER_UNSUPPORTED
 *                return
 *            }
 *            // BODY_SENSORS runtime permission kontrolü → PERMISSION_REQUIRED
 *            state.value = WatchTrackingState.SERVICE_READY
 *        }
 *
 *        override fun startTracking(sessionId: String?) {
 *            tracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
 *            tracker.setEventListener(eventListener)  // veya setEventListener(listener, DATA_TYPE)
 *            state.value = WatchTrackingState.TRACKING
 *        }
 *
 *        private val eventListener = HealthTracker.TrackerEventListener { dataPointData ->
 *            // HeartRateSet oku (docs 02):
 *            //  HEART_RATE, HEART_RATE_STATUS, IBI_LIST, IBI_STATUS_LIST, DataPoint timestamp
 *            val hr = dataPointData.getInt(ValueKey.HeartRateSet.HEART_RATE)
 *            val status = dataPointData.getInt(ValueKey.HeartRateSet.HEART_RATE_STATUS)
 *            val ibis = dataPointData.getIntegerList(ValueKey.HeartRateSet.IBI_LIST)
 *            val ibiStatus = dataPointData.getIntegerList(ValueKey.HeartRateSet.IBI_STATUS_LIST)
 *            val ts = dataPointData.timestamp
 *            val now = SystemClock.elapsedRealtimeNanos()
 *            // HR status → WatchTrackingState (MOVEMENT/WEAK_SIGNAL/DETACHED) — docs 02 status kodları
 *            updateStateFromHrStatus(status)
 *            // Olayı emit et — valid IBI'leri filtrele (IBI_STATUS==0, IBI>0)
 *            events.tryEmit(WatchHeartRateEvent(...))
 *        }
 *
 *        override fun stopTracking() { tracker.unsetEventListener(); ... }
 *        override fun release() { service.disconnectService(); ... }
 *    }
 *    ```
 * 3. [com.kadirjohn.lulse.wear.MainActivity] veya WearTransportRepository,
 *    `if (samsungAarExists) SamsungHealthSensorSource(ctx, scope)
 *     else StubHealthSensorSource(scope)` ile seçer.
 *
 * ## Önemli (docs 02, 03):
 *  - 1 callback = 1 beat değildir (~1 Hz, 0–4 IBI paket). Per-beat timestamp icat etme.
 *  - HR_STATUS kodları: 1 SUCCESS, 0 INITIAL, -2 MOVEMENT, -3 DETACHED, -8 WEAK_PPG,
 *    -10 WEAK_PPG_MOVEMENT, -999 HIGHER_PRIORITY_SENSOR.
 *  - Orijinal Samsung DataPoint/callback/send zamanını koru — telefon receive ile değiştirme.
 *  - Geçersiz durumları da emit et (debug için), ama valid kapısı HR_STATUS==1.
 *  - Developer Mode watch'ta etkin olmalı (docs 02, 04).
 *  - Emülatör desteklenmez — gerçek Watch6 Classic gerekli.
 */
class SamsungHealthSensorSourcePlaceholder