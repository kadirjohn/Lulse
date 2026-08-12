package com.kadirjohn.lulse.data.recording

import android.content.Context
import com.kadirjohn.lulse.data.sensor.SensorSample
import com.kadirjohn.lulse.data.sensor.SensorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kayıt seansını yönetir: aktifken gelen sample'ları biriktirir,
 * durdurulunca [SessionMetadata] ile birlikte CSV'ye dışa aktarır.
 *
 * UI/ViewModel bu sınıfın [state]'ini okur ve start/stop çağırır.
 * Sample ekleme tek coroutine (repository consumer) içinden yapılır —
 * thread-safe tutmak için basit senkronizasyon.
 */
class RecordingManager(
    private val context: Context,
    private val exporter: CsvExporter = CsvExporter(),
) {

    data class State(
        val recording: Boolean = false,
        val sessionId: Long = 0L,
        val recordedCount: Int = 0,
        val lastExportPath: String? = null,
        val lastError: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    // Aktif seansın biriken sample'ları. recording sırasında doldurulur.
    private val activeSamples = mutableListOf<SensorSample>()
    private var sessionStartMs: Long = 0L

    /** Kaydı başlat. Önceki birikmiş sample'ları temizle. */
    fun start() {
        synchronized(this) {
            activeSamples.clear()
            sessionStartMs = System.currentTimeMillis()
            _state.value = State(recording = true, sessionId = sessionStartMs)
        }
    }

    /** Aktif seansa sample ekle (kayıt kapalıysa yok sayılır). */
    fun add(sample: SensorSample) {
        if (!_state.value.recording) return
        synchronized(this) {
            activeSamples.add(sample)
            _state.value = _state.value.copy(recordedCount = activeSamples.size)
        }
    }

    /**
     * Kaydı durdur ve CSV'ye yaz.
     * @param sampleRateHz Export'a gömülecek gözlemlenen ortalama Hz.
     * @param placement Debug etiketi (opsiyonel).
     * @param breathing Debug etiketi (opsiyonel).
     * @return Yazılan dosya, başarısızsa null.
     */
    fun stop(
        sampleRateHz: Float = 0f,
        placement: Placement = Placement.UNSPECIFIED,
        breathing: BreathingCondition = BreathingCondition.UNSPECIFIED,
    ): java.io.File? {
        val samples: List<SensorSample>
        val start: Long
        synchronized(this) {
            samples = activeSamples.toList()
            start = sessionStartMs
            activeSamples.clear()
        }
        val end = System.currentTimeMillis()
        val metadata = SessionMetadata(
            sessionStartMs = start,
            sessionEndMs = end,
            sampleRateHz = sampleRateHz,
            phonePlacement = placement,
            breathingCondition = breathing,
            sensorTypes = SensorType.entries.map { it.name },
        )
        val result = exporter.exportToDownloads(context, samples, metadata)
        // Debug panelde gösterilecek yol: public Downloads yolu (varsa) yoksa app kopyası.
        val shownPath = result.publicFile?.let { "Downloads/Lulse/${it.name}" }
            ?: result.appFile?.absolutePath
        _state.value = State(
            recording = false,
            sessionId = start,
            recordedCount = samples.size,
            lastExportPath = shownPath,
            lastError = if (result.publicFile == null && result.appFile == null) "Export failed" else null,
        )
        return result.publicFile ?: result.appFile
    }

    fun isRecording(): Boolean = _state.value.recording
}