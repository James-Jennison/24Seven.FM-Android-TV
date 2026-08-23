package com.codeframe78.twentyfourseven.player.playback

import com.codeframe78.twentyfourseven.player.domain.CalibratedBandCount
import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationBandCount
import com.codeframe78.twentyfourseven.player.domain.AudioWaveformSampleCount
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Continuous, allocation-free PCM meter. It retains only one short analysis window and
 * exposes dBFS measurements; it never stores program audio beyond that window.
 */
internal class CalibratedPcmMeter(sampleRateHz: Int) {
    data class Reading(
        val energy: Float,
        val leftDbfs: Float,
        val rightDbfs: Float,
        val stereoCorrelation: Float,
        val bandDbfs: FloatArray,
        val spectrumLevels: FloatArray,
        val transient: Float,
    )

    private var sampleRateHz = sampleRateHz
    private val monoWindow = FloatArray(WindowFrames)
    private val leftWindow = FloatArray(WindowFrames)
    private val rightWindow = FloatArray(WindowFrames)
    private val waveformHistory = FloatArray(WaveformHistoryFrames)
    private val fftReal = FloatArray(WindowFrames)
    private val fftImaginary = FloatArray(WindowFrames)
    private val hannWindow = FloatArray(WindowFrames) { index ->
        .5f * (1f - cos((2.0 * Math.PI * index / (WindowFrames - 1)).toFloat()))
    }
    private val bandDbfs = FloatArray(CalibratedBandCount) { FloorDbfs }
    private val spectrumDbfs = FloatArray(AudioVisualizationBandCount) { FloorDbfs }
    private val spectrumLevels = FloatArray(AudioVisualizationBandCount)
    private val previousSpectrumDbfs = FloatArray(AudioVisualizationBandCount) { FloorDbfs }
    private var windowSize = 0
    private var waveformWriteIndex = 0

    var reading = Reading(0f, FloorDbfs, FloorDbfs, 0f, bandDbfs, spectrumLevels, 0f)
        private set

    fun configure(sampleRateHz: Int) {
        this.sampleRateHz = sampleRateHz.coerceAtLeast(1)
        reset()
    }

    fun accept(mono: Float, left: Float, right: Float) {
        waveformHistory[waveformWriteIndex] = mono
        waveformWriteIndex = (waveformWriteIndex + 1) % WaveformHistoryFrames
        monoWindow[windowSize] = mono
        leftWindow[windowSize] = left
        rightWindow[windowSize] = right
        windowSize++
        if (windowSize == WindowFrames) {
            analyzeWindow()
            monoWindow.copyInto(monoWindow, destinationOffset = 0, startIndex = WindowHopFrames, endIndex = WindowFrames)
            leftWindow.copyInto(leftWindow, destinationOffset = 0, startIndex = WindowHopFrames, endIndex = WindowFrames)
            rightWindow.copyInto(rightWindow, destinationOffset = 0, startIndex = WindowHopFrames, endIndex = WindowFrames)
            windowSize = WindowFrames - WindowHopFrames
        }
    }

    fun reset() {
        windowSize = 0
        waveformWriteIndex = 0
        waveformHistory.fill(0f)
        bandDbfs.fill(FloorDbfs)
        spectrumDbfs.fill(FloorDbfs)
        spectrumLevels.fill(0f)
        previousSpectrumDbfs.fill(FloorDbfs)
        reading = Reading(0f, FloorDbfs, FloorDbfs, 0f, bandDbfs, spectrumLevels, 0f)
    }

    fun waveformSnapshot(): FloatArray = FloatArray(AudioWaveformSampleCount) { index ->
        val historyOffset = index * WaveformHistoryFrames / AudioWaveformSampleCount
        waveformHistory[(waveformWriteIndex + historyOffset) % WaveformHistoryFrames]
    }

