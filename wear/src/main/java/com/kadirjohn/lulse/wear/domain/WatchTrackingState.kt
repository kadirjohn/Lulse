package com.kadirjohn.lulse.wear.domain

import com.kadirjohn.lulse.shared.hrStatusName

/**
 * Watch'ta Samsung Health Tracking'in durumu (docs 02 "Watch state model").
 * Tüm başarısızlık tipleri "NO_BPM"'e flatlenmez — UI farklı mesajlar gösterir.
 */
enum class WatchTrackingState {
    UNINITIALIZED,
    CONNECTING,
    SERVICE_READY,
    TRACKER_UNSUPPORTED,
    PERMISSION_REQUIRED,
    TRACKING,
    WEAK_SIGNAL,
    MOVEMENT,
    DETACHED,
    POLICY_ERROR,
    OLD_PLATFORM,
    ERROR,
}

/**
 * Watch'tan üretilen tek bir HR olayı (docs 02 "Suggested Watch model").
 *
 * Önemli (docs 02): orijinal zaman damgaları korunur — Samsung DataPoint,
 * callback, send zamanı. ASLA birbiriyle değiştirilmez.
 * [validIbiValuesMs] IBI_STATUS==0 ve >0 olan IBI'ler.
 */
data class WatchHeartRateEvent(
    val sessionId: String?,
    val sequence: Long,
    val dataPointTimestamp: Long,
    val callbackElapsedNanos: Long,
    val sendElapsedNanos: Long,
    val heartRateBpm: Int,
    val heartRateStatus: Int,
    val ibiValuesMs: List<Int>,
    val ibiStatuses: List<Int>,
    val validIbiValuesMs: List<Int>,
)

/**
 * Watch UI için HR durumundan insanca metin (docs 05 invalid states).
 * Bu, ekranda "-- BPM / Adjust the watch" vb. gösterir.
 */
fun WatchTrackingState.uiLabel(): String = when (this) {
    WatchTrackingState.UNINITIALIZED,
    WatchTrackingState.CONNECTING,
    WatchTrackingState.SERVICE_READY -> "Measuring..."
    WatchTrackingState.TRACKING -> ""
    WatchTrackingState.WEAK_SIGNAL -> "Weak signal"
    WatchTrackingState.MOVEMENT -> "Movement detected"
    WatchTrackingState.DETACHED -> "Watch not on wrist"
    WatchTrackingState.TRACKER_UNSUPPORTED -> "Sensor unsupported"
    WatchTrackingState.PERMISSION_REQUIRED -> "Permission needed"
    WatchTrackingState.POLICY_ERROR -> "Developer mode required"
    WatchTrackingState.OLD_PLATFORM -> "Watch too old"
    WatchTrackingState.ERROR -> "Error"
}