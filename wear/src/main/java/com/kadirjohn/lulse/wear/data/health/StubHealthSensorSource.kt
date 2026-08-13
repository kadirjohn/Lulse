package com.kadirjohn.lulse.wear.data.health

import android.os.SystemClock
import com.kadirjohn.lulse.shared.Protocol
import com.kadirjohn.lulse.wear.domain.WatchHeartRateEvent
import com.kadirjohn.lulse.wear.domain.WatchTrackingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Samsung AAR yokken kullanılan sahte [HealthSensorSource] (docs planı Adım 4).
 *
 * 1 Hz'de gerçekçi bir HR olayı üretir: 72 BPM, IBI ~833ms (status=1 SUCCESS).
 * Bu, transport'ı (HELLO/HELLO_ACK/HR mesajı/CSV) gerçek SDK olmadan test etmeyi
 * sağlar. AAR geldiğinde [SamsungHealthSensorSource] ile değiştirilir.
 *
 * Garanti: 1 callback = 1 beat yanılgısı yok — bu stub ~1 Hz üretir, gerçek SDK
 * gibi 0–4 IBI paketler (çoğu zaman 1, ara sıra 2).
 */
class StubHealthSensorSource(
    private val scope: CoroutineScope,
) : HealthSensorSource {

    private val _state = MutableStateFlow(WatchTrackingState.UNINITIALIZED)
    override val state: StateFlow<WatchTrackingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WatchHeartRateEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<WatchHeartRateEvent> = _events.asSharedFlow()

    private var job: Job? = null
    private var sequence = 0L
    private var sessionId: String? = null

    override fun startTracking(sessionId: String?) {
        this.sessionId = sessionId
        if (job?.isActive == true) return
        _state.value = WatchTrackingState.TRACKING
        job = scope.launch {
            // Sahte ~1 Hz HR olayı üretimi.
            // Deterministik — Math.random yerine index tabanlı küçük varyasyon.
            var tick = 0
            while (true) {
                delay(1000) // ~1 Hz (docs 02: processed HR ~1 Hz)
                val now = SystemClock.elapsedRealtimeNanos()
                val tickIndex = (tick++).coerceAtLeast(0)
                // HR: 72 ± 2 (deterministik dalgalanma)
                val bpm = 72 + (tickIndex % 5 - 2)
                // IBI: 60000/72 ≈ 833ms; ara sıra 2 IBI paket (0–4 aralığını simüle)
                val ibiCount = if (tickIndex % 5 == 0) 2 else 1
                val ibis = List(ibiCount) { 833 + (tickIndex % 3) }
                val statuses = List(ibiCount) { 0 } // IBI_STATUS=0 valid
                _events.emit(
                    WatchHeartRateEvent(
                        sessionId = sessionId,
                        sequence = sequence++,
                        dataPointTimestamp = now, // sahte Samsung DataPoint zamanı
                        callbackElapsedNanos = now,
                        sendElapsedNanos = now,
                        heartRateBpm = bpm,
                        heartRateStatus = 1, // SUCCESS (docs 02)
                        ibiValuesMs = ibis,
                        ibiStatuses = statuses,
                        validIbiValuesMs = ibis,
                    )
                )
            }
        }
    }

    override fun stopTracking() {
        job?.cancel()
        job = null
        _state.value = WatchTrackingState.SERVICE_READY
    }

    override fun release() {
        stopTracking()
        _state.value = WatchTrackingState.UNINITIALIZED
    }

    @Suppress("unused")
    private fun protocolVersionCheck(): Int = Protocol.PROTOCOL_VERSION
}