    private fun analyzeWindow() {
        for (index in 0 until WindowFrames) {
            fftReal[index] = monoWindow[index] * hannWindow[index]
            fftImaginary[index] = 0f
        }
        fftInPlace()
        BandRanges.forEachIndexed { band, range ->
            bandDbfs[band] = rangeDbfs(range)
        }
        var positiveSpectralChange = 0f
        SpectrumBandRanges.forEachIndexed { band, range ->
            val dbfs = rangeDbfs(range)
            spectrumDbfs[band] = dbfs
            spectrumLevels[band] = dbfsToDisplayUnit(dbfs)
            positiveSpectralChange += (dbfs - previousSpectrumDbfs[band]).coerceAtLeast(0f)
            previousSpectrumDbfs[band] = dbfs
        }
        var mixedSquaredTotal = 0f
        var leftSquaredTotal = 0f
        var rightSquaredTotal = 0f
        var stereoProductTotal = 0f
        repeat(WindowFrames) { index ->
            mixedSquaredTotal += monoWindow[index] * monoWindow[index]
            leftSquaredTotal += leftWindow[index] * leftWindow[index]
            rightSquaredTotal += rightWindow[index] * rightWindow[index]
            stereoProductTotal += leftWindow[index] * rightWindow[index]
        }
        val mixedDbfs = rmsToDbfs(sqrt(mixedSquaredTotal / WindowFrames))
        val stereoDenominator = sqrt(leftSquaredTotal * rightSquaredTotal)
        val stereoCorrelation = if (stereoDenominator > CorrelationDenominatorFloor) {
            (stereoProductTotal / stereoDenominator).coerceIn(-1f, 1f)
        } else 0f
        reading = Reading(
            leftDbfs = rmsToDbfs(sqrt(leftSquaredTotal / WindowFrames)),
            rightDbfs = rmsToDbfs(sqrt(rightSquaredTotal / WindowFrames)),
            stereoCorrelation = stereoCorrelation,
            bandDbfs = bandDbfs,
            energy = dbfsToDisplayUnit(mixedDbfs),
            spectrumLevels = spectrumLevels,
            transient = (positiveSpectralChange / (AudioVisualizationBandCount * TransientDbfsRange)).coerceIn(0f, 1f),
        )
    }

    private fun rangeDbfs(range: BandRange): Float {
        var power = 0f
        for (bin in 1..WindowFrames / 2) {
            val frequency = bin * sampleRateHz.toFloat() / WindowFrames
            if (frequency >= range.startHz && frequency < range.endExclusiveHz) {
                power += (fftReal[bin] * fftReal[bin] + fftImaginary[bin] * fftImaginary[bin]) * OneSidedWindowPowerScale
            }
        }
        return rmsToDbfs(sqrt(power))
    }

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

    private fun rmsToDbfs(rms: Float) = (20f * log10(rms.coerceAtLeast(MinimumRms))).coerceIn(FloorDbfs, 0f)

    private fun dbfsToDisplayUnit(dbfs: Float) =
        ((dbfs.coerceIn(FloorDbfs, 0f) - FloorDbfs) / -FloorDbfs).coerceIn(0f, 1f)

    private data class BandRange(val startHz: Float, val endExclusiveHz: Float)

    private companion object {
        const val WindowFrames = 2_048
        const val WindowHopFrames = 1_024
        const val WaveformHistoryFrames = 1_024
        const val FloorDbfs = -60f
        const val MinimumRms = .001f
        const val CorrelationDenominatorFloor = .000_001f
        const val TransientDbfsRange = 12f
        const val OneSidedWindowPowerScale = 2f / (WindowFrames * WindowFrames * .375f)
        val BandRanges = arrayOf(
            BandRange(20f, 80f),
            BandRange(80f, 250f),
            BandRange(250f, 2_000f),
            BandRange(2_000f, 14_000f),
        )
        val SpectrumBandRanges = Array(AudioVisualizationBandCount) { index ->
            val ratio = 14_000.0 / 20.0
            val start = 20.0 * ratio.pow(index.toDouble() / AudioVisualizationBandCount)
            val end = 20.0 * ratio.pow((index + 1).toDouble() / AudioVisualizationBandCount)
            BandRange(start.toFloat(), end.toFloat())
        }
    }
}
