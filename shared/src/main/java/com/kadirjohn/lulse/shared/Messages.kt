package com.kadirjohn.lulse.shared

import kotlinx.serialization.Serializable

/**
 * Wear OS Data Layer mesaj gövdeleri (docs 03, 05).
 *
 * Hepsi `@Serializable` — kotlinx.serialization JSON ile byte[]'e serileştirip
 * MessageClient üzerinden taşınır. Tüm mesajlar [protocolVersion] taşır.
 *
 * Önemli (docs 02, 03): Samsung HEART_RATE_CONTINUOUS ~1 Hz, bir HeartRateSet'te
 * 0–4 IBI. **1 mesaj = 1 beat değildir.** IBI için bağımsız per-beat timestamp
 * yok — icat etme, sadece liste halinde sakla.
 */

/** Watch'tan telefona HELLO'ya cevap — bağlantı ve hazırlık teyidi (docs 03, 04). */
@Serializable
data class HelloAck(
    val protocolVersion: Int,
    val wearAppVersion: String,
    val watchModel: String,
    val sdkReady: Boolean,
    val heartRateTrackerSupported: Boolean,
    val trackingState: String, // WatchTrackingState adı
)

/** Watch'tan telefona HR + IBI olayı (docs 03 HR message schema, docs 02 model). */
@Serializable
data class WatchHrMessage(
    val protocolVersion: Int,
    val type: String = "watch_hr",
    val sessionId: String?,
    val sequence: Long,
    // Watch tarafı zaman damgaları (docs 02 — orijinal Samsung/callback/send zamanı).
    val watchDataPointTimestamp: Long,
    val watchCallbackElapsedNanos: Long,
    val watchSendElapsedNanos: Long,
    // HR + status (docs 02).
    val heartRateBpm: Int,
    val heartRateStatus: Int,
    // IBI listeleri — 0–4 değer, status listesiyle eş uzunlukta (docs 02, 03).
    val ibiValuesMs: List<Int>,
    val ibiStatuses: List<Int>,
)

/** Telefon → watch oturum komutu (docs 05 Phase 7). Telefon sessionId'nin sahibi. */
@Serializable
data class SessionCommand(
    val type: String, // "session_start" | "session_stop"
    val sessionId: String,
)

/** Telefon → watch clock sync ping (docs 03 NTP-like). */
@Serializable
data class Ping(
    val syncId: Long,
    val t0PhoneNanos: Long,
)

/** Watch → telefon clock sync pong (docs 03). t1/t2 watch tarafı, t0 phone'dan yansıyan. */
@Serializable
data class Pong(
    val syncId: Long,
    val t0PhoneNanos: Long,
    val t1WatchNanos: Long,
    val t2WatchNanos: Long,
)

/** Telefon → watch durum sorgusu; watch → telefon cevap (docs 05 Phase 2). */
@Serializable
data class StatusResponse(
    val trackingState: String,
    val hrStatus: Int?,
    val lastValidIbiMs: Int?,
)