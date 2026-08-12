package com.kadirjohn.lulse

import com.kadirjohn.lulse.data.sensor.SensorRingBuffer
import com.kadirjohn.lulse.data.sensor.SensorSample
import com.kadirjohn.lulse.data.sensor.SensorType
import com.kadirjohn.lulse.domain.motion.MotionAnalyzer
import com.kadirjohn.lulse.domain.motion.MotionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MotionAnalyzer çekirdek davranış testleri (pure logic, JVM).
 */
class MotionAnalyzerTest {

    private val analyzer = MotionAnalyzer(windowSeconds = 2f)

    @Test
    fun stillSamples_classifyAsStill() {
        val buffer = SensorRingBuffer()
        // Sabit: z=9.81 (yerçekimi), x/y ~0, küçük gürültü.
        val now = 10_000_000_000L
        repeat(200) { i ->
            buffer.add(
                SensorSample(
                    sensorType = SensorType.ACCELEROMETER,
                    timestampNanos = now + i * 5_000_000L, // 200Hz
                    eventTimeMs = 0L,
                    x = 0.01f * (i % 3 - 1),
                    y = 0.01f * (i % 2 - 1),
                    z = 9.81f,
                ),
            )
        }
        val score = analyzer.analyze(buffer, now + 200 * 5_000_000L)
        assertTrue("still score küçük olmalı: ${score.total}", score.total < 1.0f)
        assertEquals(MotionState.STILL, analyzer.classify(score, MotionState.STILL))
    }

    @Test
    fun highMotionSamples_classifyAsHighMotion() {
        val buffer = SensorRingBuffer()
        val now = 10_000_000_000L
        repeat(200) { i ->
            buffer.add(
                SensorSample(
                    sensorType = SensorType.ACCELEROMETER,
                    timestampNanos = now + i * 5_000_000L,
                    eventTimeMs = 0L,
                    x = 6f * (i % 5 - 2),
                    y = 5f * (i % 4 - 1),
                    z = 9.81f + 4f * (i % 3 - 1),
                ),
            )
        }
        val score = analyzer.analyze(buffer, now + 200 * 5_000_000L)
        assertTrue("high motion score büyük olmalı: ${score.total}", score.total > 1.5f)
        assertEquals(MotionState.HIGH_MOTION, analyzer.classify(score, MotionState.HIGH_MOTION))
    }

    @Test
    fun emptyBuffer_producesEmptyScore() {
        val buffer = SensorRingBuffer()
        val score = analyzer.analyze(buffer, 0L)
        assertEquals(0f, score.total, 0.001f)
        assertEquals(0, score.samples)
    }

    @Test
    fun phoneFlat_classifiedAsLyingFlat() {
        // Telefon göğüste yatay: yerçekimi z'de (~9.81), x/y ~0.
        val samples = flatSamples()
        val orientation = analyzer.orientationFromAccel(samples)
        assertEquals(com.kadirjohn.lulse.domain.motion.Orientation.LYING_FLAT, orientation)
    }

    @Test
    fun phoneUpright_blocksStillState() {
        // Telefon dik: yerçekimi y'de (~9.81), z ~0.
        val buffer = SensorRingBuffer()
        val now = 10_000_000_000L
        repeat(200) { i ->
            buffer.add(
                SensorSample(
                    sensorType = SensorType.ACCELEROMETER,
                    timestampNanos = now + i * 5_000_000L,
                    eventTimeMs = 0L,
                    x = 0.02f,
                    y = 9.8f,
                    z = 0.01f,
                ),
            )
        }
        val score = analyzer.analyze(buffer, now + 200 * 5_000_000L)
        assertTrue("dik pozisyon phoneUpright olmalı", score.phoneUpright)
        // Hareket sıfır olsa bile dikse STILL'e geçmemeli — HIGH_MOTION yönlendirme.
        assertEquals(MotionState.HIGH_MOTION, analyzer.classify(score, MotionState.HIGH_MOTION))
    }

    private fun flatSamples(): List<SensorSample> =
        (0 until 100).map { i ->
            SensorSample(
                sensorType = SensorType.ACCELEROMETER,
                timestampNanos = i * 5_000_000L,
                eventTimeMs = 0L,
                x = 0.01f,
                y = 0.02f,
                z = 9.81f,
            )
        }
}