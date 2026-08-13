package com.kadirjohn.lulse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kadirjohn.lulse.data.recording.AnalysisFrame
import com.kadirjohn.lulse.data.recording.BreathingCondition
import com.kadirjohn.lulse.data.recording.Placement
import com.kadirjohn.lulse.data.recording.RecordingManager
import com.kadirjohn.lulse.data.wear.isReady
import com.kadirjohn.lulse.data.wear.label
import com.kadirjohn.lulse.data.sensor.SampleRateEstimator
import com.kadirjohn.lulse.data.sensor.SensorRepository
import com.kadirjohn.lulse.data.sensor.SensorRingBuffer
import com.kadirjohn.lulse.data.sensor.SensorType
import com.kadirjohn.lulse.domain.measurement.MeasurementStateMachine
import com.kadirjohn.lulse.domain.motion.MotionAnalyzer
import com.kadirjohn.lulse.domain.motion.MotionState
import com.kadirjohn.lulse.domain.signal.SignalProcessor
import com.kadirjohn.lulse.ui.screen.DebugUiState
import com.kadirjohn.lulse.ui.screen.HomeUiState
import com.kadirjohn.lulse.ui.screen.SignalQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tüm katmanları birleştiren ViewModel (MVVM).
 *
 * SensorRepository → ring buffer → MotionAnalyzer → MeasurementStateMachine
 * pipeline'ı tek bir consumer coroutine içinde çalıştırır; UI'ya [uiState] üzerinden
 * tek bir state akıtır. Recording/debug aksiyonları buradan dispatch edilir.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val sensorRepository = SensorRepository(app)
    private val recordingManager = RecordingManager(app)
    private val motionAnalyzer = MotionAnalyzer()
    private val measurementStateMachine = MeasurementStateMachine()
    private val signalProcessor = SignalProcessor()

    // Watch6 referansı — opsiyonel. Watch yoksa bağlantı Disconnected kalır,
    // telefon ölçümü etkilenmez (docs 05 non-negotiable).
    private val wearRepository = com.kadirjohn.lulse.data.wear.WearConnectionHolder.get(app)

    private val ringBuffer = SensorRingBuffer()
    private val sampleRateEstimators: Map<SensorType, SampleRateEstimator> =
        SensorType.entries.associateWith { SampleRateEstimator() }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // En son motion state — histeresis için analizler arası tutulur.
    private var currentMotionState: MotionState = MotionState.HIGH_MOTION

    init {
        startPipeline()
        startWatchObserver()
    }

    /**
     * Watch bağlantı + referans durumunu UI'ya yansıtır (watch6 integration).
     * Watch opsiyoneldir — bağlantı yoksa alanlar null/false kalır.
     * Ayrıca watch reference/clock-sync olaylarını aktif kayıtta RecordingManager'a besler.
     */
    private fun startWatchObserver() {
        viewModelScope.launch {
            wearRepository.connectionState.collect { state ->
                _uiState.update { prev ->
                    prev.copy(
                        debug = prev.debug.copy(
                            watchConnected = state.isReady,
                            watchState = state.label(),
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            wearRepository.latestReference.collect { ref ->
                val nowMs = System.currentTimeMillis()
                _uiState.update { prev ->
                    prev.copy(
                        debug = prev.debug.copy(
                            watchReferenceBpm = ref?.bpm,
                            watchHrStatus = ref?.hrStatus,
                            watchLastValidIbiMs = ref?.lastValidIbiMs,
                            watchReferenceAgeMs = ref?.referenceAgeMs,
                            watchSequence = ref?.sequence ?: 0,
                        ),
                    )
                }
                // AnalysisFrame'e watch snapshot ekle — analyze() içinde okunur.
            }
        }
        // Aktif kayıtta watch referans + clock sync olaylarını CSV'ye topla.
        viewModelScope.launch {
            wearRepository.referenceEvents.collect { event ->
                if (recordingManager.isRecording()) {
                    recordingManager.addWatchRefEvent(event)
                }
            }
        }
        viewModelScope.launch {
            wearRepository.clockSyncFrames.collect { frame ->
                if (recordingManager.isRecording()) {
                    recordingManager.addClockSyncFrame(frame)
                }
            }
        }
    }

    /**
     * Sensör flow'unu tüketen tek coroutine:
     *  - ring buffer'ı doldurur
     *  - sample rate tahminini günceller
     *  - recordingManager'a aktif kayıtta sample verir
     *  - periyodik olarak motion + measurement state üretir
     */
    private fun startPipeline() {
        viewModelScope.launch {
            var lastAnalysisNanos = 0L
            val analysisIntervalNanos = 200_000_000L // ~200ms (spec §9.A)

            sensorRepository.samples().collect { sample ->
                ringBuffer.add(sample)
                sampleRateEstimators[sample.sensorType]?.update(sample.timestampNanos)
                recordingManager.add(sample)

                // Throttle: ~200ms'de bir analiz.
                if (sample.timestampNanos - lastAnalysisNanos >= analysisIntervalNanos) {
                    lastAnalysisNanos = sample.timestampNanos
                    analyze(sample.timestampNanos)
                }
            }
        }
    }

    private fun analyze(nowNanos: Long) {
        val (score, motionState) = motionAnalyzer.analyzeAndClassify(ringBuffer, nowNanos, currentMotionState)
        currentMotionState = motionState

        // Sadece STILL iken pulse ara — hareketli/dikken DSP çalışmaz.
        val pulse: SignalProcessor.SignalResult? =
            if (motionState == MotionState.STILL && !score.phoneUpright) {
                signalProcessor.process(ringBuffer.snapshot())
            } else null

        val nowMs = System.currentTimeMillis()
        val (measurementState, _) = measurementStateMachine.update(motionState, pulse, nowMs)

        _uiState.update { prev ->
            prev.copy(
                motionState = motionState,
                measurementState = measurementState,
                motionScore = score,
                phoneUpright = score.phoneUpright,
                bpm = measurementStateMachine.lastPulse?.bpm,
                confidencePct = measurementStateMachine.lastPulse?.let { (it.confidence * 100).toInt() },
                signalQuality = qualityFromConfidence(measurementStateMachine.lastPulse?.confidence),
                lastBeatNanos = measurementStateMachine.lastPulse?.lastBeatNanos,
                recentBeatNanos = measurementStateMachine.lastPulse?.recentBeatNanos ?: emptyList(),
                debug = prev.debug.copy(
                    motionScore = score.total,
                    accelVariance = score.accelVariance,
                    gyroEnergy = score.gyroEnergy,
                    jerk = score.jerkMagnitude,
                    motionState = motionState,
                    measurementState = measurementState,
                    orientation = score.orientation,
                    bufferDropped = ringBuffer.droppedCount(),
                    bufferSize = ringBuffer.size,
                    bpm = pulse?.bpm,
                    confidence = pulse?.confidence,
                ),
            )
        }
        // Aktif kayıtta bu analiz tick'inin debug snapshot'ını CSV'ye topla.
        recordingManager.addAnalysisFrame(
            AnalysisFrame(
                timestampNanos = nowNanos,
                bpm = pulse?.bpm,
                confidence = pulse?.confidence,
                verdict = pulse?.verdict,
                motionState = motionState,
                measurementState = measurementState,
                motionScoreTotal = score.total,
                accelVariance = score.accelVariance,
                gyroEnergy = score.gyroEnergy,
                jerk = score.jerkMagnitude,
                orientation = score.orientation,
                phoneUpright = score.phoneUpright,
                bufferSize = ringBuffer.size,
                bufferDropped = ringBuffer.droppedCount(),
                sampleRatesHz = sampleRateEstimators.mapKeys { it.key.name }
                    .mapValues { it.value.hz() },
            ),
        )
        pushSensorDebug()
    }

    private fun qualityFromConfidence(c: Float?): SignalQuality = when {
        c == null -> SignalQuality.UNKNOWN
        c >= 0.8f -> SignalQuality.HIGH
        c >= 0.6f -> SignalQuality.MEDIUM
        else -> SignalQuality.LOW
    }

    private fun pushSensorDebug() {
        val avail = sensorRepository.availability.mapKeys { it.key.name }
        val info = sensorRepository.sensorInfo().mapKeys { it.key.name }
        val rates = sampleRateEstimators.mapKeys { it.key.name }.mapValues { it.value.hz() }
        _uiState.update { prev ->
            prev.copy(
                debug = prev.debug.copy(
                    sensorAvailability = avail,
                    sensorInfo = info,
                    sampleRateHz = rates,
                    recording = recordingManager.isRecording(),
                    recordedCount = recordingManager.state.value.recordedCount,
                    lastExportPath = recordingManager.state.value.lastExportPath,
                ),
            )
        }
    }

    // --- Debug / recording aksiyonları ---

    /** Long-press ile debug panelini aç. Zaten açıksa kapat. */
    fun toggleDebug() {
        _uiState.update { it.copy(debugVisible = !it.debugVisible, debugMinimized = false) }
    }

    /** Debug panelini tamamen kapat (çarpı butonu). */
    fun closeDebug() {
        _uiState.update { it.copy(debugVisible = false, debugMinimized = false) }
    }

    /** Debug panelini minimize et → sürüklenebilir balon moduna geç. */
    fun minimizeDebug() {
        _uiState.update { it.copy(debugVisible = false, debugMinimized = true) }
    }

    /** Balona tıkla → debug panelini tekrar tam aç. */
    fun restoreDebug() {
        _uiState.update { it.copy(debugVisible = true, debugMinimized = false) }
    }

    /** Balon sürükleme — ekran konumunu güncelle (px offset). */
    fun updateBubbleOffset(x: Float, y: Float) {
        _uiState.update { it.copy(bubbleOffsetX = x, bubbleOffsetY = y) }
    }

    fun dismissIntro() {
        _uiState.update { it.copy(showIntro = false) }
    }

    fun startRecording() {
        recordingManager.start()
        // Watch referans oturumunu başlat (watch bağlıysa) — clock sync + HR stream.
        // Watch yoksa zararsız (docs 05: telefon bağımsız çalışır).
        val sessionId = "lulse_${System.currentTimeMillis()}"
        wearRepository.startSession(sessionId)
        pushSensorDebug()
    }

    fun stopRecording() {
        // Ortalama sample rate (accelerometer tercih).
        val avgHz = sampleRateEstimators[SensorType.ACCELEROMETER]?.hz()
            ?: sampleRateEstimators[SensorType.LINEAR_ACCELERATION]?.hz() ?: 0f
        recordingManager.stop(sampleRateHz = avgHz, placement = Placement.CENTER_CHEST, breathing = BreathingCondition.NORMAL)
        wearRepository.stopSession()
        pushSensorDebug()
    }

    /** Watch'a bağlanmayı yeniden dene (debug panel butonu). Watch opsiyonel. */
    fun connectWatch() {
        wearRepository.connect()
    }

    override fun onCleared() {
        super.onCleared()
        // Flow toplama viewModelScope kapanınca durur; sensor listener otomatik unregister olur.
        if (recordingManager.isRecording()) {
            recordingManager.stop()
            wearRepository.stopSession()
        }
        wearRepository.release()
    }
}