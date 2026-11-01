package com.codeframe78.twentyfourseven.player.domain

/** Ephemeral theme-independent controls. Calibrated measurements remain in AudioVisualizationState. */
data class MusicReactiveState(
    val epoch: Long = 0,
    val sequence: Long = 0,
    val sampleRateHz: Int = 0,
    val windowEndSample: Long = 0,
    /** Last complete FFT window end; null during spectral warmup. Never a speaker timestamp. */
    val spectralWindowEndSample: Long? = null,
    val measuredAtElapsedRealtimeMs: Long = 0,
    val status: ReactiveStatus = ReactiveStatus.WarmingUp,
    val amplitude: Float = 0f,
    val peak: Float = 0f,
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val energy: Float = 0f,
    val onset: Float = 0f,
    val onsetId: Long = 0,
    val onsetSample: Long = 0,
    /** Decode-clock data is not an assertion about actual speaker/display synchronization. */
    val timing: ReactiveTiming = ReactiveTiming.DecodeClock,
    val estimatedPresentationElapsedRealtimeMs: Long? = null,
)

enum class ReactiveStatus { WarmingUp, Active, Silence, Paused, Unavailable }
enum class ReactiveTiming { DecodeClock, SinkEstimate }
