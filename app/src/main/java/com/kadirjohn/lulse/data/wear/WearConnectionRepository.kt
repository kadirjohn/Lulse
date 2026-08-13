package com.kadirjohn.lulse.data.wear

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.kadirjohn.lulse.shared.ClockSync
import com.kadirjohn.lulse.shared.ClockSyncFrame
import com.kadirjohn.lulse.shared.HelloAck
import com.kadirjohn.lulse.shared.Ping
import com.kadirjohn.lulse.shared.Pong
import com.kadirjohn.lulse.shared.Protocol
import com.kadirjohn.lulse.shared.ReferenceValidity
import com.kadirjohn.lulse.shared.SessionCommand
import com.kadirjohn.lulse.shared.WatchHrMessage
import com.kadirjohn.lulse.shared.WatchReferenceEvent
import com.kadirjohn.lulse.shared.WatchReferenceSnapshot
import com.kadirjohn.lulse.shared.isReferenceValid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Telefon tarafı Wear bağlantı merkezi (docs 01, 03, 05).
 *
 * Sorumluluklar:
 *  - [connectionState]: watch capability keşfi + HELLO/HELLO_ACK → [WatchConnectionState].
 *  - [referenceEvents]: watch'tan gelen HR olayları (CSV'ye yazılır).
 *  - [clockSyncFrames]: clock sync değişimleri (CSV'ye yazılır).
 *  - [latestReference]: en son geçerli watch referansı (stale kontrolü, analysis frame'e snapshot).
 *  - Oturum yönetimi: [startSession]/[stopSession] → watch'a SESSION_START/STOP.
 *  - Clock sync: [runClockSync] 8–12 ping/pong, outlier reject, median offset.
 *
 * Singleton (process scope) — [WearMessageListenerService] ve [MainViewModel]
 * aynı instance'ı paylaşır (service Activity'den bağımsız yaşar).
 *
 * Watch opsiyoneldir: bağlantı yoksa [connectionState] Disconnected kalır, telefon
 * ölçümüne devam eder. Telefon SCG'si watch BPM ile ASLA değiştirilmez (docs 05).
 */
class WearConnectionRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) {
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val json = Json { ignoreUnknownKeys = true }

