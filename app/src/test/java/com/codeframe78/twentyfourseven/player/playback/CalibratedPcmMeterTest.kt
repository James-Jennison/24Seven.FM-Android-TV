package com.codeframe78.twentyfourseven.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class CalibratedPcmMeterTest {
    @Test
    fun `each calibrated band reports the dBFS of an in-band reference sine`() {
        val references = listOf(2 to 0, 6 to 1, 43 to 2, 256 to 3)
        references.forEach { (bin, expectedBand) ->
            val meter = CalibratedPcmMeter(SampleRateHz)
            val amplitude = .25f
            feedSine(meter, bin, amplitude, amplitude)

            val expectedDbfs = 20f * log10(amplitude / sqrt(2f))
            assertEquals(expectedDbfs, meter.reading.bandDbfs[expectedBand], .35f)
            meter.reading.bandDbfs.forEachIndexed { index, actual ->
                if (index != expectedBand) assertTrue("band $index leaked into $expectedBand: $actual", actual < -45f)
            }
        }
    }

    @Test
    fun `left and right masters retain their independent decoded PCM levels`() {
        val meter = CalibratedPcmMeter(SampleRateHz)
        feedSine(meter, bin = 43, leftAmplitude = .5f, rightAmplitude = .25f)

        assertEquals(20f * log10(.5f / sqrt(2f)), meter.reading.leftDbfs, .1f)
        assertEquals(20f * log10(.25f / sqrt(2f)), meter.reading.rightDbfs, .1f)
    }

    @Test
    fun `stereo correlation reports decoded in-phase and inverted channels`() {
        val inPhase = CalibratedPcmMeter(SampleRateHz)
        feedSine(inPhase, bin = 43, leftAmplitude = .25f, rightAmplitude = .25f)
        assertEquals(1f, inPhase.reading.stereoCorrelation, .001f)

        val inverted = CalibratedPcmMeter(SampleRateHz)
        feedSine(inverted, bin = 43, leftAmplitude = .25f, rightAmplitude = -.25f)
        assertEquals(-1f, inverted.reading.stereoCorrelation, .001f)
    }

    @Test
    fun `all visualizer spectrum levels use the fixed dBFS display range`() {
        val meter = CalibratedPcmMeter(SampleRateHz)
        val amplitude = .25f
        feedSine(meter, bin = 43, leftAmplitude = amplitude, rightAmplitude = amplitude)

        val expectedDbfs = 20f * log10(amplitude / sqrt(2f))
        val expectedLevel = (expectedDbfs + 60f) / 60f
        assertEquals(expectedLevel, meter.reading.spectrumLevels.max(), .015f)
    }

    @Test
    fun `Signal Scope snapshot is an ordered downsample of the latest decoded PCM`() {
        val meter = CalibratedPcmMeter(SampleRateHz)
        repeat(WaveformHistoryFrames) { frame ->
            val sample = (frame % 101) / 100f - .5f
            meter.accept(sample, sample, sample)
        }

        val waveform = meter.waveformSnapshot()
        waveform.forEachIndexed { index, actual ->
            val expectedFrame = index * WaveformHistoryFrames / waveform.size
            val expected = (expectedFrame % 101) / 100f - .5f
            assertEquals("sample $index", expected, actual, 0f)
        }
    }

    private fun feedSine(meter: CalibratedPcmMeter, bin: Int, leftAmplitude: Float, rightAmplitude: Float) {
        val frequency = bin * SampleRateHz.toFloat() / WindowFrames
        repeat(WindowFrames) { frame ->
            val phase = 2.0 * PI * frequency * frame / SampleRateHz
            val left = leftAmplitude * sin(phase).toFloat()
            val right = rightAmplitude * sin(phase).toFloat()
            meter.accept((left + right) / 2f, left, right)
        }
    }

    private companion object {
        const val SampleRateHz = 48_000
        const val WindowFrames = 2_048
        const val WaveformHistoryFrames = 1_024
    }
}
