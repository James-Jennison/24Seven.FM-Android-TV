package com.codeframe78.twentyfourseven.player.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class PcmSpectralAnalyzerTest {
    private fun tone(frames: Int, bin: Int, channels: Int = 1): FloatArray =
        FloatArray(frames * channels) { index ->
            val value = (.5 * sin(2.0 * Math.PI * bin * (index / channels) / 2048)).toFloat()
            if (index % channels == 0) value else -value
        }

    private fun analyze(samples: FloatArray, channels: Int = 1): List<PcmSpectralFrame> {
        val result = mutableListOf<PcmSpectralFrame>()
        PcmSpectralAnalyzer(1, 48000, channels).accept(samples) { result.add(it) }
        return result
    }

    @Test fun invertedStereoRetainsMonoSpectralPower() {
        assertEquals(analyze(tone(4096, 32)), analyze(tone(4096, 32, 2), 2))
    }

    @Test fun binCenteredTonesHaveExpectedBandPower() {
        // A .5 peak sinusoid has mean-square power .125 (-9.0309 dBFS).
        val expected = (60f - 9.0309f) / 60f
        for ((bin, band) in listOf(4 to 0, 32 to 1, 128 to 2)) {
            val reading = analyze(tone(2048, bin)).single()
            val levels = listOf(reading.bass, reading.mid, reading.treble)
            assertEquals(expected, levels[band], .0001f)
            levels.forEachIndexed { index, value -> if (index != band) assertEquals(0f, value, .001f) }
            assertEquals(0f, reading.flux, 0f)
            assertEquals(2048L, reading.endSample)
        }
    }

    @Test fun arbitraryWholeFrameChunksProduceIdenticalWindows() {
        val samples = tone(8192, 32, 2)
        val expected = analyze(samples, 2)
        val actual = mutableListOf<PcmSpectralFrame>()
        val analyzer = PcmSpectralAnalyzer(1, 48000, 2)
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(samples.size - offset, 2 * (1 + offset % 509))
            analyzer.accept(samples, offset, count) { actual.add(it) }
            offset += count
        }
        assertEquals(7, actual.size)
        assertEquals(expected, actual)
        assertEquals(8192L, actual.last().endSample)
    }

    @Test fun startupSteadyToneAndSilenceHaveNoFlux() {
        analyze(tone(8192, 32)).forEach { assertTrue(it.flux < .00001f) }
        analyze(FloatArray(8192)).forEach {
            assertEquals(0f, it.bass + it.mid + it.treble + it.flux, 0f)
        }
    }

    @Test fun attackAfterSilenceProducesBoundedPositiveFlux() {
        val input = FloatArray(2048) + tone(4096, 32)
        val readings = analyze(input)
        assertEquals(0f, readings.first().flux, 0f)
        assertTrue(readings.drop(1).any { it.flux > .13f })
        assertTrue(readings.all { it.flux.isFinite() && it.flux in 0f..1f })
        assertTrue(readings.last().flux < .00001f)
    }

    @Test fun malformedInputDoesNotConsumeSamplesAndNewEpochDropsPartialWindow() {
        val analyzer = PcmSpectralAnalyzer(1, 48000, 2)
        for ((offset, size) in listOf(-1 to 2, 0 to 3, 1 to Int.MAX_VALUE)) {
            var rejected = false
            try {
                analyzer.accept(FloatArray(4096), offset, size) { throw AssertionError() }
            } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        val readings = mutableListOf<PcmSpectralFrame>()
        analyzer.accept(FloatArray(4096)) { readings.add(it) }
        assertEquals(listOf(PcmSpectralFrame(1, 48000, 2048, 0f, 0f, 0f, 0f)), readings)
        analyzer.accept(tone(1023, 32, 2)) { throw AssertionError("Incomplete hop published") }
        val fresh = mutableListOf<PcmSpectralFrame>()
        PcmSpectralAnalyzer(2, 44100, 1).accept(FloatArray(2048)) { fresh.add(it) }
        assertEquals(listOf(PcmSpectralFrame(2, 44100, 2048, 0f, 0f, 0f, 0f)), fresh)
    }

    @Test fun nonfiniteAndOutOfRangeSamplesAreSanitized() {
        val raw = FloatArray(4096) { listOf(Float.NaN, Float.POSITIVE_INFINITY, -2f, 2f)[it % 4] }
        val safe = FloatArray(4096) { listOf(0f, 0f, -1f, 1f)[it % 4] }
        assertEquals(analyze(safe), analyze(raw))
    }

    @Test fun unsupportedConstructionFailsBeforeAllocatingWindows() {
        for ((rate, channels) in listOf(0 to 1, 48000 to 0, 48000 to 33)) {
            var rejected = false
            try { PcmSpectralAnalyzer(1, rate, channels) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }
}
