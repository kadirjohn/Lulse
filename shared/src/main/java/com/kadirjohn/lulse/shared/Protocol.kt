package com.kadirjohn.lulse.shared

/**
 * Telefon ↔ Watch arası Wear OS Data Layer mesaj protokolü (docs 01, 03, 05).
 *
 * Mesajlar MessageClient ile byte[] olarak taşınır; serileştirme kotlinx.serialization
 * JSON ile (docs 03: "JSON is acceptable initially because processed HR traffic
 * is low-frequency"). Tüm mesajlar [protocolVersion] taşır — uyumsuzlukta telefon
 * WATCH_INCOMPATIBLE state'ine düşer (docs 03, 04).
 *
 * Watch opsiyoneldir: bu protokol hiçbir mesaj olmadan da telefon tam çalışır.
 * Telefon SCG sonucu ASLA watch BPM ile değiştirilmez (docs 05 non-negotiable).
 */
object Protocol {
    /** Protokol sürümü — kırılım değişikliğinde artır. */
    const val PROTOCOL_VERSION = 1

    /** Wear capability — telefon bu capability'yi arar (docs 01, 04). */
    const val CAPABILITY = "lulse_watch_reference"

    // Message path'leri (docs 01).
    const val PATH_HELLO = "/lulse/watch/hello"
    const val PATH_HELLO_ACK = "/lulse/watch/hello_ack"
    const val PATH_STATUS_REQUEST = "/lulse/watch/status_request"
    const val PATH_STATUS_RESPONSE = "/lulse/watch/status"
    const val PATH_HR = "/lulse/watch/hr"
    const val PATH_SESSION_START = "/lulse/watch/session/start"
    const val PATH_SESSION_STOP = "/lulse/watch/session/stop"
    const val PATH_PING = "/lulse/watch/time/ping"
    const val PATH_PONG = "/lulse/watch/time/pong"
}

/**
 * Watch referans geçerlilik kapısı (docs 02, 03).
 * HR_STATUS == 1 ve IBI_STATUS == 0 ve IBI > 0 → geçerli referans.
 * Geçersiz olaylar da kaydedilir (debug için), ama "reference_available" false olur.
 */
object ReferenceValidity {
    const val HR_STATUS_SUCCESS = 1
    const val IBI_STATUS_VALID = 0

    fun isHrValid(hrStatus: Int): Boolean = hrStatus == HR_STATUS_SUCCESS
    fun isIbiValid(ibiStatus: Int, ibiMs: Int): Boolean =
        ibiStatus == IBI_STATUS_VALID && ibiMs > 0
}

/** HR status kodları (docs 02) — debug/log için insanca isim. */
fun hrStatusName(status: Int): String = when (status) {
    1 -> "SUCCESS"
    0 -> "INITIAL"
    -2 -> "MOVEMENT"
    -3 -> "DETACHED"
    -8 -> "WEAK_PPG"
    -10 -> "WEAK_PPG_MOVEMENT"
    -999 -> "HIGHER_PRIORITY_SENSOR"
    else -> "UNKNOWN($status)"
}