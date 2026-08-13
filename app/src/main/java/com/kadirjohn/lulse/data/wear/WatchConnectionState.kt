package com.kadirjohn.lulse.data.wear

import com.kadirjohn.lulse.shared.Protocol

/**
 * Telefonun watch bağlantı durumu (docs 01, 03, 04).
 * Watch opsiyoneldir — her durumda telefon ölçümü devam eder.
 */
sealed interface WatchConnectionState {
    /** Watch node bağlı değil. */
    data object Disconnected : WatchConnectionState
    /** Watch bağlı ama lulse_watch_reference capability'si bulunamadı (wear app yok?). */
    data object ConnectedAppNotDetected : WatchConnectionState
    /** Capability + HELLO_ACK alındı — watch hazır (docs 03 connection handshake). */
    data class Ready(
        val nodeId: String,
        val nodeName: String,
        val protocolVersion: Int,
        val wearAppVersion: String,
        val watchModel: String,
        val sdkReady: Boolean,
        val heartRateTrackerSupported: Boolean,
        val trackingState: String,
    ) : WatchConnectionState
    /** Protocol sürümü uyumsuz (docs 03, 04). */
    data class Incompatible(val nodeId: String, val watchProtocol: Int) : WatchConnectionState
    /** Watch'tan hata/timeout. */
    data class Error(val message: String) : WatchConnectionState
}

/** Watch durumu için kısa insanca etiket (debug UI için). */
fun WatchConnectionState.label(): String = when (this) {
    is WatchConnectionState.Disconnected -> "DISCONNECTED"
    is WatchConnectionState.ConnectedAppNotDetected -> "APP_NOT_DETECTED"
    is WatchConnectionState.Ready -> "READY"
    is WatchConnectionState.Incompatible -> "INCOMPATIBLE"
    is WatchConnectionState.Error -> "ERROR"
}

/** Watch bağlı ve hazır mı (UI ⌚ buton rengi için). */
val WatchConnectionState.isReady: Boolean
    get() = this is WatchConnectionState.Ready

/** Watch node'una ulaşılabilir mi (capability keşfi sonrası). */
val WatchConnectionState.isConnected: Boolean
    get() = this !is WatchConnectionState.Disconnected

/** Beklenen protocol sürümü (docs 03). */
val EXPECTED_PROTOCOL_VERSION: Int get() = Protocol.PROTOCOL_VERSION