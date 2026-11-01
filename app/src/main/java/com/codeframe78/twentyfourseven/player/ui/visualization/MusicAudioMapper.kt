package com.codeframe78.twentyfourseven.player.ui.visualization

import com.codeframe78.twentyfourseven.player.domain.MusicReactiveState
import com.codeframe78.twentyfourseven.player.domain.ReactiveStatus
import com.codeframe78.twentyfourseven.player.ui.VideoAudioFrame

/** Render-specific output, theme-independent input. No audio capture or analysis lives here. */
internal fun interface MusicAudioMapper<T> {
    fun map(music: MusicReactiveState, nowMs: Long, reduced: Boolean): T
}

private fun MusicReactiveState.available(nowMs: Long) =
    (status == ReactiveStatus.Active || status == ReactiveStatus.Silence) && measuredAtElapsedRealtimeMs > 0 &&
        nowMs >= measuredAtElapsedRealtimeMs && nowMs - measuredAtElapsedRealtimeMs <= 250

private fun unit(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

internal data class NeonMusicFrame(
    val energy: Float = 0f, val bass: Float = 0f, val mid: Float = 0f,
    val treble: Float = 0f, val impact: Float = 0f,
)

internal object NeonMusicMapper : MusicAudioMapper<NeonMusicFrame> {
    override fun map(music: MusicReactiveState, nowMs: Long, reduced: Boolean): NeonMusicFrame {
        if (!music.available(nowMs)) return NeonMusicFrame()
        val gain = if (reduced) .68f else 1f
        return NeonMusicFrame(unit(music.amplitude) * gain, unit(music.bass) * gain,
            unit(music.mid) * gain, unit(music.treble) * gain,
            unit(music.onset) * if (reduced) .46f else 1f)
    }
}

internal object EmeraldMusicMapper : MusicAudioMapper<VideoAudioFrame> {
    override fun map(music: MusicReactiveState, nowMs: Long, reduced: Boolean): VideoAudioFrame {
        if (!music.available(nowMs)) return VideoAudioFrame(alpha = if (reduced) .40f else .55f)
        val gain = if (reduced) .25f else 1f
        val bass = unit(music.bass); val mid = unit(music.mid); val highs = unit(music.treble)
        val hit = unit(music.onset) * .025f * gain
        val shear = mid * .010f * gain
        val scale = bass * .14f * gain
        return VideoAudioFrame(
            scaleX = 1f + scale * 1.35f + shear * 2f + hit,
            scaleY = 1f + scale + shear * 2f + hit,
            shear = shear,
            alpha = (if (reduced) .40f else .55f) + (bass * .16f + mid * .10f + highs * .12f) * gain,
            speed = .5f + (unit(music.energy) * .75f + mid * .25f) * gain,
            moving = music.status == ReactiveStatus.Active,
        )
    }
}
