package com.kadirjohn.lulse.data.recording

import android.os.Build

/**
 * Bir kayıt seansının metadata'sı.
 *
 * Bu yapı, ileride Faz 7 (debug/recording mode) ve Faz 8 (AI/ML veri toplama)
 * için temel oluşturur. V1'de CSV export'a gömülür.
 *
 * TODO (Faz 7): referans BPM alanı, kullanıcının seçtiği etiketler tam aktif hale gelsin.
 */
data class SessionMetadata(
    val sessionStartMs: Long,
    val sessionEndMs: Long = sessionStartMs,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val appVersion: String = "1.0",
    /** Gözlemlenen ortalama sample rate (Hz) — export anında doldurulur. */
    val sampleRateHz: Float = 0f,
    /** Telefonun göğüs üzerindeki yeri (debug etiketi). */
    val phonePlacement: Placement = Placement.UNSPECIFIED,
    /** Nefes koşulu (debug etiketi). */
    val breathingCondition: BreathingCondition = BreathingCondition.UNSPECIFIED,
    /**
     * Opsiyonel referans nabız (debug modunda kullanıcı girebilir).
     * TODO (Faz 7): referans ölçüm cihazından gelen değer.
     */
    val referenceBpm: Int? = null,
    val sensorTypes: List<String> = emptyList(),
) {
    val durationSec: Float get() = (sessionEndMs - sessionStartMs) / 1000f
}

enum class Placement { UNSPECIFIED, CENTER_CHEST, LEFT_CHEST }

enum class BreathingCondition { UNSPECIFIED, NORMAL, BREATH_HOLD, DEEP }

/** CSV export için kullanılan sabit başlık sırası. */
internal val CSV_HEADER = listOf(
    "timestamp_nanos",
    "event_time_ms",
    "sensor_type",
    "x",
    "y",
    "z",
)