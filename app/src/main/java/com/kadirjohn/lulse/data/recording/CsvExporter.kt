package com.kadirjohn.lulse.data.recording

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kadirjohn.lulse.data.sensor.SensorSample
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

    data class ExportResult(
        val publicFile: File?,
        val appFile: File?,
        val relativePath: String,
    )

    /**
     * Sample'ları Downloads/Lulse/ altına CSV olarak yazar.
     * @return [ExportResult]; publicFile null ise yazma başarısız (appFile yine de olabilir).
     */
    fun exportToDownloads(
        context: Context,
        samples: List<SensorSample>,
        metadata: SessionMetadata,
    ): ExportResult {
        val name = fileName(metadata)
        val relative = "Lulse/$name"

        // 1) App-specific kopya (her zaman denenir — güvenlik).
        val appDir = File(context.filesDir, "lulse_sessions").apply { mkdirs() }
        val appFile = File(appDir, name)
        runCatching {
            appFile.outputStream().use { os -> writeTo(os, samples, metadata) }
        }.getOrNull()

        // 2) Public Downloads/Lulse/ — MediaStore ile.
        val publicFile = writeToDownloads(context, relative, samples, metadata)
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
            resolver.openOutputStream(uri, "w")?.use { os -> writeTo(os, samples, metadata) }
                ?: return null
            // Yazma tamam — dosyayı görünür yap.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            File(relativePath) // public mutlak yol yerine kısa yol döner; mutlak yolu log/debug'ta göster.
        }.getOrNull()
    }

    /** Verilen sample + metadata'yı [OutputStream]'e yazar. */
    fun writeTo(out: OutputStream, samples: List<SensorSample>, metadata: SessionMetadata) {
        out.bufferedWriter().use { w ->
            writeMetadata(w, metadata, sampleCount = samples.size)
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
        }
    }

    private fun writeMetadata(w: java.io.BufferedWriter, m: SessionMetadata, sampleCount: Int) {
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
    }

    private fun fileName(m: SessionMetadata): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val stamp = fmt.format(Date(m.sessionStartMs))
        return "lulse_${stamp}.csv"
    }
}