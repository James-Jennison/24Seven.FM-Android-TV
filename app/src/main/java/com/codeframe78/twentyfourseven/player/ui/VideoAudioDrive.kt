package com.codeframe78.twentyfourseven.player.ui

import com.codeframe78.twentyfourseven.player.domain.AudioVisualizationState
import com.codeframe78.twentyfourseven.player.domain.IdleVisualizerStyle
import kotlin.math.abs
import com.codeframe78.twentyfourseven.player.ui.visualization.EmeraldMusicMapper
import kotlin.math.exp

/** Local presentation only: calibrated PCM drives video speed, scale, shear and light. */
internal data class VideoAudioFrame(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val shear: Float = 0f,
    val alpha: Float = .55f,
    val speed: Float = .5f,
    val moving: Boolean = false,
)

internal class VideoAudioDrive {
    private var emeraldFrame = VideoAudioFrame()
    private var low = 0f
    private var mid = 0f
    private var high = 0f
    private var attack = 0f

    fun reset() { emeraldFrame = VideoAudioFrame(); low = 0f; mid = 0f; high = 0f; attack = 0f }

    fun update(style: IdleVisualizerStyle, audio: AudioVisualizationState, seconds: Float, reduced: Boolean, nowMs: Long = audio.sampledAtElapsedRealtimeMs): VideoAudioFrame {
        if (style.isPhotographicNative()) {
            val fresh = audio.freshForVideo(nowMs)
            return VideoAudioFrame(alpha = 1f, speed = 1f,
                moving = fresh.channelCount > 0 && fresh.energy.isFinite() && fresh.energy > .001f)
        }
        if (style == IdleVisualizerStyle.EmeraldResonance && audio.music != null) {
            val target = EmeraldMusicMapper.map(audio.music, nowMs, reduced)
            val dt = if (seconds.isFinite()) seconds.coerceIn(0f, .1f) else 0f
            val blend = if (target.moving) 1f else 1f - exp(-dt / .18f)
            fun settle(old: Float, next: Float) = old + (next - old) * blend
            emeraldFrame = target.copy(
                scaleX = settle(emeraldFrame.scaleX, target.scaleX),
                scaleY = settle(emeraldFrame.scaleY, target.scaleY),
                shear = settle(emeraldFrame.shear, target.shear),
                alpha = settle(emeraldFrame.alpha, target.alpha),
            )
            return emeraldFrame
        }
        val dt = if (seconds.isFinite()) seconds.coerceIn(0f, .1f) else 0f
        fun unit(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        // Use the same fixed dBFS bounds as the PCM meters, without track normalization.
        fun band(index: Int): Float {
            val db = audio.calibratedBandDbfs.getOrNull(index) ?: -60f
            return if (db.isFinite()) ((db + 60f) / 54f).coerceIn(0f, 1f) else 0f
        }
        val present = audio.channelCount > 0 && unit(audio.energy) > .001f
        fun smooth(value: Float, target: Float): Float = value + (target - value) *
            (1f - exp(-dt / if (style == IdleVisualizerStyle.LavaLamp) 1.2f else if (style == IdleVisualizerStyle.DjConsole) .3f else if (target > value) .035f else .18f))
        low = smooth(low, if (present) (band(0) * .45f + band(1) * .55f) else 0f)
        mid = smooth(mid, if (present) band(2) else 0f)
        high = smooth(high, if (present) band(3) else 0f)
        attack = smooth(attack, if (present) unit(audio.transient) else 0f)
        val gain = if (reduced) .25f else 1f
        val expansion = when (style) {
            IdleVisualizerStyle.DjConsole -> 0f // Fixed camera; the native light rig supplies spatial reactivity.
            IdleVisualizerStyle.LavaLamp -> .035f
            IdleVisualizerStyle.AuroraVeil -> .045f
            IdleVisualizerStyle.ChromaticTerrain -> .09f
            else -> .14f
        }
        val scale = low * expansion * gain
        val horizontal = when (style) {
            IdleVisualizerStyle.EmeraldResonance, IdleVisualizerStyle.AuroraDrift -> 1.35f
            IdleVisualizerStyle.SpectralSilk, IdleVisualizerStyle.OscilloscopeTunnel, IdleVisualizerStyle.RibbonReactor -> .65f
            IdleVisualizerStyle.VioletCircuit, IdleVisualizerStyle.LaserGrid -> .85f
            else -> 1f
        }
        val shear = when (style) {
            IdleVisualizerStyle.SpectralSilk, IdleVisualizerStyle.OscilloscopeTunnel, IdleVisualizerStyle.RibbonReactor -> mid * .022f
            IdleVisualizerStyle.VioletCircuit, IdleVisualizerStyle.LaserGrid -> mid * -.014f
            IdleVisualizerStyle.EmeraldResonance, IdleVisualizerStyle.AuroraDrift -> mid * .010f
            else -> 0f
        } * gain
        // Overscan bounds the shear so no black triangle is exposed at the edges.
        val overscan = abs(shear) * 2f
        val hit = attack * if (style == IdleVisualizerStyle.LavaLamp || style == IdleVisualizerStyle.DjConsole) 0f else if (style == IdleVisualizerStyle.ElectricSynapse || style == IdleVisualizerStyle.SpectralMandala || style == IdleVisualizerStyle.EchoReef) .07f else .025f
        val speedWeight = if (style == IdleVisualizerStyle.LavaLamp || style == IdleVisualizerStyle.DjConsole) .35f else if (style == IdleVisualizerStyle.IonTransit || style == IdleVisualizerStyle.PrismaticPassage || style == IdleVisualizerStyle.SignalRain || style == IdleVisualizerStyle.StarfieldWarp || style == IdleVisualizerStyle.VelvetVortex) 1.15f else .75f
        return VideoAudioFrame(
            scaleX = 1f + scale * horizontal + overscan + hit * gain,
            scaleY = 1f + scale + overscan + hit * gain,
            shear = shear,
            alpha = (if (reduced) .40f else .55f) + (low * .16f + mid * .10f + high * .12f + attack * .07f) * gain,
            speed = .5f + (low * speedWeight + mid * .25f) * gain,
            moving = present,
        )
    }
}

/** Reject absent, future-dated, or stalled PCM without adding an audio recorder or session. */
internal fun AudioVisualizationState.freshForVideo(nowMs: Long): AudioVisualizationState =
    if (sampledAtElapsedRealtimeMs > 0L && nowMs >= sampledAtElapsedRealtimeMs &&
        nowMs - sampledAtElapsedRealtimeMs <= 500L
    ) this else AudioVisualizationState()
