package com.kadirjohn.lulse.data.recording

import android.content.Context
import com.kadirjohn.lulse.data.sensor.SensorSample
import com.kadirjohn.lulse.data.sensor.SensorType
import com.kadirjohn.lulse.shared.ClockSyncFrame
import com.kadirjohn.lulse.shared.WatchReferenceEvent
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
    // Aktif seansın biriken analiz tick'leri (debug snapshot'ları).
    private val activeAnalysisFrames = mutableListOf<AnalysisFrame>()
    // Watch referans olayları + clock sync frame'leri (watch6 integration, docs 03).
    private val activeWatchRefEvents = mutableListOf<WatchReferenceEvent>()
    private val activeClockSyncFrames = mutableListOf<ClockSyncFrame>()
    private var sessionStartMs: Long = 0L

    /** Kaydı başlat. Önceki birikmiş sample'ları ve analiz frame'leri temizle. */
    fun start() {
        synchronized(this) {
            activeSamples.clear()
            activeAnalysisFrames.clear()
            activeWatchRefEvents.clear()
            activeClockSyncFrames.clear()
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
     * Aktif seansa bir analiz tick snapshot'ı ekle (~200ms'de bir çağrılır).
     * Kayıt kapalıysa yok sayılır. CSV'de sensör verisinden sonra ikinci tablo
     * olarak yazılır — algoritmanın canlıda ne ürettiğini offline incelemek için.
     */
    fun addAnalysisFrame(frame: AnalysisFrame) {
        if (!_state.value.recording) return
        synchronized(this) {
            activeAnalysisFrames.add(frame)
        }
    }

    /** Aktif seansa bir watch referans olayı ekle (watch6 integration, docs 03).
     *  Kayıt kapalıysa yok sayılır. CSV "watch reference events" tablosuna yazılır. */
    fun addWatchRefEvent(event: WatchReferenceEvent) {
        if (!_state.value.recording) return
        synchronized(this) {
            activeWatchRefEvents.add(event)
        }
    }

    /** Aktif seansa bir clock sync frame'i ekle (docs 03).
     *  Kayıt kapalıysa yok sayılır. CSV "clock sync frames" tablosuna yazılır. */
    fun addClockSyncFrame(frame: ClockSyncFrame) {
        if (!_state.value.recording) return
        synchronized(this) {
            activeClockSyncFrames.add(frame)
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
        val frames: List<AnalysisFrame>
        val watchRefs: List<WatchReferenceEvent>
        val clockSyncs: List<ClockSyncFrame>
        val start: Long
        synchronized(this) {
            samples = activeSamples.toList()
            frames = activeAnalysisFrames.toList()
            watchRefs = activeWatchRefEvents.toList()
            clockSyncs = activeClockSyncFrames.toList()
            start = sessionStartMs
            activeSamples.clear()
            activeAnalysisFrames.clear()
            activeWatchRefEvents.clear()
            activeClockSyncFrames.clear()
        }
        val end = System.currentTimeMillis()
        // Watch referansı varsa SessionMetadata.referenceBpm'i geçerli HR ortalamasıyla doldur
        // (docs 03: watch referans, telefon SCG'sini değiştirmez, sadece metadata'ya yazılır).
        val refBpm = watchRefs
            .filter { it.heartRateStatus == 1 }
            .map { it.heartRateBpm }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toInt()
        val metadata = SessionMetadata(
            sessionStartMs = start,
            sessionEndMs = end,
            sampleRateHz = sampleRateHz,
            phonePlacement = placement,
            breathingCondition = breathing,
            sensorTypes = SensorType.entries.map { it.name },
            referenceBpm = refBpm,
        )
        val result = exporter.exportToDownloads(context, samples, metadata, frames, watchRefs, clockSyncs)
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