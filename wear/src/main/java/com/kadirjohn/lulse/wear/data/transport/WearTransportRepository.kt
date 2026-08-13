package com.kadirjohn.lulse.wear.data.transport

import android.content.Context
import android.os.SystemClock
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.kadirjohn.lulse.shared.ClockSync
import com.kadirjohn.lulse.shared.HelloAck
import com.kadirjohn.lulse.shared.Ping
import com.kadirjohn.lulse.shared.Pong
import com.kadirjohn.lulse.shared.Protocol
import com.kadirjohn.lulse.shared.SessionCommand
import com.kadirjohn.lulse.shared.StatusResponse
import com.kadirjohn.lulse.shared.WatchHrMessage
import com.kadirjohn.lulse.wear.data.health.HealthSensorSource
import com.kadirjohn.lulse.wear.domain.WatchHeartRateEvent
import com.kadirjohn.lulse.wear.domain.WatchTrackingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.google.android.gms.tasks.Task

/**
 * Watch → telefon Wear OS Data Layer taşıma katmanı (docs 01, 05 Phase 2).
 *
 * Sorumluluklar:
 *  - `lulse_watch_reference` capability'sini advertise et → telefon watch'ı bulur.
 *  - Telefondan gelen mesajları al: HELLO, SESSION_START/STOP, PING, STATUS_REQUEST.
 *  - Telefona gönder: HELLO_ACK, HR olayları, PONG, STATUS_RESPONSE.
 *  - [HealthSensorSource].events akışını [WatchHrMessage]'a serileştirip yollar.
 *
 * [HealthSensorSource] stub veya Samsung olabilir — transport kaynak-agnostik.
 */
class WearTransportRepository(
    private val context: Context,
    private val healthSource: HealthSensorSource,
    private val scope: CoroutineScope,
) {
    private val messageClient = Wearable.getMessageClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val json = Json { ignoreUnknownKeys = true }

    /** Şu anki oturum ID'si — telefon SESSION_START ile gönderir. */
    private val _currentSession = MutableStateFlow<String?>(null)
    val currentSession: StateFlow<String?> = _currentSession.asStateFlow()

    /** Watch verisyonu (docs 04 — HELLO_ACK'te gönderilir). */
    private val wearAppVersion = "1.0"

    private val watchModel = android.os.Build.MODEL

    init {
        // HR olaylarını telefona yolla.
        scope.launch {
            healthSource.events.collect { event ->
                sendHrEvent(event)
            }
        }
    }

    /** Capability advertise et — telefon keşfi için. Wear OS otomatik handler. */
    fun advertiseCapability() {
        // CapabilityClient.addLocalCapability Wear'da capability ekler.
        // (Bazı Wear OS sürümlerinde capability advertise wearapp'da manifest/provider
        //  üzerinden de yapılır; runtime API yeterli docs 01 için.)
        capabilityClient.addLocalCapability(Protocol.CAPABILITY)
    }

    /**
     * Telefondan gelen mesajı işle — [WearMessageListenerService] çağırır.
     * Path'e göre dispatch.
     */
    fun handleMessage(path: String, data: ByteArray, nodeId: String) {
        when (path) {
            Protocol.PATH_HELLO -> sendHelloAck(nodeId)
            Protocol.PATH_SESSION_START -> {
                val cmd = json.decodeFromString<SessionCommand>(String(data))
                _currentSession.value = cmd.sessionId
                healthSource.startTracking(cmd.sessionId)
            }
            Protocol.PATH_SESSION_STOP -> {
                _currentSession.value = null
                healthSource.stopTracking()
            }
            Protocol.PATH_PING -> handlePing(nodeId, data)
            Protocol.PATH_STATUS_REQUEST -> sendStatusResponse(nodeId)
        }
    }

    private fun sendHelloAck(nodeId: String) {
        val ack = HelloAck(
            protocolVersion = Protocol.PROTOCOL_VERSION,
            wearAppVersion = wearAppVersion,
            watchModel = watchModel,
            sdkReady = false, // AAR yoksa false; Samsung impl geldiğinde true
            heartRateTrackerSupported = true, // stub her zaman "destekli" simulation
            trackingState = healthSource.state.value.name,
        )
        send(nodeId, Protocol.PATH_HELLO_ACK, json.encodeToString(HelloAck.serializer(), ack))
    }

    private fun sendStatusResponse(nodeId: String) {
        val resp = StatusResponse(
            trackingState = healthSource.state.value.name,
            hrStatus = null, // son HR status — health source'dan alınabilir
            lastValidIbiMs = null,
        )
        send(nodeId, Protocol.PATH_STATUS_RESPONSE, json.encodeToString(StatusResponse.serializer(), resp))
    }

    private fun handlePing(nodeId: String, data: ByteArray) {
        // PING: { syncId, t0PhoneNanos }
        // Watch: t1 = receive, t2 = send → PONG ile yansıt (docs 03).
        val ping = json.decodeFromString(Ping.serializer(), String(data))
        val t1Watch = SystemClock.elapsedRealtimeNanos()
        val t2Watch = SystemClock.elapsedRealtimeNanos()
        val pong = Pong(ping.syncId, ping.t0PhoneNanos, t1Watch, t2Watch)
        send(nodeId, Protocol.PATH_PONG, json.encodeToString(Pong.serializer(), pong))
    }

    private fun sendHrEvent(event: WatchHeartRateEvent) {
        // Bağlı node'lara yolla. Node listesini her seferinde sorgulamak pahalıdır;
        // Wear'da genelde tek phone node vardır. MessageClient.sendMessage ile
        // tüm bağlı node'lara yollanır (docs 01: NodeClient).
        val msg = WatchHrMessage(
            protocolVersion = Protocol.PROTOCOL_VERSION,
            sessionId = event.sessionId,
            sequence = event.sequence,
            watchDataPointTimestamp = event.dataPointTimestamp,
            watchCallbackElapsedNanos = event.callbackElapsedNanos,
            watchSendElapsedNanos = SystemClock.elapsedRealtimeNanos(),
            heartRateBpm = event.heartRateBpm,
            heartRateStatus = event.heartRateStatus,
            ibiValuesMs = event.ibiValuesMs,
            ibiStatuses = event.ibiStatuses,
        )
        val payload = json.encodeToString(WatchHrMessage.serializer(), msg).toByteArray()
        // Bağlı node'ları sorgula ve yolla — Task callback tabanlı (coroutines-play-services
        // dependency olmadan). Transport hatası ölçümü durdurmaz (docs 05 Phase 10).
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, Protocol.PATH_HR, payload)
                        .addOnFailureListener { /* yut */ }
                }
            }
            .addOnFailureListener { /* yut */ }
    }

    private fun send(nodeId: String, path: String, message: String) {
        messageClient.sendMessage(nodeId, path, message.toByteArray())
            .addOnFailureListener { /* transport hatası — ölçüm devam eder */ }
    }

    fun release() {
        healthSource.release()
    }

    // --- UI accessor'ları (MainActivity için) ---

    /** Health source tracking durumu (UI bu state'e göre içerik seçer). */
    fun healthTrackingState(): WatchTrackingState = healthSource.state.value

    /** Health source HR olay akışı (UI son BPM/IBI'yı buradan okur). */
    fun healthEvents(): kotlinx.coroutines.flow.SharedFlow<WatchHeartRateEvent> = healthSource.events
}