package com.codeframe78.twentyfourseven.player.analysis

import com.codeframe78.twentyfourseven.player.domain.MusicReactiveState
import com.codeframe78.twentyfourseven.player.domain.ReactiveStatus

/**
 * Single-owner feature/conditioning core. The adapter supplies whole interleaved frames and
 * creates a new instance for each discontinuity or format epoch. No clock, theme, coroutine,
 * playback ownership, PCM retention beyond the analyzers, or output queue lives here.
 *
 * Split at amplitude boundaries before feeding either analyzer: processing an entire caller
 * chunk spectrally first would leak future spectral data into earlier amplitude snapshots.
 * Each spectral flux measurement is consumed once, while band levels hold until the next FFT.
 */
internal class SharedMusicAnalyzer(
    epoch: Long,
    private val sampleRateHz: Int,
    private val channelCount: Int,
) {
    private val amplitude = PcmAmplitudeAnalyzer(epoch, sampleRateHz, channelCount)
    private val spectral = PcmSpectralAnalyzer(epoch, sampleRateHz, channelCount)
    private val conditioner = MusicConditioner()
    private val amplitudeWindowFrames = sampleRateHz / 100
    private var untilAmplitude = amplitudeWindowFrames
    private var acceptedFrames = 0L
    private var latestSpectrum: PcmSpectralFrame? = null
    private var pendingFlux = 0f
    private var spectralReady = false
    private var sequence = 0L

    /** Synchronous callback must not throw or reenter. Invalid ranges fail before mutation. */
    fun accept(
        samples: FloatArray,
        offset: Int = 0,
        length: Int = samples.size - offset,
        emit: (MusicReactiveState) -> Unit,
    ) {
        require(offset >= 0 && length >= 0 && offset <= samples.size - length)
        require(length % channelCount == 0)
        require(acceptedFrames <= Long.MAX_VALUE - length / channelCount)
        var cursor = offset
        val end = offset + length
        while (cursor < end) {
            val frames = minOf(untilAmplitude, (end - cursor) / channelCount)
            val count = frames * channelCount
            spectral.accept(samples, cursor, count) {
                latestSpectrum = it
                pendingFlux = maxOf(pendingFlux, it.flux)
            }
            amplitude.accept(samples, cursor, count) { frame ->
                val spectrum = latestSpectrum
                if (spectrum != null && !spectralReady) {
                    conditioner.reset()
                    spectralReady = true
                }
                val state = conditioner.update(frame.epoch, frame.endSample, frame.sampleRateHz,
                    frame.rms, frame.peak, spectrum?.bass ?: 0f, spectrum?.mid ?: 0f,
                    spectrum?.treble ?: 0f, pendingFlux)
                pendingFlux = 0f
                emit(state.copy(
                    sequence = ++sequence,
                    spectralWindowEndSample = spectrum?.endSample,
                    status = if (spectrum == null) ReactiveStatus.WarmingUp else state.status,
                    // Do not expose startup attacks before the first spectral baseline.
                    onset = if (spectrum == null) 0f else state.onset,
                    onsetId = if (spectrum == null) 0L else state.onsetId,
                    onsetSample = if (spectrum == null) 0L else state.onsetSample,
                ))
            }
            cursor += count
            acceptedFrames += frames
            untilAmplitude -= frames
            if (untilAmplitude == 0) untilAmplitude = amplitudeWindowFrames
        }
    }
}
