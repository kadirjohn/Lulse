package com.kadirjohn.lulse.data.recording

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kadirjohn.lulse.data.sensor.SensorSample
import com.kadirjohn.lulse.shared.ClockSyncFrame
import com.kadirjohn.lulse.shared.WatchReferenceEvent
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kaydedilmiş sensör sample'larını CSV olarak dosyaya yazar.
 *
 * Hedef: public Downloads/Lulse dizini — böylece root'suz cihazda
 * dosya yöneticisinden erişilebilir, Bluetooth/ paylaşım ile dışa aktarılabilir.
 *
 * - Android 10+ (API 29+) scoped storage: [MediaStore.Downloads] kullanılır.
 *   minSdk 31 olduğu için her zaman bu yol geçerli.
 * - Geriye dönük olarak app-specific storage'a da bir kopya bırakılır (güvenlik).
 *
 * Metadata comment satırı olarak dosya başına gömülür (# önekli).
 */
class CsvExporter {

    /** Watch referans olayları tablosu başlığı (docs 03 WATCH REFERENCE EVENTS columns). */
    private val WATCH_REFERENCE_HEADER = listOf(
        "watch_sequence", "session_id",
        "watch_datapoint_timestamp", "watch_callback_elapsed_ns", "watch_send_elapsed_ns",
        "phone_receive_elapsed_ns", "estimated_phone_datapoint_ns",
        "heart_rate_bpm", "heart_rate_status",
        "ibi_values_ms", "ibi_statuses", "valid_ibi_values_ms",
        "clock_offset_ns", "clock_rtt_ns", "clock_uncertainty_ns",
        "transport_age_ms",
    )

    /** Clock sync frame'leri tablosu başlığı (docs 03 ClockSyncFrame). */
    private val CLOCK_SYNC_HEADER = listOf(
        "session_id", "sync_id",
        "t0_phone_ns", "t1_watch_ns", "t2_watch_ns", "t3_phone_ns",
        "rtt_ns", "watch_minus_phone_offset_ns",
        "accepted", "reason",
    )

    data class ExportResult(
        val publicFile: File?,
        val appFile: File?,
        val relativePath: String,
    )

    /**
     * Sample'ları (ve analiz tick'lerini) Downloads/Lulse/ altına CSV olarak yazar.
     * @return [ExportResult]; publicFile null ise yazma başarısız (appFile yine de olabilir).
     */
    fun exportToDownloads(
        context: Context,
        samples: List<SensorSample>,
        metadata: SessionMetadata,
        analysisFrames: List<AnalysisFrame> = emptyList(),
        watchRefEvents: List<WatchReferenceEvent> = emptyList(),
        clockSyncFrames: List<ClockSyncFrame> = emptyList(),
    ): ExportResult {
        val name = fileName(metadata)
        val relative = "Lulse/$name"

        // 1) App-specific kopya (her zaman denenir — güvenlik).
        val appDir = File(context.filesDir, "lulse_sessions").apply { mkdirs() }
        val appFile = File(appDir, name)
        runCatching {
            appFile.outputStream().use { os -> writeTo(os, samples, metadata, analysisFrames, watchRefEvents, clockSyncFrames) }
        }.getOrNull()

        // 2) Public Downloads/Lulse/ — MediaStore ile.
        val publicFile = writeToDownloads(context, relative, samples, metadata, analysisFrames, watchRefEvents, clockSyncFrames)
        return ExportResult(
            publicFile = publicFile,
            appFile = appFile,
            relativePath = relative,
        )
    }

    private fun writeToDownloads(
        context: Context,
        relativePath: String,
        samples: List<SensorSample>,
        metadata: SessionMetadata,
        analysisFrames: List<AnalysisFrame>,
        watchRefEvents: List<WatchReferenceEvent>,
        clockSyncFrames: List<ClockSyncFrame>,
    ): File? {
        val resolver = context.contentResolver
        val name = relativePath.substringAfterLast('/')
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Lulse")
            // Mevcut cihaz adı/zaman damgası.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri, "w")?.use { os -> writeTo(os, samples, metadata, analysisFrames, watchRefEvents, clockSyncFrames) }
                ?: return null
            // Yazma tamam — dosyayı görünür yap.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            File(relativePath) // public mutlak yol yerine kısa yol döner; mutlak yolu log/debug'ta göster.
        }.getOrNull()
    }

    /**
     * Verilen sample + metadata + analiz tick'lerini [OutputStream]'e yazar.
     *
     * Dosya yapısı:
     *  - # önekli metadata satırları
     *  - sensör tablosu (CSV_HEADER) — raw IMU verisi, satır başına 1 sample
     *  - boş ayraç satırı
     *  - # analysis frames başlığı + analiz tablosu (ANALYSIS_HEADER) —
     *    her analiz tick (~200ms) bir satır: bpm, confidence, verdict,
     *    motionState, measurementState, bufferSize, vb. Algoritmanın canlıda
     *    ne ürettiğini offline incelemek için.
     */
    fun writeTo(
        out: OutputStream,
        samples: List<SensorSample>,
        metadata: SessionMetadata,
        analysisFrames: List<AnalysisFrame> = emptyList(),
        watchRefEvents: List<WatchReferenceEvent> = emptyList(),
        clockSyncFrames: List<ClockSyncFrame> = emptyList(),
    ) {
        out.bufferedWriter().use { w ->
            writeMetadata(w, metadata, sampleCount = samples.size, analysisCount = analysisFrames.size,
                watchRefCount = watchRefEvents.size, clockSyncCount = clockSyncFrames.size)
            w.appendLine(CSV_HEADER.joinToString(","))
            samples.forEach { s ->
                w.appendLine(
                    listOf(
                        s.timestampNanos.toString(),
                        s.eventTimeMs.toString(),
                        s.sensorType.name,
                        s.x.toString(),
                        s.y.toString(),
                        s.z.toString(),
                    ).joinToString(","),
                )
            }
            // İkinci tablo: analiz tick'leri (debug snapshot'ları).
            if (analysisFrames.isNotEmpty()) {
                w.appendLine()
                w.appendLine("# analysis frames (per ~200ms tick)")
                w.appendLine(ANALYSIS_HEADER.joinToString(","))
                analysisFrames.forEach { f ->
                    w.appendLine(
                        listOf(
                            f.timestampNanos.toString(),
                            f.bpm?.toString() ?: "",
                            f.confidence?.toString() ?: "",
                            f.verdict ?: "",
                            f.motionState.name,
                            f.measurementState.name,
                            f.motionScoreTotal.toString(),
                            f.accelVariance.toString(),
                            f.gyroEnergy.toString(),
                            f.jerk.toString(),
                            f.orientation.name,
                            f.phoneUpright.toString(),
                            f.bufferSize.toString(),
                            f.bufferDropped.toString(),
                            f.sampleRatesHz["ACCELEROMETER"]?.toString() ?: "",
                            f.sampleRatesHz["GYROSCOPE"]?.toString() ?: "",
                            f.sampleRatesHz["LINEAR_ACCELERATION"]?.toString() ?: "",
                        ).joinToString(","),
                    )
                }
            }
            // Üçüncü tablo: watch referans olayları (watch6 integration, docs 03).
            if (watchRefEvents.isNotEmpty()) {
                w.appendLine()
                w.appendLine("# watch reference events")
                w.appendLine(WATCH_REFERENCE_HEADER.joinToString(","))
                watchRefEvents.forEach { e ->
                    w.appendLine(
                        listOf(
                            e.watchSequence.toString(),
                            e.sessionId ?: "",
                            e.watchDataPointTimestamp.toString(),
                            e.watchCallbackElapsedNanos.toString(),
                            e.watchSendElapsedNanos.toString(),
                            e.phoneReceiveElapsedNanos.toString(),
                            e.estimatedPhoneDatapointNanos.toString(),
                            e.heartRateBpm.toString(),
                            e.heartRateStatus.toString(),
                            e.ibiValuesMs.joinToString(";"),
                            e.ibiStatuses.joinToString(";"),
                            e.validIbiValuesMs.joinToString(";"),
                            e.clockOffsetNanos.toString(),
                            e.clockRttNanos.toString(),
                            e.clockUncertaintyNanos.toString(),
                            e.transportAgeMs.toString(),
                        ).joinToString(","),
                    )
                }
            }
            // Dördüncü tablo: clock sync frame'leri (docs 03).
            if (clockSyncFrames.isNotEmpty()) {
                w.appendLine()
                w.appendLine("# clock sync frames")
                w.appendLine(CLOCK_SYNC_HEADER.joinToString(","))
                clockSyncFrames.forEach { c ->
                    w.appendLine(
                        listOf(
                            c.sessionId,
                            c.syncId.toString(),
                            c.t0PhoneNanos.toString(),
                            c.t1WatchNanos.toString(),
                            c.t2WatchNanos.toString(),
                            c.t3PhoneNanos.toString(),
                            c.rttNanos.toString(),
                            c.watchMinusPhoneOffsetNanos.toString(),
                            c.accepted.toString(),
                            c.reason ?: "",
                        ).joinToString(","),
                    )
                }
            }
        }
    }

    private fun writeMetadata(
        w: java.io.BufferedWriter,
        m: SessionMetadata,
        sampleCount: Int,
        analysisCount: Int = 0,
        watchRefCount: Int = 0,
        clockSyncCount: Int = 0,
    ) {
        w.appendLine("# Lulse sensor session")
        w.appendLine("# device_model=${m.deviceModel}")
        w.appendLine("# app_version=${m.appVersion}")
        w.appendLine("# start_ms=${m.sessionStartMs}")
        w.appendLine("# end_ms=${m.sessionEndMs}")
        w.appendLine("# duration_sec=${m.durationSec}")
        w.appendLine("# sample_rate_hz=${m.sampleRateHz}")
        w.appendLine("# placement=${m.phonePlacement}")
        w.appendLine("# breathing=${m.breathingCondition}")
        w.appendLine("# reference_bpm=${m.referenceBpm ?: ""}")
        w.appendLine("# sensors=${m.sensorTypes.joinToString(";")}")
        w.appendLine("# count=$sampleCount")
        w.appendLine("# analysis_frames=$analysisCount")
        w.appendLine("# watch_reference_events=$watchRefCount")
        w.appendLine("# clock_sync_frames=$clockSyncCount")
    }

    private fun fileName(m: SessionMetadata): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val stamp = fmt.format(Date(m.sessionStartMs))
        return "lulse_${stamp}.csv"
    }
}