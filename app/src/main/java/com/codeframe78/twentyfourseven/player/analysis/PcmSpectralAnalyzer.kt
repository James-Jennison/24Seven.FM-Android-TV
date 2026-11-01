package com.codeframe78.twentyfourseven.player.analysis

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.log10

/** Immutable spectral snapshot; endSample is exclusive within the format epoch. */
internal data class PcmSpectralFrame(
    val epoch: Long,
    val sampleRateHz: Int,
    val endSample: Long,
    val bass: Float,
    val mid: Float,
    val treble: Float,
    val flux: Float,
)

/**
 * Single-owner pure Kotlin spectral analysis. Stores only a 2048-frame window per channel,
 * with a 1024-frame hop. A new instance is required for every format change/discontinuity.
 * Channel powers are averaged AFTER their FFTs, so opposite-phase channels do not cancel.
 * This is decode-sample timing, not measured speaker timing or a perceptual loudness model.
 * The existing calibrated meter is deliberately independent and remains unchanged.
 */
internal class PcmSpectralAnalyzer(
    private val epoch: Long,
    private val sampleRateHz: Int,
    private val channelCount: Int,
) {
    init {
        require(epoch >= 0)
        require(sampleRateHz in 100..384000)
        require(channelCount in 1..32)
    }

    private val windows = Array(channelCount) { FloatArray(WindowFrames) }
    private val fftReal = FloatArray(WindowFrames)
    private val fftImaginary = FloatArray(WindowFrames)
    private val hann = FloatArray(WindowFrames) { index ->
        (.5 * (1.0 - cos(2.0 * Math.PI * index / (WindowFrames - 1)))).toFloat()
    }
    private val windowPower = hann.sumOf { it.toDouble() * it }
    private val powers = DoubleArray(WindowFrames / 2 + 1)
    private val previousMagnitudes = DoubleArray(WindowFrames / 2 + 1)
    private var windowSize = 0
    private var endSample = 0L
    private var initialized = false

    /** Whole interleaved frames only. Callback is synchronous and must not throw or reenter. */
    fun accept(
        samples: FloatArray,
        offset: Int = 0,
        length: Int = samples.size - offset,
        emit: (PcmSpectralFrame) -> Unit,
    ) {
        require(offset >= 0 && length >= 0 && offset <= samples.size - length)
        require(length % channelCount == 0)
        require(endSample <= Long.MAX_VALUE - length / channelCount)
        var index = offset
        val end = offset + length
        while (index < end) {
            repeat(channelCount) { channel ->
                val raw = samples[index++]
                windows[channel][windowSize] = if (raw.isFinite()) raw.coerceIn(-1f, 1f) else 0f
            }
            windowSize++
            endSample++
            if (windowSize == WindowFrames) {
                val frame = analyze()
                windows.forEach { it.copyInto(it, 0, HopFrames, WindowFrames) }
                windowSize = WindowFrames - HopFrames
                emit(frame)
            }
        }
    }

    private fun analyze(): PcmSpectralFrame {
        powers.fill(0.0)
        for (channel in windows) {
            for (i in 0 until WindowFrames) {
                fftReal[i] = channel[i] * hann[i]
                fftImaginary[i] = 0f
            }
            fftInPlace()
            for (bin in powers.indices) {
                // DC and Nyquist have no negative-frequency partner.
                val symmetry = if (bin == 0 || bin == WindowFrames / 2) 1.0 else 2.0
                powers[bin] += (fftReal[bin].toDouble() * fftReal[bin] +
                    fftImaginary[bin].toDouble() * fftImaginary[bin]) *
                    symmetry / (WindowFrames * windowPower * channelCount)
            }
        }
        var bassPower = 0.0
        var midPower = 0.0
        var treblePower = 0.0
        var positiveChange = 0.0
        var magnitudeTotal = 0.0
        for (bin in powers.indices) {
            val hz = bin.toDouble() * sampleRateHz / WindowFrames
            if (hz < 20.0 || hz >= 14000.0) continue
            val power = powers[bin]
            when {
                hz < 250.0 -> bassPower += power
                hz < 2000.0 -> midPower += power
                else -> treblePower += power
            }
            val magnitude = sqrt(power)
            positiveChange += (magnitude - previousMagnitudes[bin]).coerceAtLeast(0.0)
            magnitudeTotal += magnitude + previousMagnitudes[bin]
            previousMagnitudes[bin] = magnitude
        }
        // First full window establishes a baseline. Silence cannot amplify numerical noise.
        val flux = if (initialized && bassPower + midPower + treblePower > 1e-6 && magnitudeTotal > 0.0) {
            (positiveChange / magnitudeTotal).toFloat().coerceIn(0f, 1f)
        } else 0f
        initialized = true
        return PcmSpectralFrame(epoch, sampleRateHz, endSample,
            level(bassPower), level(midPower), level(treblePower), flux)
    }

    /** Fixed -60..0 dB power display mapping; no adaptive gain or audible processing. */
    private fun level(power: Double): Float =
        ((10.0 * log10(power.coerceAtLeast(1e-6)) + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)

    private fun fftInPlace() {
        var reversed = 0
        for (index in 1 until WindowFrames) {
            var bit = WindowFrames shr 1
            while (reversed and bit != 0) {
                reversed = reversed xor bit
                bit = bit shr 1
            }
            reversed = reversed xor bit
            if (index < reversed) {
                val real = fftReal[index]
                fftReal[index] = fftReal[reversed]
                fftReal[reversed] = real
                val imaginary = fftImaginary[index]
                fftImaginary[index] = fftImaginary[reversed]
                fftImaginary[reversed] = imaginary
            }
        }
        var length = 2
        while (length <= WindowFrames) {
            val angle = (-2.0 * Math.PI / length).toFloat()
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            var start = 0
            while (start < WindowFrames) {
                var rotationReal = 1f
                var rotationImaginary = 0f
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val transformedReal = fftReal[odd] * rotationReal - fftImaginary[odd] * rotationImaginary
                    val transformedImaginary = fftReal[odd] * rotationImaginary + fftImaginary[odd] * rotationReal
                    val evenReal = fftReal[even]
                    val evenImaginary = fftImaginary[even]
                    fftReal[even] = evenReal + transformedReal
                    fftImaginary[even] = evenImaginary + transformedImaginary
                    fftReal[odd] = evenReal - transformedReal
                    fftImaginary[odd] = evenImaginary - transformedImaginary
                    val nextRotationReal = rotationReal * stepReal - rotationImaginary * stepImaginary
                    rotationImaginary = rotationReal * stepImaginary + rotationImaginary * stepReal
                    rotationReal = nextRotationReal
                }
                start += length
            }
            length = length shl 1
        }
    }

    private companion object {
        const val WindowFrames = 2048
        const val HopFrames = 1024
    }
}
