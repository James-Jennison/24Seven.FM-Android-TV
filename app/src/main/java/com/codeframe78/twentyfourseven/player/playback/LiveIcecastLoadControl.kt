package com.codeframe78.twentyfourseven.player.playback

import androidx.media3.exoplayer.DefaultLoadControl

/** Explicit buffering contract for continuous, unseekable Icecast playback. */
internal object LiveIcecastLoadControl {
    val profile = BufferProfile(
        minBufferMillis = 12_000,
        maxBufferMillis = 45_000,
        startupBufferMillis = 1_500,
        rebufferBufferMillis = 3_000,
    )

    fun create(): DefaultLoadControl = DefaultLoadControl.Builder()
        // Keep enough decoded audio to absorb ordinary Wi-Fi jitter without retaining the default
        // buffering profile tuned for broader media.
        .setBufferDurationsMsForStreaming(
            profile.minBufferMillis,
            profile.maxBufferMillis,
            profile.startupBufferMillis,
            profile.rebufferBufferMillis,
        )
        .setPrioritizeTimeOverSizeThresholdsForStreaming(true)
        .build()
}

internal data class BufferProfile(
    val minBufferMillis: Int,
    val maxBufferMillis: Int,
    val startupBufferMillis: Int,
    val rebufferBufferMillis: Int,
)
