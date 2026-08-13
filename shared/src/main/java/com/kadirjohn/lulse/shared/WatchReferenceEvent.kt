package com.kadirjohn.lulse.shared

/**
 * Telefon tarafında, watch'tan gelen [WatchHrMessage]'dan + telefon receive zamanından
 * üretilen tam referans olayı. CSV "watch reference events" tablosuna bir satır olarak
 * yazılır (docs 03 WATCH REFERENCE EVENTS columns).
 *
 * Önemli (docs 02, 03): watch zaman damgaları orijinal haliyle saklanır — Samsung
 * DataPoint/callback/send zamanı ASLA telefon receive zamanıyla değiştirilmez.
 * [estimatedPhoneDatapointNs] clock sync offset ile *tahmini* phone zamanıdır;
 * ham watch zamanları ayrı kolonlarda korunur.
 *
 * @param validIbiValuesMs IBI_STATUS==0 ve IBI>0 olan IBI değerleri (docs 02 valid kuralı).
 */
data class WatchReferenceEvent(
    val watchSequence: Long,
    val sessionId: String?,

    // Watch tarafı orijinal zaman damgaları.
    val watchDataPointTimestamp: Long,
    val watchCallbackElapsedNanos: Long,
    val watchSendElapsedNanos: Long,

    // Telefon tarafı.
    val phoneReceiveElapsedNanos: Long,
    /** Clock sync offset ile tahmini phone zamanı (watchDataPoint - offset). */
    val estimatedPhoneDatapointNanos: Long,

    // HR + status.
    val heartRateBpm: Int,
    val heartRateStatus: Int,

    // IBI listeleri (0–4 değer).
    val ibiValuesMs: List<Int>,
    val ibiStatuses: List<Int>,
    val validIbiValuesMs: List<Int>,

    // Clock sync durumu (olay anındaki offset/RTT/uncertainty).
    val clockOffsetNanos: Long,
    val clockRttNanos: Long,
    val clockUncertaintyNanos: Long,

    /** Watch send → phone receive arası gecikme (ms). */
    val transportAgeMs: Long,
)

/**
 * Watch referans snapshot'ı — her phone analysis frame'e eklenecek en son geçerli
 * referans özeti (docs 03 "Add Watch snapshot to phone analysis frames").
 * Stale kontrolü [referenceAvailable] ile yapılır (threshold ~2.5–3s).
 */
data class WatchReferenceSnapshot(
    val watchConnected: Boolean,
    val referenceAvailable: Boolean,
    val bpm: Int?,
    val hrStatus: Int?,
    val lastValidIbiMs: Int?,
    /** Son geçerli referans olayından beri geçen süre (ms). */
    val referenceAgeMs: Long?,
    val clockUncertaintyMs: Long?,
    val sequence: Long,
    val timestampNanos: Long,
)

/**
 * Watch referans olayının geçerli olup olmadığı (docs 02, 03 quality gate).
 * Geçersiz olaylar da CSV'ye yazılır (debug için), ama snapshot'a dahil edilmez.
 */
fun WatchReferenceEvent.isReferenceValid(): Boolean =
    ReferenceValidity.isHrValid(heartRateStatus) &&
        validIbiValuesMs.isNotEmpty()