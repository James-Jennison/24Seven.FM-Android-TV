package com.codeframe78.twentyfourseven.player.domain

import kotlinx.coroutines.flow.StateFlow

data class AudioVisualizationState(
    val energy: Float = 0f,
    /** Per-channel RMS envelopes from the decoded PCM path; equal for mono sources. */
    val leftEnergy: Float = 0f,
    val rightEnergy: Float = 0f,
    /** RMS dBFS before presentation gain; used only by the calibrated Master meters. */
    val leftDbfs: Float = -60f,
    val rightDbfs: Float = -60f,
    /** Normalized L/R PCM correlation for the current analysis window (-1 = inverted, +1 = in phase). */
    val stereoCorrelation: Float = 0f,
    /** Windowed PCM-power dBFS values for SUB, LOW, MID, and HIGH. */
    val calibratedBandDbfs: List<Float> = List(CalibratedBandCount) { -60f },
    val channelCount: Int = 0,
    /** Recent decoded mono PCM, resampled for Signal Scope only. */
    val waveform: List<Float> = List(AudioWaveformSampleCount) { 0f },
    val bands: List<Float> = List(AudioVisualizationBandCount) { 0f },
    /** Fast spectral-change envelope: high on attacks, then decays smoothly. */
    val transient: Float = 0f,
)

const val AudioVisualizationBandCount = 32
const val CalibratedBandCount = 4
const val AudioWaveformSampleCount = 256

interface AudioVisualizationRepository {
    val state: StateFlow<AudioVisualizationState>

    fun publish(state: AudioVisualizationState)

    fun clear()
}
