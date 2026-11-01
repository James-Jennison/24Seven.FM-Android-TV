package com.codeframe78.twentyfourseven.player.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.math.sqrt

class PcmAmplitudeAnalyzerTest {
    @Test fun invertedStereoHasTheSamePowerAsMono() {
        val mono = FloatArray(480) { .5f }
        val stereo = FloatArray(960) { if (it % 2 == 0) .5f else -.5f }
        var monoResult: PcmAmplitudeFrame? = null
        var stereoResult: PcmAmplitudeFrame? = null
        PcmAmplitudeAnalyzer(1, 48000, 1).accept(mono) { monoResult = it }
        PcmAmplitudeAnalyzer(1, 48000, 2).accept(stereo) { stereoResult = it }
        assertEquals(monoResult, stereoResult)
        assertEquals(.5f, stereoResult!!.rms, 0f)
        assertEquals(.5f, stereoResult!!.peak, 0f)
    }

    @Test fun unequalChannelsUseMeanPowerAndMaximumPeak() {
        val samples = FloatArray(480 * 3) { if (it % 3 == 0) .6f else 0f }
        PcmAmplitudeAnalyzer(2, 48000, 3).accept(samples) {
            assertEquals(sqrt(.36f / 3), it.rms, .000001f)
            assertEquals(.6f, it.peak, 0f)
        }
    }

    @Test fun arbitraryWholeFrameChunksPreserveEveryWindow() {
        for (rate in listOf(44100, 48000, 96000)) {
            val samples = FloatArray(rate / 5 * 2) { sin(it * .17).toFloat() }
            val whole = mutableListOf<PcmAmplitudeFrame>()
            PcmAmplitudeAnalyzer(3, rate, 2).accept(samples) { whole.add(it) }
            val chunked = mutableListOf<PcmAmplitudeFrame>()
            val analyzer = PcmAmplitudeAnalyzer(3, rate, 2)
            var offset = 0
            while (offset < samples.size) {
                val size = minOf(samples.size - offset, 2 * (1 + offset % 509))
                analyzer.accept(samples, offset, size) { chunked.add(it) }
                offset += size
            }
            assertEquals(20, whole.size)
            assertEquals(whole, chunked)
            assertEquals((rate / 5).toLong(), whole.last().endSample)
        }
    }

    @Test fun partialWindowIsNotPublishedAndNewEpochDropsIt() {
        val old = PcmAmplitudeAnalyzer(1, 48000, 1)
        old.accept(FloatArray(479) { 1f }) { throw AssertionError("Partial window published") }
        val fresh = mutableListOf<PcmAmplitudeFrame>()
        PcmAmplitudeAnalyzer(2, 44100, 1).accept(FloatArray(441)) { fresh.add(it) }
        assertEquals(listOf(PcmAmplitudeFrame(2, 44100, 441, 0f, 0f)), fresh)
    }

    @Test fun malformedRangesDoNotAdvanceWindow() {
        val analyzer = PcmAmplitudeAnalyzer(1, 48000, 2)
        for ((offset, size) in listOf(-1 to 2, 0 to 3, 1 to Int.MAX_VALUE)) {
            var rejected = false
            try {
                analyzer.accept(FloatArray(960), offset, size) { throw AssertionError() }
            } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
        val frames = mutableListOf<PcmAmplitudeFrame>()
        analyzer.accept(FloatArray(960)) { frames.add(it) }
        assertEquals(listOf(PcmAmplitudeFrame(1, 48000, 480, 0f, 0f)), frames)
    }

    @Test fun nonfiniteAndClippedInputRemainFinite() {
        val values = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -2f, 2f)
        val frames = mutableListOf<PcmAmplitudeFrame>()
        PcmAmplitudeAnalyzer(1, 48000, 1).accept(FloatArray(480) { values[it % 4] }) { frames.add(it) }
        assertEquals(1, frames.size)
        assertEquals(sqrt(.5f), frames.single().rms, .000001f)
        assertEquals(1f, frames.single().peak, 0f)
    }
}