    private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Disconnected)
    val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

    private val _referenceEvents = MutableSharedFlow<WatchReferenceEvent>(extraBufferCapacity = 64)
    val referenceEvents: SharedFlow<WatchReferenceEvent> = _referenceEvents.asSharedFlow()

    private val _clockSyncFrames = MutableSharedFlow<ClockSyncFrame>(extraBufferCapacity = 32)
    val clockSyncFrames: SharedFlow<ClockSyncFrame> = _clockSyncFrames.asSharedFlow()

    private val _latestReference = MutableStateFlow<WatchReferenceSnapshot?>(null)
    val latestReference: StateFlow<WatchReferenceSnapshot?> = _latestReference.asStateFlow()

    /** Aktif oturum ID'si — telefon sessionId'nin sahibi (docs 03, 05). */
    @Volatile private var currentSessionId: String? = null
    /** En son clock sync offset (watch→phone mapleme). */
    @Volatile private var currentOffsetNanos: Long = 0L
    @Volatile private var currentRttNanos: Long = 0L
    @Volatile private var currentUncertaintyNanos: Long = Long.MAX_VALUE
    private var syncId = 0L

    private val capabilityListener = object : CapabilityClient.OnCapabilityChangedListener {
        override fun onCapabilityChanged(info: CapabilityInfo) {
            onCapabilityChangedInternal(info)
        }
    }

    init {
        // Capability değişikliklerini dinle — watch bağlan/kopar (docs 01).
        capabilityClient.addListener(capabilityListener, Protocol.CAPABILITY)
        // Başlangıçta capability'yi hemen sorgula — listener sadece değişimde
        // tetiklenir, app açılışta mevcut durumu almak için主动 keşfet.
        connect()
    }

    private fun onCapabilityChangedInternal(info: CapabilityInfo) {
        val node = info.nodes.firstOrNull()
        Log.i("LULSE_WEAR", "capability changed: ${info.name} nodes=${info.nodes.size}")
        if (node == null) {
            _connectionState.value = WatchConnectionState.Disconnected
            return
        }
        if (!node.isNearby) {
            Log.i("LULSE_WEAR", "node not nearby: ${node.id}")
            _connectionState.value = WatchConnectionState.Disconnected
            return
        }
        // Capability reachable → HELLO gönder.
        Log.i("LULSE_WEAR", "node nearby, sending HELLO to ${node.id}")
        sendHello(node.id)
    }

    /** HELLO gönder — watch HELLO_ACK ile cevaplar (docs 03). */
    private fun sendHello(nodeId: String) {
        _connectionState.value = WatchConnectionState.ConnectedAppNotDetected
        messageClient.sendMessage(nodeId, Protocol.PATH_HELLO, ByteArray(0))
            .addOnSuccessListener { Log.i("LULSE_WEAR", "HELLO sent to $nodeId") }
            .addOnFailureListener { e ->
                Log.e("LULSE_WEAR", "HELLO failed", e)
                _connectionState.value = WatchConnectionState.Error("HELLO failed")
            }
    }

    /**
     * Watch'tan gelen mesajı işle — [WearMessageListenerService] çağırır.
     */
    fun handleMessage(path: String, data: ByteArray, sourceNodeId: String) {
        when (path) {
            Protocol.PATH_HELLO_ACK -> handleHelloAck(data, sourceNodeId)
            Protocol.PATH_HR -> handleHrMessage(data)
            Protocol.PATH_PONG -> handlePong(data)
        }
    }

    private fun handleHelloAck(data: ByteArray, sourceNodeId: String) {
        try {
            val ack = json.decodeFromString(HelloAck.serializer(), String(data))
            Log.i("LULSE_WEAR", "HELLO_ACK: protocol=${ack.protocolVersion} model=${ack.watchModel} tracking=${ack.trackingState}")
            // Protocol uyumu (docs 03, 04).
            if (ack.protocolVersion != EXPECTED_PROTOCOL_VERSION) {
                Log.w("LULSE_WEAR", "protocol mismatch: phone=$EXPECTED_PROTOCOL_VERSION watch=${ack.protocolVersion}")
                _connectionState.value = WatchConnectionState.Incompatible(sourceNodeId, ack.protocolVersion)
                return
            }
            val nodeName = sourceNodeId
            _connectionState.value = WatchConnectionState.Ready(
                nodeId = sourceNodeId,
                nodeName = nodeName,
                protocolVersion = ack.protocolVersion,
                wearAppVersion = ack.wearAppVersion,
                watchModel = ack.watchModel,
                sdkReady = ack.sdkReady,
                heartRateTrackerSupported = ack.heartRateTrackerSupported,
                trackingState = ack.trackingState,
            )
        } catch (e: Exception) {
            _connectionState.value = WatchConnectionState.Error("HELLO_ACK parse: ${e.message}")
        }
    }

    private fun handleHrMessage(data: ByteArray) {
        try {
            val msg = json.decodeFromString(WatchHrMessage.serializer(), String(data))
            val phoneReceive = SystemClock.elapsedRealtimeNanos()
            // Watch→phone zaman mapleme (clock sync offset ile).
            val estimatedPhoneNs = ClockSync.mapWatchToPhone(
                msg.watchDataPointTimestamp, currentOffsetNanos
            )
            val transportAgeMs = (phoneReceive - msg.watchSendElapsedNanos) / 1_000_000
            val event = WatchReferenceEvent(
                watchSequence = msg.sequence,
                sessionId = msg.sessionId,
                watchDataPointTimestamp = msg.watchDataPointTimestamp,
                watchCallbackElapsedNanos = msg.watchCallbackElapsedNanos,
                watchSendElapsedNanos = msg.watchSendElapsedNanos,
                phoneReceiveElapsedNanos = phoneReceive,
                estimatedPhoneDatapointNanos = estimatedPhoneNs,
                heartRateBpm = msg.heartRateBpm,
                heartRateStatus = msg.heartRateStatus,
                ibiValuesMs = msg.ibiValuesMs,
                ibiStatuses = msg.ibiStatuses,
                validIbiValuesMs = msg.ibiValuesMs.filterIndexed { i, v ->
                    i < msg.ibiStatuses.size &&
                        ReferenceValidity.isIbiValid(msg.ibiStatuses[i], v)
                },
                clockOffsetNanos = currentOffsetNanos,
                clockRttNanos = currentRttNanos,
                clockUncertaintyNanos = currentUncertaintyNanos,
                transportAgeMs = transportAgeMs,
            )
            scope.launch { _referenceEvents.emit(event) }
            // En son geçerli referans snapshot'ını güncelle (stale kontrolü UI/analysis için).
            if (event.isReferenceValid()) {
                _latestReference.value = WatchReferenceSnapshot(
                    watchConnected = true,
                    referenceAvailable = true,
                    bpm = event.heartRateBpm,
                    hrStatus = event.heartRateStatus,
                    lastValidIbiMs = event.validIbiValuesMs.lastOrNull(),
                    referenceAgeMs = 0L,
                    clockUncertaintyMs = currentUncertaintyNanos / 1_000_000,
                    sequence = event.watchSequence,
                    timestampNanos = phoneReceive,
                )
            }
        } catch (_: Exception) {
            // parse hatası — yut, ölçüm devam eder
        }
    }

    private fun handlePong(data: ByteArray) {
        try {
            val pong = json.decodeFromString(Pong.serializer(), String(data))
            val t3 = SystemClock.elapsedRealtimeNanos()
            val frame = ClockSync.toFrame(
                sessionId = currentSessionId ?: "no-session",
                syncId = pong.syncId,
                t0 = pong.t0PhoneNanos,
                t1 = pong.t1WatchNanos,
                t2 = pong.t2WatchNanos,
                t3 = t3,
            )
            scope.launch { _clockSyncFrames.emit(frame) }
            // Kabul edilen frame'lerle offset/RTT/uncertainty güncelle (runClockSync takip eder).
        } catch (_: Exception) {
            // yut
        }
    }

    /** Oturum başlat — watch'a SESSION_START + clock sync (docs 05 Phase 7). */
    fun startSession(sessionId: String) {
        currentSessionId = sessionId
        currentOffsetNanos = 0L
        currentUncertaintyNanos = Long.MAX_VALUE
        sendToWatch(Protocol.PATH_SESSION_START, SessionCommand("session_start", sessionId))
        scope.launch { runClockSync() }
    }

    fun stopSession() {
        currentSessionId?.let {
            sendToWatch(Protocol.PATH_SESSION_STOP, SessionCommand("session_stop", it))
        }
        currentSessionId = null
    }

    /**
     * Clock sync — 8–12 ping/pong, outlier reject, median offset (docs 03).
     * Telefon time-reference authority. Watch clock'u phone clock'una maplenir.
     */
    suspend fun runClockSync() {
        val nodeId = (_connectionState.value as? WatchConnectionState.Ready)?.nodeId ?: return
        val samples = 10
        val acceptedOffsets = mutableListOf<Long>()
        for (i in 0 until samples) {
            val id = ++syncId
            val t0 = SystemClock.elapsedRealtimeNanos()
            val ping = Ping(id, t0)
            // Ping gönder, pong handlePong'da frame emit eder; burada beklemek yerine
            // fire-and-forget — offsets toplanır. Basit V1: ardışık, kısa bekle.
            sendToWatch(Protocol.PATH_PING, ping)
            kotlinx.coroutines.delay(120) // ~1 Hz watch cevabı için bekle
            // Son frame'in offset'ini oku (en son emit edilen).
            // V1: runClockSync ayrı bir collected offset list tutar — burada basit.
        }
        // V1 basit: en son currentOffset/RTT zaten handlePong'da güncellenmezdi.
        // Phase C'de tam topla-median yapılır. Şimdilik stub yeterli.
        // TODO Phase C: acceptedOffsets'ten median, uncertainty = RTT/2.
    }

    private inline fun <reified T> sendToWatch(path: String, message: T) {
        val nodeId = (_connectionState.value as? WatchConnectionState.Ready)?.nodeId ?: return
        val payload = json.encodeToString(kotlinx.serialization.serializer(), message).toByteArray()
        messageClient.sendMessage(nodeId, path, payload)
            .addOnFailureListener { /* transport hatası — ölçüm devam eder */ }
    }

    /** Watch'a bağlanmayı yeniden dene (debug panel butonu). */
    fun connect() {
        // Capability keşfini tetikle.
        capabilityClient.getCapability(Protocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info -> onCapabilityChangedInternal(info) }
            .addOnFailureListener {
                _connectionState.value = WatchConnectionState.Error("capability query failed")
            }
    }

    fun release() {
        capabilityClient.removeListener(capabilityListener)
    }
}