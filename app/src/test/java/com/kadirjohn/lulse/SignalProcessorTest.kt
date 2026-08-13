package com.kadirjohn.lulse

import com.kadirjohn.lulse.data.sensor.SensorSample
import com.kadirjohn.lulse.data.sensor.SensorType
import com.kadirjohn.lulse.domain.signal.SignalProcessor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

/**
 * SignalProcessor çekirdek davranış testleri (pure logic, JVM).
 *
 * ACF-birincil mimarinin doğrulaması: doğru BPM, harmonik doubling koruması,
 * 50Hz dayanıklılığı, dürüst-null. Parametreler lulsedata/ ve kullanıcının
 * bağımsız analiziyle doğrulananlarla uyumlu.
 */
class SignalProcessorTest {

    private val processor = SignalProcessor()

    /**
     * Sentetik göğüs-üzeri IMU sinyali üretir — hem ACC.z hem GYRO.x verir
     * (process() iki kanal kullanır). Kardiyak titreşim ~5–30Hz bandında,
     * beat periyodunda pulsatile envelope + bant-içi taşıyıcı.
     *
     * @param bpm kalp atış hızı
     * @param seconds kayıt uzunluğu
     * @param fsHz sample rate (ACC ve GYRO aynı)
     * @param secondHarmonicGain beat'in 2. harmoniğinin göreli gücü (doubling simülasyonu)
     */
    private fun syntheticCardiac(
        bpm: Int,
        seconds: Float,
        fsHz: Float,
        secondHarmonicGain: Float = 0.0f,
        noiseStd: Float = 0.01f,
        gravity: Float = 9.81f,
    ): List<SensorSample> {
        val n = (seconds * fsHz).toInt()
        val beatHz = bpm / 60f
        val carrierHz = 10f
        val samples = ArrayList<SensorSample>(n * 2)
        var seed = 12345L
        for (i in 0 until n) {
            val t = i.toDouble() / fsHz.toDouble()
            val beatFreq = beatHz.toDouble()
            val env = (1 - cos(2 * PI * beatFreq * t)) / 2
            val env2 = if (secondHarmonicGain > 0.0) {
                (1 - cos(2 * PI * (2 * beatFreq) * t)) / 2
            } else 0.0
            val carrier = cos(2 * PI * carrierHz.toDouble() * t)
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            val noise = noiseStd.toDouble() * ((seed.toDouble() / 0x3fffffff) - 0.5) * 2
            val z = (gravity.toDouble() + (env + secondHarmonicGain.toDouble() * env2) * carrier * 0.4 + noise).toFloat()
            // GYRO.x: düşük genlik, aynı periyodiklik (dönme titreşimi).
            val gx = ((env + secondHarmonicGain.toDouble() * env2) * carrier * 0.02 + noise * 0.3).toFloat()
            val tsNs = (t * 1e9).toLong()
            samples.add(SensorSample(SensorType.ACCELEROMETER, tsNs, (t * 1000).toLong(), 0f, 0f, z))
            samples.add(SensorSample(SensorType.GYROSCOPE, tsNs, (t * 1000).toLong(), gx, 0f, 0f))
        }
        return samples
    }

    @Test
    fun regularBeat_detectsCorrectBpm() {
        val samples = syntheticCardiac(bpm = 80, seconds = 8f, fsHz = 199f)
        val result = processor.process(samples)
        assertNotNull("düzenli sinyal BPM üretmeli", result)
        assertTrue("bpm 80 civarı olmalı, geldi: ${result!!.bpm}", result.bpm in 74..86)
    }

    @Test
    fun strongSecondHarmonic_doesNotDoubleBpm() {
        // 80 bpm + güçlü 2. harmonik → eski algoritma ~160 verebilirdi.
        // ACF-birincil mimari fundamental hipotez testiyle ~80'de tutmalı.
        val samples = syntheticCardiac(
            bpm = 80, seconds = 8f, fsHz = 199f,
            secondHarmonicGain = 1.2f, noiseStd = 0.02f,
        )
        val result = processor.process(samples)
        assertNotNull("doubling senaryosunda da BPM üretmeli", result)
        assertTrue(
            "harmonik doubling yüzünden 160 değil ~80 olmalı, geldi: ${result!!.bpm}",
            result.bpm < 110,
        )
    }

    @Test
    fun lowSampleRate_stillProcesses() {
        // 50 Hz — eski kod fs<60 reddeder + 30Hz biquad NaN üretirdi.
        val samples = syntheticCardiac(bpm = 80, seconds = 8f, fsHz = 50f)
        val result = processor.process(samples)
        assertNotNull("50Hz sinyal işlenebilmeli (eski kod reddediyordu)", result)
        assertTrue("bpm makul aralıkta olmalı, geldi: ${result!!.bpm}", result.bpm in 60..100)
    }

    @Test
    fun tooFewSamples_returnsNullHonestly() {
        val samples = syntheticCardiac(bpm = 80, seconds = 0.5f, fsHz = 199f)
        val result = processor.process(samples)
        assertNull("yetersiz pencere null dönmeli", result)
    }

    @Test
    fun accelerometerMissing_returnsNull() {
        // Sadece gyroscope — birincil kanal (ACC.z) yok.
        val samples = (0 until 1600).map { i ->
            SensorSample(SensorType.GYROSCOPE, i * 5_000_000L, 0L, 0.01f, 0f, 0f)
        }
        val result = processor.process(samples)
        assertNull("accelerometer yoksa null dönmeli", result)
    }

    @Test
    fun flatLine_noPulseReturnsNullOrValidRange() {
        // Sabit yerçekimi, kardiyak modülasyon yok.
        val n = 1600
        val samples = (0 until n).flatMap { i ->
            val ts = i * 5_000_000L
            listOf(
                SensorSample(SensorType.ACCELEROMETER, ts, 0L, 0f, 0f, 9.81f),
                SensorSample(SensorType.GYROSCOPE, ts, 0L, 0f, 0f, 0f),
            )
        }
        val result = processor.process(samples)
        // Dürüst null beklenir; sonuç gelirse bile saçma BPM olmamalı.
        if (result != null) {
            assertTrue("flat-line BPM'si 40-180 aralığında olmalı: ${result.bpm}", result.bpm in 40..180)
        }
    }
}