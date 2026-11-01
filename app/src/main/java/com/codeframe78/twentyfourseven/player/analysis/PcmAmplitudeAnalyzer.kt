package com.codeframe78.twentyfourseven.player.analysis

import kotlin.math.abs
import kotlin.math.sqrt

/** Immutable measurements; endSample is exclusive and relative to this format's epoch. */
internal data class PcmAmplitudeFrame(
    val epoch: Long,
    val sampleRateHz: Int,
    val endSample: Long,
    val rms: Float,
    val peak: Float,
)

/**
 * Single-owner streaming core, with constant storage and no retained PCM or output queue.
 * A new instance is required after a discontinuity/format change, with a new epoch.
 * Windows are approximately 10 ms (rounded down to whole frames). This is sample timing,
 * not a speaker presentation timestamp. Spectral features have a separate window clock.
 *
 * Power is averaged across channels, never across signed samples: inverted stereo retains
 * its energy. Peak is the largest absolute sample in any channel. Nonfinite samples become
 * zero and out-of-range float PCM is clipped to the nominal [-1, 1] analysis range.
 */
internal class PcmAmplitudeAnalyzer(
    private val epoch: Long,
    private val sampleRateHz: Int,
    private val channelCount: Int,
) {
    init {
        require(epoch >= 0)
        require(sampleRateHz in 100..384000)
        require(channelCount in 1..32)
    }

    private val windowFrames = sampleRateHz / 100
    private var endSample = 0L
    private var framesInWindow = 0
    private var sumSquares = 0.0
    private var peak = 0f

    /**
     * Input ranges must contain whole interleaved frames; malformed ranges fail before mutation.
     * [emit] is synchronous and must not throw or reenter this analyzer. The adapter owns any
     * bounded asynchronous handoff. Chunk boundaries do not change the measurement windows.
     */
    fun accept(
        samples: FloatArray,
        offset: Int = 0,
        length: Int = samples.size - offset,
        emit: (PcmAmplitudeFrame) -> Unit,
    ) {
        require(offset >= 0 && length >= 0 && offset <= samples.size - length)
        require(length % channelCount == 0)
        require(endSample <= Long.MAX_VALUE - length / channelCount)
        var index = offset
        val end = offset + length
        while (index < end) {
            repeat(channelCount) {
                val raw = samples[index++]
                val value = if (raw.isFinite()) raw.coerceIn(-1f, 1f) else 0f
                sumSquares += value.toDouble() * value
                peak = maxOf(peak, abs(value))
            }
            endSample++
            framesInWindow++
            if (framesInWindow == windowFrames) {
                val result = PcmAmplitudeFrame(
                    epoch, sampleRateHz, endSample,
                    sqrt(sumSquares / (windowFrames * channelCount)).toFloat(), peak,
                )
                framesInWindow = 0
                sumSquares = 0.0
                peak = 0f
                emit(result)
            }
        }
    }
}
