package com.kadirjohn.lulse.wear.data.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.health.connect.HealthPermissions
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.kadirjohn.lulse.wear.domain.WatchHeartRateEvent
import com.kadirjohn.lulse.wear.domain.WatchTrackingState
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Samsung Health Sensor SDK v1.4.1 tabanlı gerçek HR/IBI kaynağı.
 *
 * Servis hazır olmadan gelen ölçüm isteği bekletilir ve bağlantı kurulunca
 * HEART_RATE_CONTINUOUS tracker başlatılır. Bir Samsung callback'i tek bir kalp
 * atışı sayılmaz; callback içindeki 0–4 IBI değeri kendi status koduyla korunur.
 */
class SamsungHealthSensorSource(
    context: Context,
    private val scope: CoroutineScope,
) : HealthSensorSource {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(WatchTrackingState.UNINITIALIZED)
    override val state: StateFlow<WatchTrackingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WatchHeartRateEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<WatchHeartRateEvent> = _events.asSharedFlow()

    override val sdkReady: Boolean = true

    @Volatile
    private var trackerSupported = false
    override val heartRateTrackerSupported: Boolean
        get() = trackerSupported

    @Volatile
    override var lastHeartRateStatus: Int? = null
        private set

    @Volatile
    override var lastValidIbiMs: Int? = null
        private set

    @Volatile
    private var service: HealthTrackingService? = null

    @Volatile
    private var tracker: HealthTracker? = null

    @Volatile
    private var connected = false

    @Volatile
    private var connectionRequested = false

    @Volatile
    private var trackingRequested = false

    @Volatile
    private var tracking = false

    @Volatile
    private var released = false

    @Volatile
    private var sessionId: String? = null

    private var sequence = 0L

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            if (released) return
            connectionRequested = false
            connected = true
            val currentService = service ?: return
            trackerSupported = runCatching {
                currentService.trackingCapability.supportHealthTrackerTypes
                    .contains(HealthTrackerType.HEART_RATE_CONTINUOUS)
            }.getOrElse {
                Log.e(TAG, "Samsung tracker capability check failed", it)
                false
            }

            if (!trackerSupported) {
                _state.value = WatchTrackingState.TRACKER_UNSUPPORTED
                return
            }

            _state.value = WatchTrackingState.SERVICE_READY
            if (trackingRequested) startTrackerWhenReady()
        }

        override fun onConnectionEnded() {
            connected = false
            connectionRequested = false
            tracking = false
            tracker = null
            service = null
            trackerSupported = false
            if (!released) _state.value = WatchTrackingState.UNINITIALIZED
        }

        override fun onConnectionFailed(error: HealthTrackerException) {
            Log.e(TAG, "Samsung Health Sensor service connection failed", error)
            connected = false
            connectionRequested = false
            tracking = false
            tracker = null
            service = null
            trackerSupported = false
            _state.value = when (error.errorCode) {
                HealthTrackerException.OLD_PLATFORM_VERSION,
                HealthTrackerException.PACKAGE_NOT_INSTALLED -> WatchTrackingState.OLD_PLATFORM
                else -> WatchTrackingState.ERROR
            }
        }
    }

    private val eventListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: List<DataPoint>) {
            val callbackNanos = SystemClock.elapsedRealtimeNanos()
            dataPoints.forEach { dataPoint -> processDataPoint(dataPoint, callbackNanos) }
        }

        override fun onFlushCompleted() = Unit

        override fun onError(error: HealthTracker.TrackerError) {
            Log.e(TAG, "Samsung HR tracker error: $error")
            tracking = false
            _state.value = when (error) {
                HealthTracker.TrackerError.PERMISSION_ERROR -> WatchTrackingState.PERMISSION_REQUIRED
                HealthTracker.TrackerError.SDK_POLICY_ERROR -> WatchTrackingState.POLICY_ERROR
                else -> WatchTrackingState.ERROR
            }
        }
    }

    init {
        connect()
    }

    override fun startTracking(sessionId: String?) {
        this.sessionId = sessionId
        trackingRequested = true

        if (!hasHeartRatePermission()) {
            _state.value = WatchTrackingState.PERMISSION_REQUIRED
            return
        }
        if (!connected) {
            connect()
            return
        }
        startTrackerWhenReady()
    }

    private fun connect() {
        if (released || connected || connectionRequested) return
        connectionRequested = true
        _state.value = WatchTrackingState.CONNECTING
        try {
            service = HealthTrackingService(connectionListener, appContext).also {
                it.connectService()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Could not create Samsung Health Sensor service", error)
            connectionRequested = false
            service = null
            _state.value = WatchTrackingState.ERROR
        }
    }

    private fun startTrackerWhenReady() {
        if (released || !trackingRequested || tracking) return
        if (!hasHeartRatePermission()) {
            _state.value = WatchTrackingState.PERMISSION_REQUIRED
            return
        }
        if (!trackerSupported) {
            _state.value = WatchTrackingState.TRACKER_UNSUPPORTED
            return
        }

        mainHandler.post {
            if (released || !trackingRequested || tracking) return@post
            try {
                val activeTracker = service
                    ?.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                    ?: throw IllegalStateException("Samsung Health Sensor service is not connected")
                tracker = activeTracker
                activeTracker.setEventListener(eventListener)
                tracking = true
                _state.value = WatchTrackingState.TRACKING
            } catch (error: SecurityException) {
                Log.e(TAG, "Heart-rate permission missing", error)
                _state.value = WatchTrackingState.PERMISSION_REQUIRED
            } catch (error: Throwable) {
                Log.e(TAG, "Could not start Samsung HR tracker", error)
                _state.value = WatchTrackingState.ERROR
            }
        }
    }

    private fun processDataPoint(dataPoint: DataPoint, callbackNanos: Long) {
        val heartRate: Int = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)
        val heartRateStatus: Int = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
        val ibiValues: List<Int> = dataPoint.getValue(ValueKey.HeartRateSet.IBI_LIST) ?: emptyList()
        val ibiStatuses: List<Int> =
            dataPoint.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST) ?: emptyList()
        val validIbis = ibiValues.mapIndexedNotNull { index, ibi ->
            ibi.takeIf { it > 0 && ibiStatuses.getOrNull(index) == IBI_STATUS_VALID }
        }

        lastHeartRateStatus = heartRateStatus
        validIbis.lastOrNull()?.let { lastValidIbiMs = it }
        updateStateFromHeartRateStatus(heartRateStatus)

        val event = WatchHeartRateEvent(
            sessionId = sessionId,
            sequence = sequence++,
            dataPointTimestamp = dataPoint.timestamp,
            callbackElapsedNanos = callbackNanos,
            sendElapsedNanos = SystemClock.elapsedRealtimeNanos(),
            heartRateBpm = heartRate,
            heartRateStatus = heartRateStatus,
            ibiValuesMs = ibiValues,
            ibiStatuses = ibiStatuses,
            validIbiValuesMs = validIbis,
        )
        if (!_events.tryEmit(event)) {
            scope.launch { _events.emit(event) }
        }
    }

    private fun updateStateFromHeartRateStatus(status: Int) {
        _state.value = when (status) {
            HR_STATUS_MOVEMENT,
            HR_STATUS_WEAK_SIGNAL_AND_MOVEMENT -> WatchTrackingState.MOVEMENT
            HR_STATUS_DETACHED -> WatchTrackingState.DETACHED
            HR_STATUS_WEAK_SIGNAL -> WatchTrackingState.WEAK_SIGNAL
            HR_STATUS_HIGHER_PRIORITY_SENSOR -> WatchTrackingState.ERROR
            else -> WatchTrackingState.TRACKING
        }
    }

    override fun stopTracking() {
        trackingRequested = false
        sessionId = null
        mainHandler.post {
            runCatching { tracker?.unsetEventListener() }
                .onFailure { Log.w(TAG, "Could not stop Samsung HR tracker cleanly", it) }
            tracker = null
            tracking = false
            if (!released) {
                _state.value = if (connected) {
                    WatchTrackingState.SERVICE_READY
                } else {
                    WatchTrackingState.UNINITIALIZED
                }
            }
        }
    }

    override fun release() {
        released = true
        trackingRequested = false
        mainHandler.post {
            runCatching { tracker?.unsetEventListener() }
            tracker = null
            tracking = false
            runCatching { service?.disconnectService() }
            service = null
            connected = false
            connectionRequested = false
            trackerSupported = false
            _state.value = WatchTrackingState.UNINITIALIZED
        }
    }

    private fun hasHeartRatePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, requiredPermission()) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "LULSE_SAMSUNG_HR"
        private const val IBI_STATUS_VALID = 0
        private const val HR_STATUS_MOVEMENT = -2
        private const val HR_STATUS_DETACHED = -3
        private const val HR_STATUS_WEAK_SIGNAL = -8
        private const val HR_STATUS_WEAK_SIGNAL_AND_MOVEMENT = -10
        private const val HR_STATUS_HIGHER_PRIORITY_SENSOR = -999

        fun requiredPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            HealthPermissions.READ_HEART_RATE
        } else {
            Manifest.permission.BODY_SENSORS
        }
    }
}
